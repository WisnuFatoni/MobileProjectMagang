import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Profil } from '@/api/types'
import { AuthProvider } from './AuthProvider'
import { LoginPage } from './LoginPage'

const http = vi.hoisted(() => ({
  masuk: vi.fn(),
  keluar: vi.fn(),
  pulihkanSesi: vi.fn(),
}))

vi.mock('@/api/http', async (asli) => {
  const modul = await asli<typeof import('@/api/http')>()
  return { ...modul, masuk: http.masuk, keluar: http.keluar, pulihkanSesi: http.pulihkanSesi }
})

import { ApiError } from '@/api/http'

const profil = (permission: string[]): Profil => ({
  user_id: 1, unit_id: 1, unit_kode: 'KP_STL', username: 'admin1', nama: 'Admin', role: ['ADMIN'], permission, wajib_ganti_password: false,
})

function tampil() {
  const qc = new QueryClient()
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<div>Halaman dasbor</div>} />
            <Route path="/panen" element={<div>Halaman panen</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  http.masuk.mockReset()
  http.keluar.mockReset().mockResolvedValue(undefined)
  http.pulihkanSesi.mockReset().mockResolvedValue(null)
})

describe('LoginPage', () => {
  it('menampilkan form dan menolak isian kosong tanpa memanggil server', async () => {
    tampil()
    await userEvent.click(await screen.findByRole('button', { name: 'Masuk' }))
    expect(await screen.findByText('Isi username')).toBeInTheDocument()
    expect(screen.getByText('Isi password')).toBeInTheDocument()
    expect(http.masuk).not.toHaveBeenCalled()
  })

  it('login berhasil membuka dasbor bagi yang punya hak laporan', async () => {
    http.masuk.mockResolvedValue(profil(['laporan.lihat', 'master.lihat']))
    tampil()
    await userEvent.type(await screen.findByLabelText('Username'), '  admin1  ')
    await userEvent.type(screen.getByLabelText('Password'), 'rahasia123')
    await userEvent.click(screen.getByRole('button', { name: 'Masuk' }))
    expect(await screen.findByText('Halaman dasbor')).toBeInTheDocument()
    expect(http.masuk).toHaveBeenCalledWith('admin1', 'rahasia123') // username dipangkas
  })

  it('pengguna tanpa hak dasbor diarahkan ke halaman pertama yang boleh dibuka', async () => {
    http.masuk.mockResolvedValue(profil(['panen.lihat']))
    tampil()
    await userEvent.type(await screen.findByLabelText('Username'), 'asisten1')
    await userEvent.type(screen.getByLabelText('Password'), 'rahasia123')
    await userEvent.click(screen.getByRole('button', { name: 'Masuk' }))
    expect(await screen.findByText('Halaman panen')).toBeInTheDocument()
  })

  it('akun tanpa hak web admin ditolak dengan pesan jelas dan sesinya dicabut', async () => {
    http.masuk.mockResolvedValue(profil(['panen.kirim']))
    tampil()
    await userEvent.type(await screen.findByLabelText('Username'), 'krani1')
    await userEvent.type(screen.getByLabelText('Password'), 'krani123')
    await userEvent.click(screen.getByRole('button', { name: 'Masuk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('tidak punya akses ke web admin')
    expect(http.keluar).toHaveBeenCalledOnce()
    expect(screen.queryByText('Halaman dasbor')).not.toBeInTheDocument()
  })

  it('password salah menampilkan pesan dari server dan form tetap bisa dipakai', async () => {
    http.masuk.mockRejectedValue(new ApiError(401, 'Username atau password salah'))
    tampil()
    await userEvent.type(await screen.findByLabelText('Username'), 'admin1')
    await userEvent.type(screen.getByLabelText('Password'), 'salah')
    await userEvent.click(screen.getByRole('button', { name: 'Masuk' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Username atau password salah')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Masuk' })).toBeEnabled())
  })
})
