/** Merapikan JSON dari kolom nilai_lama dan nilai_baru; teks yang bukan JSON ditampilkan apa adanya. */
export function rapikanJson(teks: string | null): string {
  if (!teks) return ''
  try {
    return JSON.stringify(JSON.parse(teks), null, 2)
  } catch {
    return teks
  }
}
