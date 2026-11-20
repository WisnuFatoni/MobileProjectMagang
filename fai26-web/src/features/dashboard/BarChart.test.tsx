import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { BarChart } from './BarChart'

describe('BarChart', () => {
  it('menampilkan nilai dan tanggal di setiap batang, tidak hanya warna', () => {
    render(<BarChart data={[{ tanggal: '2026-11-09', catatan: 3, janjang: 120 }, { tanggal: '2026-11-10', catatan: 5, janjang: 1500 }]} />)
    expect(screen.getByRole('img', { name: /Janjang per hari/ })).toBeInTheDocument()
    expect(screen.getByText('120')).toBeInTheDocument()
    expect(screen.getByText('1.500')).toBeInTheDocument()
    expect(screen.getByText('10/11')).toBeInTheDocument()
    expect(screen.getByText('5 cat.')).toBeInTheDocument()
  })

  it('pesan ramah bila belum ada data', () => {
    render(<BarChart data={[]} />)
    expect(screen.getByText(/Belum ada catatan/)).toBeInTheDocument()
  })
})
