import { ipdApi } from '@/api/endpoints'
import type { FilterIpd, IpdDetail } from '@/api/types'
import type { GambarEkspor } from '@/lib/ekspor'

/** Batas pengaman supaya satu ekspor tidak membebani server dan browser. */
export const MAKS_INSPEKSI_EKSPOR = 500
export const MAKS_FOTO_EKSPOR = 150
/** Sisi terpanjang foto yang disematkan (piksel): cukup jelas dibaca, ukuran berkas tetap wajar. */
const SISI_FOTO = 900

export type Lapor = (teks: string) => void

/** Menjalankan pekerjaan berbatas jumlah serentak, hasilnya berurutan seperti masukan. */
export async function paralel<T, R>(data: T[], serentak: number, kerja: (x: T, i: number) => Promise<R>): Promise<R[]> {
  const hasil = new Array<R>(data.length)
  let berikut = 0
  async function pekerja() {
    for (;;) {
      const i = berikut++
      if (i >= data.length) return
      hasil[i] = await kerja(data[i], i)
    }
  }
  await Promise.all(Array.from({ length: Math.min(serentak, data.length) }, pekerja))
  return hasil
}

/** Mengambil semua id inspeksi yang cocok dengan filter (semua halaman). */
export async function idInspeksi(filter: FilterIpd): Promise<{ id: string[]; terpotong: boolean }> {
  const id: string[] = []
  let total = 0
  for (let h = 1; h <= 100; h++) {
    const hal = await ipdApi.daftar({ ...filter, halaman: h, per_halaman: 100 })
    total = hal.total
    id.push(...hal.item.map((x) => x.id))
    if (id.length >= hal.total || hal.item.length === 0 || id.length >= MAKS_INSPEKSI_EKSPOR) break
  }
  return { id: id.slice(0, MAKS_INSPEKSI_EKSPOR), terpotong: total > MAKS_INSPEKSI_EKSPOR }
}

/** Mengambil rincian tiap inspeksi (pokok dan temuan) beberapa sekaligus. */
export async function ambilDetail(id: string[], lapor: Lapor): Promise<IpdDetail[]> {
  let selesai = 0
  return paralel(id, 6, async (x) => {
    const d = await ipdApi.detail(x)
    lapor(`Mengambil rincian inspeksi ${++selesai}/${id.length}`)
    return d
  })
}

/** Foto dari server dikecilkan lewat kanvas lalu dijadikan JPEG data URL. Null bila gagal (foto itu dilewati). */
export async function fotoKecil(fotoId: string, keterangan: string): Promise<GambarEkspor | null> {
  try {
    const blob = await ipdApi.foto(fotoId)
    const bmp = await createImageBitmap(blob)
    const skala = Math.min(1, SISI_FOTO / Math.max(bmp.width, bmp.height))
    const w = Math.max(1, Math.round(bmp.width * skala))
    const h = Math.max(1, Math.round(bmp.height * skala))
    const kanvas = document.createElement('canvas')
    kanvas.width = w
    kanvas.height = h
    const c = kanvas.getContext('2d')
    if (!c) return null
    c.drawImage(bmp, 0, 0, w, h)
    bmp.close()
    return { keterangan, dataUrl: kanvas.toDataURL('image/jpeg', 0.78), lebar: w, tinggi: h }
  } catch {
    return null
  }
}

/** Foto semua inspeksi, per id inspeksi. Dibatasi MAKS_FOTO_EKSPOR; sisanya dilewati dan jumlahnya dikembalikan. */
export async function ambilFoto(daftar: IpdDetail[], lapor: Lapor): Promise<{ per: Map<string, GambarEkspor[]>; dilewati: number }> {
  const tugas: { ipd: string; id: string; ket: string }[] = []
  for (const d of daftar) for (const f of d.foto) tugas.push({ ipd: d.id, id: f.id, ket: f.keterangan ?? '' })
  const dipakai = tugas.slice(0, MAKS_FOTO_EKSPOR)
  let selesai = 0
  const hasil = await paralel(dipakai, 4, async (t) => {
    const g = await fotoKecil(t.id, t.ket)
    lapor(`Mengambil foto ${++selesai}/${dipakai.length}`)
    return g
  })
  const per = new Map<string, GambarEkspor[]>()
  dipakai.forEach((t, i) => {
    const g = hasil[i]
    if (g) per.set(t.ipd, [...(per.get(t.ipd) ?? []), g])
  })
  return { per, dilewati: tugas.length - dipakai.length }
}
