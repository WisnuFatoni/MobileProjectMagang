import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Halaman, IpdBaris, PanenRingkas } from '@/api/types'
import { TertundaPage } from './TertundaPage'

const { panen, ipd } = vi.hoisted(() => ({ panen: vi.fn(), ipd: vi.fn() }))

vi.mock('@/features/auth/AuthProvider', () => ({ useAuth: () => ({ bisa: () => true }) }))
vi.mock('@/api/endpoints', () => ({
  laporanApi: { panen: (f: unknown) => panen(f) },
  ipdApi: { daftar: (f: unknown) => ipd(f) },
}))

const JAM = 3_600_000
const lalu = (ms: number) => new Date(Date.now() - ms).toISOString()
const halaman = <T,>(item: T[]): Halaman<T> => ({ item, total: item.length, halaman: 1, per_halaman: 100 })

const catatan = (id: string, diterima: string): PanenRingkas => ({
  id,
  tanggal_panen: '2026-11-20',
  blok_kode: '10C',
  tph_kode: '5',
  total_janjang: 20,
  brondolan_kg: 0,
  status_erp: 'MENUNGGU',
  status_lokasi: 'TPH_TANPA_KOORDINAT',
  jarak_ke_tph_m: null,
  gps_mock: false,
  pencatat: 'Royyy',
  penyetuju: null,
  diterima_server: diterima,
})

const inspeksi = (id: string, dibuat: string, asisten: string | null): IpdBaris =>
  ({
    id,
    tanggal_inspeksi: '2026-11-21',
    divisi_kode: 'DIV2',
    blok_kode: 'D4',
    pemanen_hrmb_code: 1,
    pemanen_nama: 'AMANAT',
    ancak_no: null,
    inspektur: 'adiii',
    inspektur_nama: 'Adiii',
    mandor_nama: 'Wawan',
    asisten_nama: asisten,
    status: 'MENUNGGU',
    pesan_tolak: null,
    catatan: null,
    dibuat_pada: dibuat,
    disetujui_pada: null,
    ringkasan: { pokok_diperiksa: 18 },
    foto: [],
  }) as unknown as IpdBaris

function tampil() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <TertundaPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('halaman Persetujuan Tertunda', () => {
  beforeEach(() => {
    panen.mockReset()
    ipd.mockReset()
  })

  it('memakai endpoint yang sudah ada dengan filter status menunggu, bukan endpoint baru', async () => {
    panen.mockResolvedValue(halaman([]))
    ipd.mockResolvedValue(halaman([]))
    tampil()
    await waitFor(() => expect(panen).toHaveBeenCalled())
    expect(panen).toHaveBeenCalledWith(expect.objectContaining({ status_erp: 'MENUNGGU' }))
    expect(ipd).toHaveBeenCalledWith(expect.objectContaining({ status: 'MENUNGGU' }))
  })

  it('menandai yang lewat batas sebagai Terlambat dan yang baru sebagai Wajar, beserta hitungan per asisten', async () => {
    panen.mockResolvedValue(halaman([catatan('p1', lalu(72 * JAM))]))
    ipd.mockResolvedValue(halaman([inspeksi('i1', lalu(1 * JAM), 'Ginting'), inspeksi('i2', lalu(2 * JAM), 'Ginting')]))
    tampil()

    expect(await screen.findByText('Terlambat', { selector: '.ant-tag' })).toBeTruthy()
    expect(screen.getAllByText('Wajar', { selector: '.ant-tag' })).toHaveLength(2)
    expect(screen.getByText('Ginting: 2')).toBeTruthy()
    // Kartu ringkasan: 3 menunggu (1 panen, 2 IPD) dan 1 terlambat
    expect(screen.getByText('1 panen • 2 IPD')).toBeTruthy()
    expect(screen.getByText('menunggu lebih dari 8 jam')).toBeTruthy()
  })

  it('tanpa yang menunggu menampilkan keterangan semua sudah diperiksa', async () => {
    panen.mockResolvedValue(halaman([]))
    ipd.mockResolvedValue(halaman([]))
    tampil()
    expect(await screen.findByText('Tidak ada catatan yang menunggu. Semua sudah diperiksa krani.')).toBeTruthy()
    expect(screen.getByText('Tidak ada inspeksi yang menunggu. Semua sudah diperiksa asisten.')).toBeTruthy()
  })
})
