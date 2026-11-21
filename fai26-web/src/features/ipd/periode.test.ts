import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { hitungPeriode } from './periode'

const hari = dayjs('2026-11-20')

describe('periode ekspor', () => {
  it('satu tanggal: awal dan akhir sama', () => {
    expect(hitungPeriode('hari', hari, [hari, hari])).toEqual({ dari: '2026-11-20', sampai: '2026-11-20' })
  })
  it('satu bulan penuh, termasuk bulan 28 dan 30 hari', () => {
    expect(hitungPeriode('bulan', hari, [hari, hari])).toEqual({ dari: '2026-11-01', sampai: '2026-11-30' })
    expect(hitungPeriode('bulan', dayjs('2026-02-10'), [hari, hari])).toEqual({ dari: '2026-02-01', sampai: '2026-02-28' })
  })
  it('satu tahun penuh', () => {
    expect(hitungPeriode('tahun', hari, [hari, hari])).toEqual({ dari: '2026-01-01', sampai: '2026-12-31' })
  })
  it('rentang memakai dua tanggal yang dipilih', () => {
    expect(hitungPeriode('rentang', hari, [dayjs('2026-11-03'), dayjs('2026-11-17')])).toEqual({ dari: '2026-11-03', sampai: '2026-11-17' })
  })
})
