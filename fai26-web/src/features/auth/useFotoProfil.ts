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
