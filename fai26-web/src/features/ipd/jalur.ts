import type { IpdPokokOut, IpdTitikJalur } from '@/api/types'

/**
 * Hitungan murni untuk peta jalur inspeksi: menghaluskan garis, dan memasangkan tiap pokok dengan titik jalur terdekat menurut
 * waktu (HP mencatat waktu tiap pokok dan waktu tiap titik GPS, jadi posisi pokok = posisi inspektur saat mencatatnya).
 */

export interface TitikPeta {
  lat: number
  lon: number
  ms: number
  ak: number | null
}

export const keTitikPeta = (t: IpdTitikJalur[]): TitikPeta[] => t.map((x) => ({ lat: x.lat, lon: x.lon, ms: Date.parse(x.t), ak: x.ak ?? null }))

/**
 * Garis dihaluskan dengan rata-rata bergerak 3 titik (titik pertama dan terakhir tetap). GPS di bawah tajuk bergoyang beberapa meter;
 * ini meredam gerigi tanpa mengubah bentuk jalur. Hanya untuk gambar: jarak dan durasi dihitung server dari data asli.
 */
export function haluskan(titik: TitikPeta[]): [number, number][] {
  const n = titik.length
  return titik.map((t, i) => {
    if (i === 0 || i === n - 1) return [t.lat, t.lon]
    const a = titik[i - 1]
    const b = titik[i + 1]
    return [(a.lat + t.lat + b.lat) / 3, (a.lon + t.lon + b.lon) / 3]
  })
}

/** Indeks titik yang waktunya paling dekat dengan {@code ms} (titik harus berurutan menurut waktu). -1 bila kosong. */
export function indeksTerdekat(titik: TitikPeta[], ms: number): number {
  if (titik.length === 0) return -1
  let kiri = 0
  let kanan = titik.length - 1
  while (kiri < kanan) {
    const tengah = (kiri + kanan) >> 1
    if (titik[tengah].ms < ms) kiri = tengah + 1
    else kanan = tengah
  }
  if (kiri > 0 && Math.abs(titik[kiri - 1].ms - ms) <= Math.abs(titik[kiri].ms - ms)) return kiri - 1
  return kiri
}

export type JenisPokok = 'LOSES' | 'LAIN' | 'BERSIH'

export interface PokokDiPeta {
  pokok: IpdPokokOut
  titik: TitikPeta
  jenis: JenisPokok
}

/**
 * Pokok yang punya waktu catat, dipasangkan dengan titik jalur terdekat. Pokok tanpa waktu (HP lama) dilewati. Pokok yang waktunya
 * jauh di luar rentang jalur (lebih dari 10 menit dari titik terdekat) juga dilewati supaya tidak digambar di tempat yang salah.
 */
export function pasangkanPokok(pokok: IpdPokokOut[], titik: TitikPeta[], jenisKode: Map<string, string>, batasMs = 10 * 60_000): PokokDiPeta[] {
  const hasil: PokokDiPeta[] = []
  for (const p of pokok) {
    if (!p.dicatat_pada) continue
    const ms = Date.parse(p.dicatat_pada)
    const i = indeksTerdekat(titik, ms)
    if (i < 0 || Math.abs(titik[i].ms - ms) > batasMs) continue
    hasil.push({ pokok: p, titik: titik[i], jenis: jenisPokok(p, jenisKode) })
  }
  return hasil
}

/** Loses bila ada temuan buah atau brondolan, temuan lain bila hanya pelepah, bersih bila tanpa temuan. */
export function jenisPokok(p: Pick<IpdPokokOut, 'temuan'>, jenisKode: Map<string, string>): JenisPokok {
  if (p.temuan.length === 0) return 'BERSIH'
  return p.temuan.some((t) => (jenisKode.get(t.kode) ?? 'LOSES') === 'LOSES') ? 'LOSES' : 'LAIN'
}

export const WARNA_JENIS: Record<JenisPokok, string> = { LOSES: '#dc2626', LAIN: '#d97706', BERSIH: '#16a34a' }
export const NAMA_JENIS: Record<JenisPokok, string> = { LOSES: 'Loses', LAIN: 'Temuan lain', BERSIH: 'Bersih' }

/** "1,2 km" atau "340 m". */
export function teksJarak(m: number): string {
  return m >= 1000 ? `${(m / 1000).toLocaleString('id-ID', { maximumFractionDigits: 2 })} km` : `${Math.round(m)} m`
}

/** "1 jam 5 menit", "42 menit", atau "kurang dari 1 menit". */
export function teksDurasi(menit: number): string {
  if (menit < 1) return 'kurang dari 1 menit'
  const j = Math.floor(menit / 60)
  const m = Math.round(menit - j * 60)
  return j > 0 ? `${j} jam ${m} menit` : `${m} menit`
}
