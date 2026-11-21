/**
 * Hitungan murni untuk pemotong foto profil di web. Gambar berukuran (iw, ih) digambar dengan skala s (piksel kotak per piksel
 * gambar) dan pojok kiri-atasnya di (tx, ty) di dalam kotak potong persegi berukuran S. Gambar tidak boleh menyisakan celah.
 */

export const ZOOM_MAKS = 5

export interface Posisi {
  tx: number
  ty: number
  s: number
}

export const skalaMin = (iw: number, ih: number, S: number): number => Math.max(S / iw, S / ih)

export const jepitSkala = (s: number, iw: number, ih: number, S: number): number => {
  const min = skalaMin(iw, ih, S)
  return Math.max(min, Math.min(min * ZOOM_MAKS, s))
}

/** Menjepit posisi supaya kotak S x S selalu tertutup gambar. */
export function jepit(p: Posisi, iw: number, ih: number, S: number): Posisi {
  return {
    s: p.s,
    tx: Math.max(S - iw * p.s, Math.min(0, p.tx)),
    ty: Math.max(S - ih * p.s, Math.min(0, p.ty)),
  }
}

/** Posisi awal: gambar menutupi kotak dan berada di tengahnya. */
export function awal(iw: number, ih: number, S: number): Posisi {
  const s = skalaMin(iw, ih, S)
  return { s, tx: (S - iw * s) / 2, ty: (S - ih * s) / 2 }
}

/** Mengubah skala dengan titik (px, py) di dalam kotak tetap di tempatnya (titik di bawah penunjuk). */
export function zoomDi(p: Posisi, sBaru: number, px: number, py: number, iw: number, ih: number, S: number): Posisi {
  const s = jepitSkala(sBaru, iw, ih, S)
  return jepit({ s, tx: px - ((px - p.tx) * s) / p.s, ty: py - ((py - p.ty) * s) / p.s }, iw, ih, S)
}

/** Posisi zoom 0..1 (terkecil ke terbesar) untuk penggeser, dan sebaliknya. */
export const keRasio = (s: number, iw: number, ih: number, S: number): number => {
  const min = skalaMin(iw, ih, S)
  return (s - min) / (min * ZOOM_MAKS - min)
}
export const dariRasio = (r: number, iw: number, ih: number, S: number): number => {
  const min = skalaMin(iw, ih, S)
  return min + (min * ZOOM_MAKS - min) * Math.max(0, Math.min(1, r))
}

/** Daerah gambar (piksel gambar) yang tampak di kotak: untuk memotong pada resolusi penuh. */
export const daerahSumber = (p: Posisi, S: number): { x: number; y: number; sisi: number } => ({ x: -p.tx / p.s, y: -p.ty / p.s, sisi: S / p.s })
