import type { FilterIpd, IpdDetail } from '@/api/types'
import type { PilihanEkspor } from '@/components/TombolEkspor'
import type { GambarEkspor, LaporanEkspor } from '@/lib/ekspor'
import { MAKS_FOTO_EKSPOR, MAKS_INSPEKSI_EKSPOR, ambilDetail, ambilFoto, idInspeksi } from './ambilRinci'

type Susun = (detail: IpdDetail[], foto: Map<string, GambarEkspor[]>) => LaporanEkspor

function dengan(l: LaporanEkspor, catatan: string[]): LaporanEkspor {
  return catatan.length === 0 ? l : { ...l, keterangan: [...l.keterangan, ['Catatan ekspor', catatan.join(' ')] as [string, string]] }
}

/** Mengambil rincian semua inspeksi sesuai filter (dan fotonya bila diminta), plus catatan bila terpotong oleh batas pengaman. */
export async function kumpulkan(filter: FilterIpd, denganFoto: boolean, lapor: (t: string) => void) {
  lapor('Menghitung jumlah inspeksi…')
  const { id, terpotong } = await idInspeksi(filter)
  const detail = await ambilDetail(id, lapor)
  const foto = denganFoto ? await ambilFoto(detail, lapor) : { per: new Map<string, GambarEkspor[]>(), dilewati: 0 }
  const catatan: string[] = []
  if (terpotong) catatan.push(`Hanya ${MAKS_INSPEKSI_EKSPOR} inspeksi pertama yang dimuat; persempit periode untuk melihat sisanya.`)
  if (foto.dilewati > 0) catatan.push(`Hanya ${MAKS_FOTO_EKSPOR} foto pertama yang disematkan (${foto.dilewati} foto dilewati).`)
  return { detail, foto: foto.per, catatan }
}

export { dengan as tambahCatatanEkspor }

/**
 * Pilihan menu Ekspor untuk daftar dan laporan: satu lembar (Excel) atau satu dokumen (PDF) yang dikelompokkan per tanggal, dan di
 * tiap tanggal per inspeksi (ancak/pemanen) lengkap dengan pokok yang kena temuan. Bisa dengan foto bukti.
 */
export function pilihanEksporLaporan(filter: FilterIpd, susun: Susun): PilihanEkspor[] {
  const ambil = (denganFoto: boolean) => async (lapor: (t: string) => void) => {
    const k = await kumpulkan(filter, denganFoto, lapor)
    return dengan(susun(k.detail, k.foto), k.catatan)
  }
  return [
    { key: 'xlsx', label: 'Excel (.xlsx)', format: 'xlsx', kelompok: 'Tanpa foto (cepat)', ambil: ambil(false) },
    { key: 'pdf', label: 'PDF', format: 'pdf', kelompok: 'Tanpa foto (cepat)', ambil: ambil(false) },
    { key: 'xlsx-foto', label: 'Excel + foto', format: 'xlsx', kelompok: 'Dengan foto bukti (lebih lama)', ambil: ambil(true) },
    { key: 'pdf-foto', label: 'PDF + foto', format: 'pdf', kelompok: 'Dengan foto bukti (lebih lama)', ambil: ambil(true) },
  ]
}

/** Pilihan untuk satu inspeksi (laci rincian): rincian sudah ada, hanya foto yang diambil bila diminta. */
export function pilihanEksporSatu(d: IpdDetail, susun: Susun): PilihanEkspor[] {
  const ambil = (denganFoto: boolean) => async (lapor: (t: string) => void) => {
    const foto = denganFoto ? await ambilFoto([d], lapor) : { per: new Map<string, GambarEkspor[]>() }
    return susun([d], foto.per)
  }
  return [
    { key: 'xlsx', label: 'Excel (.xlsx)', format: 'xlsx', kelompok: 'Tanpa foto', ambil: ambil(false) },
    { key: 'pdf', label: 'PDF', format: 'pdf', kelompok: 'Tanpa foto', ambil: ambil(false) },
    { key: 'xlsx-foto', label: 'Excel + foto', format: 'xlsx', kelompok: 'Dengan foto bukti', ambil: ambil(true) },
    { key: 'pdf-foto', label: 'PDF + foto', format: 'pdf', kelompok: 'Dengan foto bukti', ambil: ambil(true) },
    { key: 'cetak', label: 'Cetak', format: 'cetak', kelompok: 'Cetak langsung', ambil: ambil(false) },
    { key: 'cetak-foto', label: 'Cetak + foto', format: 'cetak', kelompok: 'Cetak langsung', ambil: ambil(true) },
  ]
}
