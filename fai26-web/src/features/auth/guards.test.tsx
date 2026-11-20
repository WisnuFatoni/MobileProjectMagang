import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Profil } from '@/api/types'
import { AuthProvider } from './AuthProvider'
import { RequireAuth, RequireIzin } from './guards'

const http = vi.hoisted(() => ({ pulihkanSesi: vi.fn() }))

vi.mock('@/api/http', async (asli) => {
  const modul = await asli<typeof import('@/api/http')>()
  return { ...modul, pulihkanSesi: http.pulihkanSesi }
})

const profil: Profil = { user_id: 1, unit_id: 1, unit_kode: 'KP_STL', username: 'a', nama: 'A', role: ['KRANI'], permission: ['master.lihat'], wajib_ganti_password: false }

function tampil(awal: string) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[awal]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Halaman login</div>} />
            <Route path="/rahasia" element={<RequireAuth><div>Isi rahasia</div></RequireAuth>} />
            <Route path="/pengguna" element={<RequireAuth><RequireIzin izin="user.kelola"><div>Daftar pengguna</div></RequireIzin></RequireAuth>} />
            <Route path="/master" element={<RequireAuth><RequireIzin izin="master.lihat"><div>Daftar master</div></RequireIzin></RequireAuth>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => http.pulihkanSesi.mockReset())

describe('guard', () => {
  it('belum login diarahkan ke halaman login', async () => {
    http.pulihkanSesi.mockResolvedValue(null)
    tampil('/rahasia')
    expect(await screen.findByText('Halaman login')).toBeInTheDocument()
    expect(screen.queryByText('Isi rahasia')).not.toBeInTheDocument()
  })

  it('server gangguan: tampil Coba lagi (bukan login), dan berhasil setelah diulang', async () => {
    http.pulihkanSesi.mockRejectedValueOnce(new Error('503')).mockResolvedValueOnce(profil)
    tampil('/rahasia')
    expect(await screen.findByText('Server belum bisa dihubungi')).toBeInTheDocument()
    expect(screen.queryByText('Halaman login')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /coba lagi/i }))
    expect(await screen.findByText('Isi rahasia')).toBeInTheDocument()
    expect(http.pulihkanSesi).toHaveBeenCalledTimes(2)
  })

  it('sudah login (sesi dipulihkan) boleh masuk', async () => {
    http.pulihkanSesi.mockResolvedValue(profil)
    tampil('/rahasia')
    expect(await screen.findByText('Isi rahasia')).toBeInTheDocument()
  })

  it('login tanpa permission yang dibutuhkan melihat halaman 403, bukan isinya', async () => {
    http.pulihkanSesi.mockResolvedValue(profil)
    tampil('/pengguna')
    expect(await screen.findByText('Tidak punya akses')).toBeInTheDocument()
    expect(screen.queryByText('Daftar pengguna')).not.toBeInTheDocument()
  })

  it('permission yang dimiliki membuka halaman', async () => {
    http.pulihkanSesi.mockResolvedValue(profil)
    tampil('/master')
    expect(await screen.findByText('Daftar master')).toBeInTheDocument()
  })
})
