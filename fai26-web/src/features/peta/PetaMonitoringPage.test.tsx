import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Halaman, IpdBaris, IpdDetail } from '@/api/types'
import { PetaMonitoringPage } from './PetaMonitoringPage'

const { daftar, detail } = vi.hoisted(() => ({ daftar: vi.fn(), detail: vi.fn() }))

vi.mock('@/api/endpoints', () => ({
  ipdApi: { daftar: (f: unknown) => daftar(f), detail: (id: string) => detail(id) },
  divisiApi: { daftar: () => Promise.resolve([{ kode: 'DIV2', nama: 'Divisi 2' }]) },
}))
// Leaflet tidak dijalankan di jsdom; yang diuji adalah data yang diteruskan ke peta.
vi.mock('./PetaGabungan', () => ({ default: ({ jalur }: { jalur: { id: string }[] }) => <div data-testid="peta">{jalur.length} jalur</div> }))

const halaman = (item: IpdBaris[]): Halaman<IpdBaris> => ({ item, total: item.length, halaman: 1, per_halaman: 100 })
const baris = (id: string, inspektur: string, nama: string): IpdBaris =>
  ({ id, tanggal_inspeksi: '2026-11-21', divisi_kode: 'DIV2', blok_kode: 'D4', pemanen_nama: 'AMANAT', inspektur, inspektur_nama: nama, status: 'DISETUJUI' }) as unknown as IpdBaris
const denganJalur = (id: string, inspektur: string, nama: string): IpdDetail =>
  ({
    ...baris(id, inspektur, nama),
    pokok: [],
    jejak: { titik: [{ t: '2026-11-21T03:00:00Z', lat: -2.7, lon: 104.3, ak: 5 }, { t: '2026-11-21T03:05:00Z', lat: -2.699, lon: 104.3, ak: 5 }], jarak_m: 110, durasi_menit: 5 },
  }) as unknown as IpdDetail
const tanpaJalur = (id: string, inspektur: string, nama: string): IpdDetail => ({ ...baris(id, inspektur, nama), pokok: [], jejak: null }) as unknown as IpdDetail

function tampil() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <PetaMonitoringPage />
    </QueryClientProvider>,
  )
}

describe('halaman Peta Monitoring IPD', () => {
  beforeEach(() => {
    daftar.mockReset()
    detail.mockReset()
  })

  it('memakai endpoint yang sudah ada: daftar satu hari lalu rincian tiap inspeksi, dan memberi tahu bahwa pembukaan tercatat', async () => {
    daftar.mockResolvedValue(halaman([baris('i1', 'adi', 'Adi'), baris('i2', 'budi', 'Budi'), baris('i3', 'budi', 'Budi')]))
    detail.mockImplementation((id: string) => Promise.resolve(id === 'i3' ? tanpaJalur(id, 'budi', 'Budi') : denganJalur(id, id === 'i1' ? 'adi' : 'budi', id === 'i1' ? 'Adi' : 'Budi')))
    tampil()

    expect(screen.getByText('Ini data lokasi petugas')).toBeTruthy()
    await waitFor(() => expect(screen.getByTestId('peta').textContent).toBe('2 jalur'), { timeout: 5000 })
    const f = daftar.mock.calls[0][0] as { dari: string; sampai: string }
    expect(f.dari).toBe(f.sampai) // satu hari
    expect(detail).toHaveBeenCalledTimes(3)
    expect(screen.getByText('Tanpa jalur')).toBeTruthy()
    expect(screen.getAllByText('Ada jalur').length).toBeGreaterThanOrEqual(2)
  })

  it('tanpa inspeksi pada hari itu menampilkan keterangan kosong dan tidak membuka jalur apa pun', async () => {
    daftar.mockResolvedValue(halaman([]))
    tampil()
    expect(await screen.findByText(/Tidak ada inspeksi IPD pada/)).toBeTruthy()
    expect(detail).not.toHaveBeenCalled()
  })

  it('bila semua inspeksi tanpa jalur, peta tidak digambar dan alasannya dijelaskan', async () => {
    daftar.mockResolvedValue(halaman([baris('i1', 'adi', 'Adi')]))
    detail.mockResolvedValue(tanpaJalur('i1', 'adi', 'Adi'))
    tampil()
    expect(await screen.findByText(/Tidak ada jalur GPS pada inspeksi yang dipilih/)).toBeTruthy()
    expect(screen.queryByTestId('peta')).toBeNull()
  })

  it('memilih petugas hanya membuka jalur milik petugas itu (membatasi catatan audit)', async () => {
    daftar.mockResolvedValue(halaman([baris('i1', 'adi', 'Adi'), baris('i2', 'budi', 'Budi')]))
    detail.mockImplementation((id: string) => Promise.resolve(denganJalur(id, id === 'i1' ? 'adi' : 'budi', id === 'i1' ? 'Adi' : 'Budi')))
    tampil()
    await waitFor(() => expect(detail).toHaveBeenCalledTimes(2), { timeout: 5000 })
    detail.mockClear()

    await userEvent.click(await screen.findByRole('combobox', { name: 'Petugas' }))
    await userEvent.click(await screen.findByText('Budi', { selector: '.ant-select-item-option-content' }))
    await waitFor(() => expect(screen.getByTestId('peta').textContent).toBe('1 jalur'), { timeout: 5000 })
    // rincian yang sudah dimuat dipakai ulang dari cache; yang tidak dipilih tidak diminta lagi
    expect(detail).not.toHaveBeenCalledWith('i1')
  })

  it('rincian yang gagal dimuat ditandai, dan yang lain tetap tampil', async () => {
    daftar.mockResolvedValue(halaman([baris('i1', 'adi', 'Adi'), baris('i2', 'budi', 'Budi')]))
    detail.mockImplementation((id: string) => (id === 'i2' ? Promise.reject(new Error('galat')) : Promise.resolve(denganJalur(id, 'adi', 'Adi'))))
    tampil()
    await waitFor(() => expect(screen.getByTestId('peta').textContent).toBe('1 jalur'), { timeout: 5000 })
    expect(await screen.findByText('Gagal dimuat')).toBeTruthy()
  })
})
