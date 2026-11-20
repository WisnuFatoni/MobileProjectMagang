import QRCode from 'qrcode'

/** Margin (zona tenang) dalam jumlah modul. progres/SPEK_QR_TPH.md mensyaratkan minimal 4. */
export const MARGIN_MODUL = 4

/**
 * Matriks modul QR (level koreksi kesalahan H, tertinggi) sesuai spesifikasi QR TPH.
 * Dipisah dari komponen supaya bisa diuji tanpa DOM.
 */
export function modulQr(teks: string): { ukuran: number; gelap: boolean[][] } {
  const qr = QRCode.create(teks, { errorCorrectionLevel: 'H' })
  const n = qr.modules.size
  const gelap: boolean[][] = []
  for (let r = 0; r < n; r++) {
    const baris: boolean[] = []
    for (let c = 0; c < n; c++) baris.push(qr.modules.get(r, c) === 1)
    gelap.push(baris)
  }
  return { ukuran: n, gelap }
}

/** Path SVG untuk semua modul gelap: satu path, bukan ribuan elemen. */
export function pathQr(gelap: boolean[][]): string {
  let d = ''
  gelap.forEach((baris, r) =>
    baris.forEach((g, c) => {
      if (g) d += `M${c} ${r}h1v1h-1z`
    }),
  )
  return d
}
