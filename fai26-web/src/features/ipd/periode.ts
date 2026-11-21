import type { Dayjs } from 'dayjs'

export type ModePeriode = 'hari' | 'bulan' | 'tahun' | 'rentang'

export const NAMA_MODE: Record<ModePeriode, string> = { hari: 'Tanggal', bulan: 'Bulan', tahun: 'Tahun', rentang: 'Rentang' }

/** Periode terpilih menjadi dua tanggal (YYYY-MM-DD, awal dan akhir, termasuk keduanya). */
export function hitungPeriode(mode: ModePeriode, tanggal: Dayjs, rentang: [Dayjs, Dayjs]): { dari: string; sampai: string } {
  const f = (d: Dayjs) => d.format('YYYY-MM-DD')
  switch (mode) {
    case 'hari':
      return { dari: f(tanggal), sampai: f(tanggal) }
    case 'bulan':
      return { dari: f(tanggal.startOf('month')), sampai: f(tanggal.endOf('month')) }
    case 'tahun':
      return { dari: f(tanggal.startOf('year')), sampai: f(tanggal.endOf('year')) }
    default:
      return { dari: f(rentang[0]), sampai: f(rentang[1]) }
  }
}
