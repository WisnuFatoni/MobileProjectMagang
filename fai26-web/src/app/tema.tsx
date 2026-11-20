import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

type Mode = 'terang' | 'gelap'
const KUNCI = 'fai26.tema'

interface NilaiTema {
  mode: Mode
  atur: (m: Mode) => void
  ganti: () => void
}

const Konteks = createContext<NilaiTema | null>(null)

function awal(): Mode {
  try {
    const s = localStorage.getItem(KUNCI)
    if (s === 'terang' || s === 'gelap') return s
  } catch {
    // diblokir
  }
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'gelap' : 'terang'
}

export function TemaProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<Mode>(awal)

  useEffect(() => {
    document.documentElement.dataset.tema = mode
    document.documentElement.style.colorScheme = mode === 'gelap' ? 'dark' : 'light'
    try {
      localStorage.setItem(KUNCI, mode)
    } catch {
      // diblokir
    }
  }, [mode])

  const nilai = useMemo<NilaiTema>(
    () => ({ mode, atur: setMode, ganti: () => setMode((m) => (m === 'gelap' ? 'terang' : 'gelap')) }),
    [mode],
  )
  return <Konteks.Provider value={nilai}>{children}</Konteks.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTema(): NilaiTema {
  const n = useContext(Konteks)
  if (!n) throw new Error('useTema harus dipakai di dalam TemaProvider')
  return n
}
