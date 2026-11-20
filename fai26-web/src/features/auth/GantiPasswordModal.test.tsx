import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/http'
import { GantiPasswordModal } from './GantiPasswordModal'

const api = vi.hoisted(() => ({ gantiPassword: vi.fn() }))
const auth = vi.hoisted(() => ({ muatUlangProfil: vi.fn() }))

vi.mock('@/api/endpoints', () => ({ akunApi: { gantiPassword: api.gantiPassword } }))
vi.mock('./AuthProvider', () => ({ useAuth: () => ({ muatUlangProfil: auth.muatUlangProfil }) }))

const u = userEvent.setup({ delay: null })
const BATAS = 40_000

function tampil(props: { wajib?: boolean; onTutup?: () => void } = {}) {
  const onTutup = props.onTutup ?? vi.fn()
  render(
    <QueryClientProvider client={new QueryClient()}>
      <App>
        <GantiPasswordModal buka wajib={props.wajib} onTutup={onTutup} />
      </App>
    </QueryClientProvider>,
  )
  return onTutup
}

async function isi(lama: string, baru: string, ulangi: string) {
  await u.type(screen.getByLabelText('Password sekarang'), lama)
  await u.type(screen.getByLabelText('Password baru'), baru)
  await u.type(screen.getByLabelText('Ulangi password baru'), ulangi)
}

beforeEach(() => {
  api.gantiPassword.mockReset()
  auth.muatUlangProfil.mockReset().mockResolvedValue(undefined)
})

describe('GantiPasswordModal', () => {
  it('menolak isian kosong, terlalu pendek, sama dengan lama, atau ulangan tidak cocok tanpa memanggil server', async () => {
    tampil()
    await u.click(screen.getByRole('button', { name: 'Simpan password' }))
    expect(await screen.findByText('Isi password sekarang')).toBeInTheDocument()
    expect(screen.getByText('Isi password baru')).toBeInTheDocument()

    await u.type(screen.getByLabelText('Password sekarang'), 'lama12345')
    await u.type(screen.getByLabelText('Password baru'), 'abc')
    await u.click(screen.getByRole('button', { name: 'Simpan password' }))
    expect(await screen.findByText('Minimal 8 karakter')).toBeInTheDocument()

    await u.clear(screen.getByLabelText('Password baru'))
    await u.type(screen.getByLabelText('Password baru'), 'lama12345')
    await u.click(screen.getByRole('button', { name: 'Simpan password' }))
    expect(await screen.findByText('Harus berbeda dari password sekarang')).toBeInTheDocument()

    await u.clear(screen.getByLabelText('Password baru'))
    await u.type(screen.getByLabelText('Password baru'), 'BaruBanget#1')
    await u.type(screen.getByLabelText('Ulangi password baru'), 'BedaSendiri#1')
    await u.click(screen.getByRole('button', { name: 'Simpan password' }))
    expect(await screen.findByText('Tidak sama dengan password baru')).toBeInTheDocument()
    expect(api.gantiPassword).not.toHaveBeenCalled()
  }, BATAS)

  it('berhasil: memanggil API dengan password lama dan baru, memuat ulang profil, dan menutup dialog', async () => {
    api.gantiPassword.mockResolvedValue({ status: 'OK' })
    const onTutup = tampil()
    await isi('lama12345', 'BaruBanget#1', 'BaruBanget#1')
    await u.click(screen.getByRole('button', { name: 'Simpan password' }))

    await waitFor(() => expect(api.gantiPassword).toHaveBeenCalledWith('lama12345', 'BaruBanget#1'))
    await waitFor(() => expect(onTutup).toHaveBeenCalled())
    expect(auth.muatUlangProfil).toHaveBeenCalled()
  }, BATAS)

  it('password lama salah dari server: pesan tampil, dialog tetap terbuka', async () => {
    api.gantiPassword.mockRejectedValue(new ApiError(422, 'Password lama salah'))
    const onTutup = tampil()
    await isi('lamaSALAH1', 'BaruBanget#1', 'BaruBanget#1')
    await u.click(screen.getByRole('button', { name: 'Simpan password' }))

    expect(await screen.findByText('Password lama salah')).toBeInTheDocument()
    expect(onTutup).not.toHaveBeenCalled()
    expect(auth.muatUlangProfil).not.toHaveBeenCalled()
  }, BATAS)

  it('mode wajib: ada peringatan, tombol Batal tersembunyi, dan tidak bisa ditutup', () => {
    tampil({ wajib: true })
    expect(screen.getByText('Password Anda diatur ulang oleh admin')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
    // Tombol Batal disembunyikan (display: none), jadi tidak ada sebagai tombol yang bisa diakses.
    expect(screen.queryByRole('button', { name: 'Batal' })).not.toBeInTheDocument()
  }, BATAS)

  it('mode biasa: tombol Batal menutup dialog', async () => {
    const onTutup = tampil()
    await u.click(screen.getByRole('button', { name: 'Batal' }))
    expect(onTutup).toHaveBeenCalled()
  }, BATAS)
})
