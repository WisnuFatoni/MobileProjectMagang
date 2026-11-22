/**
 * Hitungan murni untuk dasbor Persetujuan Tertunda: berapa lama sebuah catatan panen (menunggu krani) atau inspeksi IPD
 * (menunggu asisten) sudah menunggu, dan apakah itu masih wajar. Tidak ada data baru: yang dipakai hanya waktu yang sudah ada
 * pada daftar catatan dan inspeksi.
 */

export type Tingkat = 'normal' | 'mendekati' | 'terlambat'

/** Pilihan batas wajar menunggu (jam). Bawaan 8 jam: persetujuan idealnya selesai dalam satu hari kerja. */
export const PILIHAN_BATAS_JAM = [4, 8, 24, 48] as const
export const BATAS_BAWAAN_JAM = 8

/** Mulai dianggap mendekati batas pada 75% dari batas wajar. */
const PECAHAN_MENDEKATI = 0.75

const MENIT = 60_000
const JAM = 60 * MENIT
const HARI = 24 * JAM

/** Lama menunggu (ms) sejak {@code sejakIso} sampai {@code sekarang}. Waktu rusak atau di masa depan dihitung 0. */
export function lamaMenunggu(sejakIso: string | null | undefined, sekarang: number): number {
  if (!sejakIso) return 0
  const t = Date.parse(sejakIso)
  if (Number.isNaN(t)) return 0
  return Math.max(0, sekarang - t)
}

export function tingkatMenunggu(lamaMs: number, batasJam: number): Tingkat {
  const batas = batasJam * JAM
  if (lamaMs > batas) return 'terlambat'
  if (lamaMs >= batas * PECAHAN_MENDEKATI) return 'mendekati'
  return 'normal'
}

/** "kurang dari 1 menit", "45 menit", "3 jam 20 menit", "2 hari 5 jam". */
export function teksLama(ms: number): string {
  if (ms < MENIT) return 'kurang dari 1 menit'
  if (ms < JAM) return `${Math.floor(ms / MENIT)} menit`
  if (ms < HARI) {
    const j = Math.floor(ms / JAM)
    const m = Math.floor((ms - j * JAM) / MENIT)
    return m === 0 ? `${j} jam` : `${j} jam ${m} menit`
  }
  const h = Math.floor(ms / HARI)
  const j = Math.floor((ms - h * HARI) / JAM)
  return j === 0 ? `${h} hari` : `${h} hari ${j} jam`
}

export interface ItemTunggu {
  id: string
  /** Waktu mulai menunggu (ISO 8601). */
  sejak: string
}

export interface RingkasanTunggu {
  jumlah: number
  terlambat: number
  mendekati: number
  /** Lama menunggu terlama (ms); 0 bila kosong. */
  terlamaMs: number
  /** Rata-rata lama menunggu (ms); 0 bila kosong. */
  rataMs: number
}

export function ringkasTunggu(item: ItemTunggu[], batasJam: number, sekarang: number): RingkasanTunggu {
  let terlambat = 0
  let mendekati = 0
  let terlama = 0
  let total = 0
  for (const i of item) {
    const lama = lamaMenunggu(i.sejak, sekarang)
    total += lama
    if (lama > terlama) terlama = lama
    const t = tingkatMenunggu(lama, batasJam)
    if (t === 'terlambat') terlambat++
    else if (t === 'mendekati') mendekati++
  }
  return { jumlah: item.length, terlambat, mendekati, terlamaMs: terlama, rataMs: item.length === 0 ? 0 : Math.round(total / item.length) }
}

/** Gabungan beberapa ringkasan (mis. panen dan IPD) menjadi satu angka untuk kartu indikator. */
export function gabungRingkasan(...r: RingkasanTunggu[]): RingkasanTunggu {
  const jumlah = r.reduce((a, x) => a + x.jumlah, 0)
  const totalLama = r.reduce((a, x) => a + x.rataMs * x.jumlah, 0)
  return {
    jumlah,
    terlambat: r.reduce((a, x) => a + x.terlambat, 0),
    mendekati: r.reduce((a, x) => a + x.mendekati, 0),
    terlamaMs: r.reduce((a, x) => Math.max(a, x.terlamaMs), 0),
    rataMs: jumlah === 0 ? 0 : Math.round(totalLama / jumlah),
  }
}

/** Yang paling lama menunggu di urutan pertama. */
export function urutkanTerlama<T extends ItemTunggu>(item: T[]): T[] {
  return [...item].sort((a, b) => Date.parse(a.sejak) - Date.parse(b.sejak))
}

/** Jumlah per nama (mis. asisten penyetuju), terbanyak dulu; nama kosong menjadi "Belum ditentukan". */
export function hitungPerNama(nama: (string | null | undefined)[]): { nama: string; jumlah: number }[] {
  const peta = new Map<string, number>()
  for (const n of nama) {
    const k = n && n.trim() ? n.trim() : 'Belum ditentukan'
    peta.set(k, (peta.get(k) ?? 0) + 1)
  }
  return [...peta.entries()].map(([n, jumlah]) => ({ nama: n, jumlah })).sort((a, b) => b.jumlah - a.jumlah || a.nama.localeCompare(b.nama))
}
