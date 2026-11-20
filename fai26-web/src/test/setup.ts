import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'

// Node 26 menyediakan localStorage global sendiri (tanpa --localstorage-file nilainya rusak) yang menimpa milik jsdom.
// Ganti dengan penyimpanan memori sederhana supaya kode aplikasi dan tes memakai localStorage yang berfungsi.
if (typeof globalThis.localStorage?.getItem !== 'function') {
  const isi = new Map<string, string>()
  const penyimpanan = {
    getItem: (k: string) => (isi.has(k) ? (isi.get(k) as string) : null),
    setItem: (k: string, v: string) => void isi.set(k, String(v)),
    removeItem: (k: string) => void isi.delete(k),
    clear: () => isi.clear(),
    key: (i: number) => Array.from(isi.keys())[i] ?? null,
    get length() {
      return isi.size
    },
  }
  Object.defineProperty(globalThis, 'localStorage', { value: penyimpanan, configurable: true, writable: true })
  Object.defineProperty(window, 'localStorage', { value: penyimpanan, configurable: true, writable: true })
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
})

// jsdom tidak punya matchMedia dan ResizeObserver, yang dipakai Ant Design.
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia
}

class ResizeObserverTiruan {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverTiruan as unknown as typeof ResizeObserver

// crypto.randomUUID untuk idPerangkat di jsdom lama.
if (!globalThis.crypto?.randomUUID) {
  Object.defineProperty(globalThis, 'crypto', {
    value: { ...(globalThis.crypto ?? {}), randomUUID: () => '00000000-0000-4000-8000-000000000000' },
  })
}
