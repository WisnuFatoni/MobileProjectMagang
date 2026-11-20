import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { IpdKodeAgg } from '@/api/types'
import { BatangLoses } from './BatangLoses'
import { temuanTeratas, warnaLoses } from './ipdTampil'

const kode = (k: string, nama: string, pokok: number, jumlah: number, persen: number): IpdKodeAgg => ({
  kode: k,
  nama,
  kelompok: 'BUAH',
  pokok,
  jumlah,
  persen_pokok: persen,
})

describe('IPD: tampilan loses', () => {
  it('batang per kode menampilkan angka (bukan hanya warna) dan kode tanpa temuan tetap 0%', () => {
    render(<BatangLoses data={[kode('A', 'Potong buah mentah', 3, 3, 15), kode('B1', 'Brondolan tak dikutip', 0, 0, 0)]} />)
    const daftar = screen.getByRole('list', { name: 'Loses per kode temuan' })
    expect(daftar.textContent).toContain('15%')
    expect(daftar.textContent).toContain('3 pokok')
    expect(daftar.textContent).toContain('0%')
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('tanpa data menampilkan keterangan, bukan grafik kosong', () => {
    render(<BatangLoses data={[]} />)
    expect(screen.getByText('Belum ada data.')).toBeTruthy()
  })

  it('warna loses naik menurut persen', () => {
    expect(warnaLoses(5)).toBe('#15803d')
    expect(warnaLoses(15)).toBe('#d97706')
    expect(warnaLoses(40)).toBe('#b91c1c')
  })

  it('temuan terbanyak diurutkan dan dibatasi tiga', () => {
    expect(temuanTeratas({ temuan_per_kode: { A: 1, B1: 11, S: 4, C: 2 } })).toBe('B1 11 • S 4 • C 2')
    expect(temuanTeratas({ temuan_per_kode: {} })).toBe('-')
    expect(temuanTeratas({ temuan_per_kode: { A: 0 } })).toBe('-')
  })
})
