import { HALAMAN } from './izin'

/**
 * Menu yang menyala untuk alamat sekarang: alamat halaman terpanjang yang cocok (sama persis atau induknya). Jadi /ipd/laporan
 * menyalakan "Laporan Loses IPD" (bukan "Inspeksi IPD"), dan /tph/cetak tetap menyalakan "TPH dan QR".
 */
export function kunciAktif(path: string): string {
  if (path === '/' || path === '') return '/'
  const bersih = path.replace(/\/+$/, '')
  let terbaik = '/' + bersih.split('/')[1]
  for (const h of HALAMAN) {
    if (h.path !== '/' && (bersih === h.path || bersih.startsWith(h.path + '/')) && h.path.length > terbaik.length) terbaik = h.path
  }
  return terbaik
}
