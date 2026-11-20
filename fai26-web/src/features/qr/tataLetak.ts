export type TataLetak = 'besar' | 'sedang' | 'kecil'

/** Ukuran sisi QR (cm) dan jumlah per halaman A4. "Besar" memenuhi syarat minimal 12 cm di SPEK_QR_TPH.md. */
export const TATA_LETAK: Record<TataLetak, { cm: number; perHalaman: number; judul: string; ket: string }> = {
  besar: { cm: 15, perHalaman: 1, judul: 'Besar (1 per halaman, 15 cm)', ket: 'Sesuai spesifikasi pemasangan di lapangan (minimal 12 cm).' },
  sedang: { cm: 9, perHalaman: 4, judul: 'Sedang (4 per halaman, 9 cm)', ket: 'Untuk papan atau tiang kecil; masih terbaca dari jarak dekat.' },
  kecil: { cm: 5.5, perHalaman: 12, judul: 'Kecil (12 per halaman, 5,5 cm)', ket: 'Hanya untuk uji coba: di bawah ukuran minimal 12 cm.' },
}

/** Membagi daftar ke halaman-halaman berisi perHalaman item. */
export function bagiHalaman<T>(daftar: T[], perHalaman: number): T[][] {
  const out: T[][] = []
  for (let i = 0; i < daftar.length; i += perHalaman) out.push(daftar.slice(i, i + perHalaman))
  return out
}

