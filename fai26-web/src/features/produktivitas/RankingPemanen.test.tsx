import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PemanenProduktif } from '@/api/types'
import { RankingPemanen } from './RankingPemanen'

const p = (o: Partial<PemanenProduktif>): PemanenProduktif => ({
  peringkat: 1,
  hrmb_code: 800001,
  nama: 'ALFARIZI',
  catatan: 2,
  janjang: 30,
  janjang_matang: 27,
  persen_matang: 90,
  brondolan_kg: 12.5,
  menunggu_persetujuan: 0,
  ...o,
})

describe('RankingPemanen', () => {
  it('menampilkan peringkat, nama, kode ERP, janjang, persen matang, dan brondolan', () => {
    render(<RankingPemanen data={[p({}), p({ peringkat: 2, hrmb_code: 800002, nama: 'ALPA', janjang: 10, persen_matang: 50 })]} rata={20} />)
    expect(screen.getByText('ALFARIZI')).toBeInTheDocument()
    expect(screen.getByText('800001')).toBeInTheDocument()
    expect(screen.getByText('ALPA')).toBeInTheDocument()
    expect(screen.getByText('90%')).toBeInTheDocument()
    expect(screen.getByText('50%')).toBeInTheDocument()
    expect(screen.getAllByText('12,5 kg').length).toBeGreaterThan(0)
  })

  it('menandai di atas dan di bawah rata-rata dengan teks, bukan hanya warna', () => {
    render(<RankingPemanen data={[p({}), p({ peringkat: 2, hrmb_code: 800002, nama: 'ALPA', janjang: 10 })]} rata={20} />)
    expect(screen.getByText('Di atas rata-rata')).toBeInTheDocument()
    expect(screen.getByText('Di bawah rata-rata')).toBeInTheDocument()
  })

  it('menunjukkan catatan yang masih menunggu persetujuan', () => {
    render(<RankingPemanen data={[p({ menunggu_persetujuan: 2 })]} rata={30} />)
    expect(screen.getByText('2 menunggu persetujuan')).toBeInTheDocument()
    expect(screen.getByText('Sama dengan rata-rata')).toBeInTheDocument()
  })

  it('pemanen yang tidak ada di master diberi tulisan jelas', () => {
    render(<RankingPemanen data={[p({ nama: '' })]} rata={30} />)
    expect(screen.getByText('Pemanen tidak ada di master')).toBeInTheDocument()
  })

  it('pesan ramah bila belum ada catatan, dan menjelaskan bahwa premi dihitung di ERP', () => {
    render(<RankingPemanen data={[]} rata={0} />)
    expect(screen.getByText('Belum ada catatan panen pada tanggal ini.')).toBeInTheDocument()
    expect(screen.getByText(/Premi dihitung di ERP/)).toBeInTheDocument()
  })
})
