import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, bentukQuery, http, idPerangkat, keluar, masuk, pulihkanSesi, sesi } from './http'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  sesi.hapus()
  sesi.onKeluar = null
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const profil = { user_id: 1, unit_id: 1, unit_kode: 'KP_STL', username: 'admin1', nama: 'Admin', role: ['ADMIN'], permission: ['master.lihat'], wajib_ganti_password: false }
const token = (n: number) => ({ access_token: `akses-${n}`, access_expires: '2026-11-11T10:00:00Z', refresh_token: `refresh-${n}` })

describe('bentukQuery', () => {
  it('melewati nilai kosong dan menyandikan karakter khusus', () => {
    expect(bentukQuery({ a: 1, b: '', c: undefined, d: null, e: 'x y&z', f: false })).toBe('?a=1&e=x+y%26z&f=false')
    expect(bentukQuery({})).toBe('')
    expect(bentukQuery(undefined)).toBe('')
  })
})

describe('idPerangkat', () => {
  it('dibuat sekali lalu tetap sama', () => {
    const a = idPerangkat()
    expect(a).toMatch(/^[0-9a-f-]{36}$/)
    expect(idPerangkat()).toBe(a)
  })
})

describe('http: isi mentah (foto profil)', () => {
  it('mengirim berkas apa adanya dengan Content-Type berkas itu, bukan JSON', async () => {
    sesi.atur(token(1))
    fetchMock.mockResolvedValueOnce(json(200, { foto_versi: 123 }))
    const berkas = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' })
    const hasil = await http<{ foto_versi: number }>('PUT', '/auth/foto', { mentah: berkas })
    expect(hasil.foto_versi).toBe(123)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/foto')
    expect(init.method).toBe('PUT')
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('image/jpeg')
    expect(init.body).toBe(berkas)
  })
})

describe('http', () => {
  it('mengirim token Bearer dan membaca JSON', async () => {
    sesi.atur(token(1))
    fetchMock.mockResolvedValueOnce(json(200, [{ id: 1 }]))
    const hasil = await api.get<{ id: number }[]>('/admin/blok', { blok_id: 5 })
    expect(hasil).toEqual([{ id: 1 }])
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/admin/blok?blok_id=5')
    expect(init.headers.Authorization).toBe('Bearer akses-1')
  })

  it('mengirim badan JSON dengan Content-Type', async () => {
    fetchMock.mockResolvedValueOnce(json(201, { id: 3 }))
    await api.post('/admin/blok', { kode: 'A12' })
    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"kode":"A12"}')
  })

  it('galat server menjadi ApiError berpesan dari server', async () => {
    fetchMock.mockResolvedValueOnce(json(409, { error: 'Blok dengan kode atau nomor yang sama sudah ada' }))
    await expect(api.post('/admin/blok', {})).rejects.toMatchObject({ status: 409, message: 'Blok dengan kode atau nomor yang sama sudah ada' })
  })

  it('jawaban galat bukan JSON tetap berpesan', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>Bad gateway</html>', { status: 502 }))
    await expect(api.get('/x')).rejects.toMatchObject({ status: 502, message: 'Server menjawab 502' })
  })

  it('jaringan putus menjadi ApiError status 0', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const e = (await api.get('/x').catch((x: unknown) => x)) as ApiError
    expect(e).toBeInstanceOf(ApiError)
    expect(e.status).toBe(0)
  })

  it('401: menukar refresh token lalu mengulang permintaan sekali', async () => {
    sesi.atur(token(1))
    fetchMock
      .mockResolvedValueOnce(json(401, { error: 'Token kedaluwarsa' }))
      .mockResolvedValueOnce(json(200, token(2))) // /auth/refresh
      .mockResolvedValueOnce(json(200, { ok: true }))
    const hasil = await api.get<{ ok: boolean }>('/admin/blok')
    expect(hasil.ok).toBe(true)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/refresh')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ refresh_token: 'refresh-1' })
    // Permintaan ulang memakai token baru, dan refresh token baru tersimpan (diganti tiap dipakai).
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer akses-2')
    expect(localStorage.getItem('fai26.refresh')).toBe('refresh-2')
  })

  it('beberapa 401 bersamaan hanya memicu satu refresh', async () => {
    sesi.atur(token(1))
    let refresh = 0
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url === '/api/auth/refresh') {
        refresh++
        return json(200, token(2))
      }
      const auth = (init.headers as Record<string, string>).Authorization
      return auth === 'Bearer akses-2' ? json(200, { ok: true }) : json(401, { error: 'x' })
    })
    const hasil = await Promise.all([api.get('/a'), api.get('/b'), api.get('/c')])
    expect(hasil).toHaveLength(3)
    expect(refresh).toBe(1)
  })

  it('refresh ditolak: sesi dihapus, onKeluar dipanggil, permintaan gagal 401', async () => {
    sesi.atur(token(1))
    const keluarFn = vi.fn()
    sesi.onKeluar = keluarFn
    fetchMock
      .mockResolvedValueOnce(json(401, { error: 'Token kedaluwarsa' }))
      .mockResolvedValueOnce(json(401, { error: 'Sesi tidak berlaku. Login ulang' }))
    await expect(api.get('/admin/blok')).rejects.toMatchObject({ status: 401 })
    expect(keluarFn).toHaveBeenCalledOnce()
    expect(sesi.punyaRefresh).toBe(false)
    expect(localStorage.getItem('fai26.refresh')).toBeNull()
  })

  it('refresh gagal karena jaringan: sesi dipertahankan', async () => {
    sesi.atur(token(1))
    const keluarFn = vi.fn()
    sesi.onKeluar = keluarFn
    fetchMock.mockResolvedValueOnce(json(401, {})).mockRejectedValueOnce(new TypeError('offline'))
    await expect(api.get('/x')).rejects.toMatchObject({ status: 401 })
    expect(keluarFn).not.toHaveBeenCalled()
    expect(sesi.punyaRefresh).toBe(true)
  })

  it('endpoint tanpa token (auth: false) tidak mencoba refresh saat 401', async () => {
    sesi.atur(token(1))
    fetchMock.mockResolvedValueOnce(json(401, { error: 'Username atau password salah' }))
    await expect(http('POST', '/auth/login', { auth: false, body: {} })).rejects.toMatchObject({ status: 401, message: 'Username atau password salah' })
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })
})

describe('masuk, pulihkan, keluar', () => {
  it('masuk mengirim id perangkat dan menyimpan token', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ...token(1), profil }))
    const p = await masuk('admin1', 'rahasia')
    expect(p.username).toBe('admin1')
    const badan = JSON.parse(fetchMock.mock.calls[0][1].body)
    expect(badan).toMatchObject({ username: 'admin1', password: 'rahasia', device_id: idPerangkat() })
    expect(badan.device_nama).toMatch(/^Web Admin/)
    expect(sesi.access).toBe('akses-1')
    expect(localStorage.getItem('fai26.refresh')).toBe('refresh-1')
  })

  it('pulihkanSesi tanpa refresh token mengembalikan null tanpa memanggil server', async () => {
    expect(await pulihkanSesi()).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('pulihkanSesi memakai refresh token lalu membaca profil', async () => {
    sesi.atur(token(1))
    sesi.access = null
    fetchMock.mockResolvedValueOnce(json(200, token(2))).mockResolvedValueOnce(json(200, profil))
    const p = await pulihkanSesi()
    expect(p?.username).toBe('admin1')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/me')
  })

  it('pulihkanSesi: refresh ditolak (401) berarti sesi tidak sah, token dihapus, hasil null', async () => {
    sesi.atur(token(1))
    fetchMock.mockResolvedValueOnce(json(401, { error: 'Sesi tidak berlaku' }))
    expect(await pulihkanSesi()).toBeNull()
    expect(localStorage.getItem('fai26.refresh')).toBeNull()
  })

  it('pulihkanSesi: server sibuk (503) melempar galat dan sesi TIDAK dihapus', async () => {
    sesi.atur(token(1))
    fetchMock.mockResolvedValueOnce(json(503, { error: 'Server sedang sibuk' }))
    await expect(pulihkanSesi()).rejects.toMatchObject({ status: 0 })
    expect(localStorage.getItem('fai26.refresh')).toBe('refresh-1')
  })

  it('pulihkanSesi: jaringan putus melempar galat dan sesi dipertahankan', async () => {
    sesi.atur(token(1))
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(pulihkanSesi()).rejects.toBeInstanceOf(ApiError)
    expect(localStorage.getItem('fai26.refresh')).toBe('refresh-1')
  })

  it('pulihkanSesi: refresh berhasil tetapi /auth/me 503 tetap dianggap gangguan, bukan keluar', async () => {
    sesi.atur(token(1))
    fetchMock.mockResolvedValueOnce(json(200, token(2))).mockResolvedValueOnce(json(503, { error: 'sibuk' }))
    await expect(pulihkanSesi()).rejects.toMatchObject({ status: 503 })
    expect(localStorage.getItem('fai26.refresh')).toBe('refresh-2')
  })

  it('keluar mencabut sesi di server lalu menghapus token lokal, walau server gagal', async () => {
    sesi.atur(token(1))
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await keluar()
    expect(sesi.access).toBeNull()
    expect(localStorage.getItem('fai26.refresh')).toBeNull()
  })
})
