import { ApiError } from '@/api/http'

/** Pesan galat yang aman ditampilkan ke pengguna. */
export function pesanGalat(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error) return e.message
  return 'Terjadi kesalahan. Coba lagi.'
}
