import type { IpdBaris, IpdDetail } from '@/api/types'

/**
 * Hitungan murni untuk Peta Monitoring IPD: satu peta berisi jalur semua inspeksi pada satu hari. Tidak memanggil server dan tidak
 * menyentuh peta, supaya dapat diuji tanpa peramban.
 */

export type StatusJalur = 'memuat' | 'ada' | 'tanpa' | 'gagal'

/** Satu inspeksi pada daftar harian beserta keadaan pemuatan jalurnya. */
export interface ItemPeta {
  baris: IpdBaris
  status: StatusJalur
  detail: IpdDetail | null
}

/** Warna garis per petugas. Dipilih agar mudah dibedakan di atas citra peta (tanpa merah/hijau yang bersaing dengan penanda loses). */
export const PALET_PETUGAS = ['#1d4ed8', '#c026d3', '#ea580c', '#0891b2', '#7c3aed', '#ca8a04', '#be123c', '#0f766e', '#4338ca', '#a16207']

/** Batas inspeksi yang jalurnya dimuat sekaligus (tiap jalur satu panggilan, dan tiap pembukaan tercatat di audit). */
export const MAKS_JALUR = 60

/**
 * Warna tiap petugas, ditetapkan menurut urutan nama supaya tidak berubah-ubah saat data dimuat atau disaring. Lebih dari
 * sepuluh petugas memakai ulang warna dari awal.
 */
export function warnaPerPetugas(petugas: string[]): Map<string, string> {
  const unik = [...new Set(petugas)].sort((a, b) => a.localeCompare(b, 'id'))
  return new Map(unik.map((p, i) => [p, PALET_PETUGAS[i % PALET_PETUGAS.length]]))
}

/** Menyaring daftar menurut petugas (username inspektur). Daftar petugas kosong berarti semua petugas. */
export function saringPetugas<T extends { inspektur: string }>(daftar: T[], petugas: string[]): T[] {
  if (petugas.length === 0) return daftar
  const dipilih = new Set(petugas)
  return daftar.filter((d) => dipilih.has(d.inspektur))
}

/** Pilihan petugas dari daftar harian: username dan nama, tanpa duplikat, terurut menurut nama. */
export function daftarPetugas(daftar: Pick<IpdBaris, 'inspektur' | 'inspektur_nama'>[]): { username: string; nama: string }[] {
  const peta = new Map<string, string>()
  for (const d of daftar) if (!peta.has(d.inspektur)) peta.set(d.inspektur, d.inspektur_nama || d.inspektur)
  return [...peta].map(([username, nama]) => ({ username, nama })).sort((a, b) => a.nama.localeCompare(b.nama, 'id'))
}

export interface RingkasanPeta {
  inspeksi: number
  denganJalur: number
  tanpaJalur: number
  memuat: number
  gagal: number
  petugas: number
  jarakM: number
}

export function ringkasPeta(item: ItemPeta[]): RingkasanPeta {
  const r: RingkasanPeta = { inspeksi: item.length, denganJalur: 0, tanpaJalur: 0, memuat: 0, gagal: 0, petugas: 0, jarakM: 0 }
  const petugas = new Set<string>()
  for (const i of item) {
    petugas.add(i.baris.inspektur)
    if (i.status === 'ada') {
      r.denganJalur++
      r.jarakM += i.detail?.jejak?.jarak_m ?? 0
    } else if (i.status === 'tanpa') r.tanpaJalur++
    else if (i.status === 'memuat') r.memuat++
    else r.gagal++
  }
  r.petugas = petugas.size
  r.jarakM = Math.round(r.jarakM * 10) / 10
  return r
}

export interface BarisPetugas {
  username: string
  nama: string
  inspeksi: number
  denganJalur: number
  jarakM: number
}

/** Ringkasan per petugas untuk legenda: jumlah inspeksi, yang berjalur, dan total jarak. Terurut menurut nama. */
export function ringkasPerPetugas(item: ItemPeta[]): BarisPetugas[] {
  const peta = new Map<string, BarisPetugas>()
  for (const i of item) {
    const b = peta.get(i.baris.inspektur) ?? { username: i.baris.inspektur, nama: i.baris.inspektur_nama || i.baris.inspektur, inspeksi: 0, denganJalur: 0, jarakM: 0 }
    b.inspeksi++
    if (i.status === 'ada') {
      b.denganJalur++
      b.jarakM += i.detail?.jejak?.jarak_m ?? 0
    }
    peta.set(i.baris.inspektur, b)
  }
  return [...peta.values()].map((b) => ({ ...b, jarakM: Math.round(b.jarakM * 10) / 10 })).sort((a, b) => a.nama.localeCompare(b.nama, 'id'))
}

/** Menentukan status jalur dari hasil pemuatan satu inspeksi. */
export function statusDari(sedangMemuat: boolean, galat: boolean, detail: IpdDetail | undefined): StatusJalur {
  if (galat) return 'gagal'
  if (sedangMemuat || !detail) return 'memuat'
  return detail.jejak && detail.jejak.titik.length > 0 ? 'ada' : 'tanpa'
}

/** Tanggal hari ini menurut jam setempat dalam format YYYY-MM-DD (bukan UTC, supaya sesuai tanggal inspeksi di kebun). */
export function tanggalLokal(d: Date): string {
  const dua = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${dua(d.getMonth() + 1)}-${dua(d.getDate())}`
}
