import type { FilterIpd, IpdDetail } from '@/api/types'
import type { PilihanEkspor } from '@/components/TombolEkspor'
import type { DokumenEkspor, GambarEkspor } from '@/lib/ekspor'
import { MAKS_FOTO_EKSPOR, MAKS_INSPEKSI_EKSPOR, ambilDetail, ambilFoto, idInspeksi } from './ambilRinci'
import { dokumenDetail } from './ekspor'

interface Param {
  /** Dokumen ringkas (tanpa mengambil rincian per inspeksi). */
  ringkas: () => Promise<DokumenEkspor>
  /** Inspeksi mana yang dimuat pada ekspor rinci (filter yang sama dengan layar). */
  filter: FilterIpd
  /** Menyusun dokumen rinci dari rincian inspeksi dan foto. */
  rinci: (detail: IpdDetail[], foto: Map<string, GambarEkspor[]>) => DokumenEkspor
}

/**
 * Pilihan menu Ekspor untuk daftar dan laporan: ringkas (cepat) atau rinci. Rinci memuat tiap inspeksi dengan pokok yang kena,
 * nama temuan, dampaknya (loses atau temuan lain), dan, bila dipilih, foto buktinya.
 */
export function pilihanEksporRinci({ ringkas, filter, rinci }: Param): PilihanEkspor[] {
  const rinciAmbil = (denganFoto: boolean) => async (lapor: (t: string) => void) => {
    lapor('Menghitung jumlah inspeksi…')
    const { id, terpotong } = await idInspeksi(filter)
    const detail = await ambilDetail(id, lapor)
    const foto = denganFoto ? await ambilFoto(detail, lapor) : { per: new Map<string, GambarEkspor[]>(), dilewati: 0 }
    const dok = rinci(detail, foto.per)
    const catatan: string[] = []
    if (terpotong) catatan.push(`Hanya ${MAKS_INSPEKSI_EKSPOR} inspeksi pertama yang dimuat; persempit rentang tanggal atau filter untuk melihat sisanya.`)
    if (foto.dilewati > 0) catatan.push(`Hanya ${MAKS_FOTO_EKSPOR} foto pertama yang disematkan (${foto.dilewati} foto dilewati).`)
    return catatan.length > 0 ? { ...dok, keterangan: [...(dok.keterangan ?? []), ['Catatan ekspor', catatan.join(' ')] as [string, string]] } : dok
  }
  return [
    { key: 'xlsx-ringkas', label: 'Excel (.xlsx)', format: 'xlsx', kelompok: 'Ringkas (cepat)', ambil: ringkas },
    { key: 'pdf-ringkas', label: 'PDF', format: 'pdf', kelompok: 'Ringkas (cepat)', ambil: ringkas },
    { key: 'xlsx-rinci', label: 'Excel rinci (tiap pokok dan temuan)', format: 'xlsx', kelompok: 'Rinci (pokok, temuan, dampak)', ambil: rinciAmbil(false) },
    { key: 'pdf-rinci', label: 'PDF rinci (satu halaman per inspeksi)', format: 'pdf', kelompok: 'Rinci (pokok, temuan, dampak)', ambil: rinciAmbil(false) },
    { key: 'xlsx-foto', label: 'Excel rinci + foto', format: 'xlsx', kelompok: 'Rinci dengan foto bukti (lebih lama)', ambil: rinciAmbil(true) },
    { key: 'pdf-foto', label: 'PDF rinci + foto', format: 'pdf', kelompok: 'Rinci dengan foto bukti (lebih lama)', ambil: rinciAmbil(true) },
  ]
}

/** Pilihan untuk satu inspeksi (laci rincian): Excel dan PDF, tanpa atau dengan foto. */
export function pilihanEksporSatu(d: IpdDetail): PilihanEkspor[] {
  const ambil = (denganFoto: boolean) => async (lapor: (t: string) => void) => {
    const foto = denganFoto ? await ambilFoto([d], lapor) : { per: new Map<string, GambarEkspor[]>() }
    return dokumenDetail(d, foto.per.get(d.id) ?? [])
  }
  return [
    { key: 'xlsx', label: 'Excel (.xlsx)', format: 'xlsx', kelompok: 'Tanpa foto', ambil: ambil(false) },
    { key: 'pdf', label: 'PDF', format: 'pdf', kelompok: 'Tanpa foto', ambil: ambil(false) },
    { key: 'xlsx-foto', label: 'Excel + foto', format: 'xlsx', kelompok: 'Dengan foto bukti', ambil: ambil(true) },
    { key: 'pdf-foto', label: 'PDF + foto', format: 'pdf', kelompok: 'Dengan foto bukti', ambil: ambil(true) },
  ]
}
