import type { LoginResponse, Profil, RefreshResponse } from './types'

const KUNCI_REFRESH = 'fai26.refresh'
const KUNCI_PERANGKAT = 'fai26.perangkat'

/** Galat dari API. status 0 berarti tidak sampai ke server. */
export class ApiError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function simpan(kunci: string, nilai: string | null) {
  try {
    if (nilai === null) localStorage.removeItem(kunci)
    else localStorage.setItem(kunci, nilai)
  } catch {
    // penyimpanan diblokir: sesi hanya hidup selama tab terbuka
  }
}

function baca(kunci: string): string | null {
  try {
    return localStorage.getItem(kunci)
  } catch {
    return null
  }
}

/**
 * ID perangkat untuk browser ini (UUID), dibuat sekali. Web admin tercatat di daftar perangkat seperti HP, sehingga
 * admin bisa melihat dan mencabut sesinya.
 */
export function idPerangkat(): string {
  let id = baca(KUNCI_PERANGKAT)
  if (!id) {
    id = crypto.randomUUID()
    simpan(KUNCI_PERANGKAT, id)
  }
  return id
}

/**
 * Token. Access token hanya di memori (hilang saat tab ditutup); refresh token di localStorage supaya sesi bertahan.
 * Catatan keamanan: localStorage bisa dibaca skrip yang berjalan di halaman ini, jadi jangan menambah skrip pihak
 * ketiga. Refresh token diganti tiap dipakai dan bisa dicabut dari server (reset password, nonaktifkan perangkat).
 */
class Sesi {
  access: string | null = null
  private refresh: string | null = baca(KUNCI_REFRESH)
  /** Dipanggil saat sesi tidak bisa dipulihkan (refresh ditolak). */
  onKeluar: (() => void) | null = null

  get punyaRefresh(): boolean {
    return this.refresh !== null
  }

  get refreshToken(): string | null {
    return this.refresh
  }

  atur(t: { access_token: string; refresh_token: string }) {
    this.access = t.access_token
    this.refresh = t.refresh_token
    simpan(KUNCI_REFRESH, t.refresh_token)
  }

  hapus() {
    this.access = null
    this.refresh = null
    simpan(KUNCI_REFRESH, null)
  }
}

export const sesi = new Sesi()

type Nilai = string | number | boolean | null | undefined

export function bentukQuery(q?: Record<string, Nilai>): string {
  if (!q) return ''
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(q)) {
    if (v === undefined || v === null || v === '') continue
    p.set(k, String(v))
  }
  const s = p.toString()
  return s ? `?${s}` : ''
}

interface Opsi {
  body?: unknown
  query?: Record<string, Nilai>
  /** false untuk endpoint tanpa token (login dan refresh): tidak mencoba memulihkan sesi saat 401. */
  auth?: boolean
  blob?: boolean
  /** Isi mentah (mis. berkas gambar), dikirim apa adanya dengan Content-Type berkas itu, bukan JSON. */
  mentah?: Blob
}

async function ubahJadiGalat(res: Response): Promise<ApiError> {
  let pesan = `Server menjawab ${res.status}`
  try {
    const j = (await res.json()) as { error?: string }
    if (j.error) pesan = j.error
  } catch {
    // isi bukan JSON
  }
  return new ApiError(res.status, pesan)
}

/** ok: token baru terpasang. ditolak: server menyatakan sesi tidak sah. gangguan: server/jaringan bermasalah (sesi dipertahankan). */
type HasilSegarkan = 'ok' | 'ditolak' | 'gangguan'

let sedangSegarkan: Promise<HasilSegarkan> | null = null

/** Menukar refresh token dengan token baru. Satu panggilan untuk semua permintaan yang kebetulan 401 bersamaan. */
function segarkan(): Promise<HasilSegarkan> {
  if (!sedangSegarkan) {
    sedangSegarkan = (async (): Promise<HasilSegarkan> => {
      const token = sesi.refreshToken
      if (!token) return 'ditolak'
      try {
        const res = await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refresh_token: token }),
        })
        if (res.ok) {
          sesi.atur((await res.json()) as RefreshResponse)
          return 'ok'
        }
        if (res.status === 401 || res.status === 400) {
          sesi.hapus()
          sesi.onKeluar?.()
          return 'ditolak'
        }
        return 'gangguan' // 5xx (misalnya database sibuk): jangan putus sesi, token tetap tersimpan
      } catch {
        return 'gangguan' // jaringan putus: sesi dibiarkan, pengguna bisa mencoba lagi
      }
    })().finally(() => {
      sedangSegarkan = null
    })
  }
  return sedangSegarkan
}

export async function http<T>(metode: string, path: string, opsi: Opsi = {}): Promise<T> {
  const url = `/api${path}${bentukQuery(opsi.query)}`
  const kirim = () => {
    const headers: Record<string, string> = {}
    if (sesi.access && opsi.auth !== false) headers.Authorization = `Bearer ${sesi.access}`
    if (opsi.body !== undefined) headers['Content-Type'] = 'application/json'
    if (opsi.mentah) headers['Content-Type'] = opsi.mentah.type || 'application/octet-stream'
    return fetch(url, {
      method: metode,
      headers,
      body: opsi.mentah ?? (opsi.body === undefined ? undefined : JSON.stringify(opsi.body)),
    })
  }

  let res: Response
  try {
    res = await kirim()
    if (res.status === 401 && opsi.auth !== false && sesi.punyaRefresh) {
      if ((await segarkan()) === 'ok') res = await kirim()
    }
  } catch (e) {
    if (e instanceof ApiError) throw e
    throw new ApiError(0, 'Tidak tersambung ke server. Periksa jaringan.')
  }

  if (!res.ok) throw await ubahJadiGalat(res)
  if (opsi.blob) return (await res.blob()) as T
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  get: <T>(path: string, query?: Record<string, Nilai>) => http<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown) => http<T>('POST', path, { body: body ?? {} }),
  put: <T>(path: string, body: unknown) => http<T>('PUT', path, { body }),
  hapus: <T>(path: string) => http<T>('DELETE', path),
  blob: (path: string) => http<Blob>('GET', path, { blob: true }),
}

// ---- Auth ---------------------------------------------------------------------

function namaPerangkat(): string {
  const ua = typeof navigator === 'undefined' ? '' : navigator.userAgent
  const peramban = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser'
  return `Web Admin (${peramban})`
}

export async function masuk(username: string, password: string): Promise<Profil> {
  const hasil = await http<LoginResponse>('POST', '/auth/login', {
    auth: false,
    body: { username, password, device_id: idPerangkat(), device_nama: namaPerangkat(), app_versi: 'web' },
  })
  sesi.atur(hasil)
  return hasil.profil
}

const gangguan = (e: unknown): e is ApiError => e instanceof ApiError && (e.status === 0 || e.status >= 500)

/**
 * Memulihkan sesi dari refresh token yang tersimpan (saat halaman dibuka ulang).
 * Mengembalikan null bila sesi memang tidak ada atau sudah tidak sah (arahkan ke login). Bila server sedang
 * bermasalah (jaringan putus, 5xx), melempar ApiError dan sesi TETAP tersimpan, sehingga pemanggil bisa
 * menawarkan "Coba lagi" alih-alih memaksa login ulang.
 */
export async function pulihkanSesi(): Promise<Profil | null> {
  if (!sesi.punyaRefresh) return null
  const hasil = await segarkan()
  if (hasil === 'ditolak') return null
  if (hasil === 'gangguan') throw new ApiError(0, 'Server belum bisa dihubungi. Coba lagi sebentar.')
  try {
    return await http<Profil>('GET', '/auth/me')
  } catch (e) {
    if (gangguan(e)) throw e
    return null
  }
}

export async function keluar(): Promise<void> {
  try {
    if (sesi.access) await http('POST', '/auth/logout', { body: {} })
  } catch {
    // tetap keluar di sisi browser
  } finally {
    sesi.hapus()
  }
}
