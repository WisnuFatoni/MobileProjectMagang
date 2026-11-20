/** Contoh nomor yang akan dibuat: 01 ... 20 (20 TPH). Dipisah supaya bisa diuji. */
export function pratinjauMassal(dari?: number | null, sampai?: number | null, lebar?: number | null): string {
  if (!dari || !sampai || !lebar || sampai < dari) return ''
  const f = (n: number) => String(n).padStart(lebar, '0')
  const jumlah = sampai - dari + 1
  return jumlah <= 3 ? Array.from({ length: jumlah }, (_, i) => f(dari + i)).join(', ') + ` (${jumlah} TPH)` : `${f(dari)}, ${f(dari + 1)}, ... ${f(sampai)} (${jumlah} TPH)`
}
