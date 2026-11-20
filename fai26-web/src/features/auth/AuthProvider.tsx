import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { http, keluar as apiKeluar, masuk as apiMasuk, pulihkanSesi, sesi } from '@/api/http'
import type { Profil } from '@/api/types'
import { punyaIzin } from '@/lib/izin'

/** gangguan: sesi tersimpan tetapi server belum bisa dihubungi (bukan berarti sesi tidak sah). */
type Status = 'memuat' | 'masuk' | 'keluar' | 'gangguan'

interface NilaiAuth {
  status: Status
  profil: Profil | null
  /** Mengulang pemulihan sesi setelah status 'gangguan'. */
  cobaLagi: () => void
  /** Membaca ulang profil dari server (misalnya setelah mengganti password, supaya penanda wajib ganti hilang). */
  muatUlangProfil: () => Promise<void>
  masuk: (username: string, password: string) => Promise<Profil>
  keluar: () => Promise<void>
  /** true bila pengguna memegang permission itu. */
  bisa: (izin: string) => boolean
}

const Konteks = createContext<NilaiAuth | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('memuat')
  const [profil, setProfil] = useState<Profil | null>(null)
  const qc = useQueryClient()

  // Memulihkan sesi dari refresh token saat halaman dibuka. `percobaan` naik tiap "Coba lagi".
  const [percobaan, setPercobaan] = useState(0)
  useEffect(() => {
    let batal = false
    pulihkanSesi().then(
      (p) => {
        if (batal) return
        setProfil(p)
        setStatus(p ? 'masuk' : 'keluar')
      },
      () => {
        if (!batal) setStatus('gangguan')
      },
    )
    return () => {
      batal = true
    }
  }, [percobaan])

  const cobaLagi = useCallback(() => {
    setStatus('memuat')
    setPercobaan((n) => n + 1)
  }, [])

  // Sesi dicabut di server (reset password, perangkat dinonaktifkan): kembali ke login.
  useEffect(() => {
    sesi.onKeluar = () => {
      qc.clear()
      setProfil(null)
      setStatus('keluar')
    }
    return () => {
      sesi.onKeluar = null
    }
  }, [qc])

  const muatUlangProfil = useCallback(async () => {
    try {
      setProfil(await http<Profil>('GET', '/auth/me'))
    } catch {
      // gagal membaca ulang tidak mengeluarkan pengguna; profil lama tetap dipakai
    }
  }, [])

  const masuk = useCallback(async (username: string, password: string) => {
    const p = await apiMasuk(username, password)
    setProfil(p)
    setStatus('masuk')
    return p
  }, [])

  const keluar = useCallback(async () => {
    await apiKeluar()
    qc.clear()
    setProfil(null)
    setStatus('keluar')
  }, [qc])

  const nilai = useMemo<NilaiAuth>(
    () => ({ status, profil, cobaLagi, muatUlangProfil, masuk, keluar, bisa: (izin) => punyaIzin(profil, izin) }),
    [status, profil, cobaLagi, muatUlangProfil, masuk, keluar],
  )
  return <Konteks.Provider value={nilai}>{children}</Konteks.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): NilaiAuth {
  const n = useContext(Konteks)
  if (!n) throw new Error('useAuth harus dipakai di dalam AuthProvider')
  return n
}
