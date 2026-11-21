import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { fotoProfilApi } from '@/api/endpoints'

/**
 * Alamat object URL foto profil akun yang sedang login, atau null bila belum punya foto. Diambil dengan fetch (token di header)
 * dan di-cache menurut versi foto, jadi hanya diunduh ulang ketika fotonya benar-benar berganti.
 */
export function useFotoProfil(versi: number | null | undefined): string | null {
  const q = useQuery({
    queryKey: ['foto-profil', versi],
    queryFn: fotoProfilApi.baca,
    enabled: versi !== null && versi !== undefined,
    staleTime: Infinity,
    gcTime: 30 * 60_000,
    retry: false,
  })
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!q.data) {
      setUrl(null)
      return
    }
    const u = URL.createObjectURL(q.data)
    setUrl(u)
    return () => URL.revokeObjectURL(u)
  }, [q.data])
  return versi === null || versi === undefined ? null : url
}

/** Sisi terpanjang foto yang dikirim ke server (server sendiri memotong persegi dan mengecilkan lagi ke 384 px). */
export const SISI_KIRIM_FOTO = 1024

/** Mengecilkan foto dari kamera atau galeri sebelum diunggah (menghemat kuota dan tetap di bawah batas 6 MB). */
export async function kecilkanFoto(berkas: Blob, sisiMaks = SISI_KIRIM_FOTO): Promise<Blob> {
  const bmp = await createImageBitmap(berkas)
  const skala = Math.min(1, sisiMaks / Math.max(bmp.width, bmp.height))
  const w = Math.max(1, Math.round(bmp.width * skala))
  const h = Math.max(1, Math.round(bmp.height * skala))
  const kanvas = document.createElement('canvas')
  kanvas.width = w
  kanvas.height = h
  const c = kanvas.getContext('2d')
  if (!c) throw new Error('Peramban tidak bisa memproses gambar')
  c.drawImage(bmp, 0, 0, w, h)
  bmp.close()
  return new Promise<Blob>((ok, gagal) => kanvas.toBlob((b) => (b ? ok(b) : gagal(new Error('Gagal memproses gambar'))), 'image/jpeg', 0.9))
}
