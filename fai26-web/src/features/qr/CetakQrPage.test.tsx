import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Tph } from '@/api/types'
import { LembarQr } from './LembarQr'
import { TATA_LETAK, bagiHalaman } from './tataLetak'

const tph = (n: number): Tph => ({
  tph_id: n,
  blok_id: 1,
  blok_kode: 'A12',
  no_tph: String(n).padStart(2, '0'),
  qr_kode: `FAI26|TPH|1|KP_STL|A12|${String(n).padStart(2, '0')}`,
  lat: null,
  lon: null,
  aktif: true,
})

describe('cetak QR TPH', () => {
  it('tata letak besar memenuhi ukuran minimal 12 cm dari spesifikasi', () => {
    expect(TATA_LETAK.besar.cm).toBeGreaterThanOrEqual(12)
    expect(TATA_LETAK.besar.perHalaman).toBe(1)
    expect(TATA_LETAK.kecil.cm).toBeLessThan(12) // ditandai hanya untuk uji
  })

  it('membagi daftar ke halaman', () => {
    expect(bagiHalaman([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]])
    expect(bagiHalaman([], 4)).toEqual([])
  })

  it('satu halaman per TPH pada ukuran besar, dengan teks yang bisa dibaca manusia', () => {
    const { container } = render(<LembarQr tph={[tph(1), tph(7)]} tataLetak="besar" />)
    expect(container.querySelectorAll('section')).toHaveLength(2)
    expect(screen.getByText('Blok A12 / TPH 07')).toBeInTheDocument()
    expect(screen.getByText('FAI26|TPH|1|KP_STL|A12|07')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'QR Blok A12 TPH 07' })).toBeInTheDocument()
  })

  it('empat per halaman pada ukuran sedang', () => {
    const { container } = render(<LembarQr tph={[1, 2, 3, 4, 5].map(tph)} tataLetak="sedang" />)
    expect(container.querySelectorAll('section')).toHaveLength(2)
    expect(container.querySelectorAll('figure')).toHaveLength(5)
  })
})
