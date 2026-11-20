import type { IpdGrupLoses, StatusIpd } from '@/api/types'

/** Tampilan status inspeksi IPD (sama dengan istilah di HP). */
export const STATUS_IPD: Record<StatusIpd, { teks: string; warna: string }> = {
  MENUNGGU: { teks: 'Menunggu asisten', warna: 'gold' },
  DISETUJUI: { teks: 'Disetujui', warna: 'green' },
  DITOLAK: { teks: 'Ditolak', warna: 'red' },
}

export const NAMA_KELOMPOK: Record<string, string> = {
  BUAH: 'Buah',
  BRONDOLAN: 'Brondolan',
  PELEPAH: 'Pelepah',
}

/** Warna batang loses: makin tinggi persen pokok bertemuan makin merah (lunak: tetap ada angka, tidak hanya warna). */
export function warnaLoses(persen: number): string {
  if (persen >= 30) return '#b91c1c'
  if (persen >= 15) return '#d97706'
  return '#15803d'
}

/** Kode temuan yang paling sering pada satu kelompok data (untuk ringkasan satu baris). */
export function temuanTeratas(g: Pick<IpdGrupLoses, 'temuan_per_kode'>, maks = 3): string {
  const urut = Object.entries(g.temuan_per_kode)
    .filter(([, n]) => n > 0)
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, maks)
  return urut.length === 0 ? '-' : urut.map(([k, n]) => `${k} ${n}`).join(' • ')
}
