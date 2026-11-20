import { describe, expect, it } from 'vitest'
import { NAMA_KELAS, STATUS_ERP, angka, desimal, tanggal, waktu } from './format'

describe('format', () => {
  it('angka dan desimal memakai pemisah Indonesia dan menangani kosong', () => {
    expect(angka(1234567)).toBe('1.234.567')
    expect(angka(null)).toBe('-')
    expect(desimal(12.5)).toBe('12,5')
    expect(desimal(28.456, 2)).toBe('28,46')
    expect(desimal(undefined)).toBe('-')
  })

  it('tanggal dan waktu dalam bahasa Indonesia', () => {
    expect(tanggal('2026-11-10T03:00:00Z')).toMatch(/^\d{1,2} Nov 2026$/)
    expect(tanggal(null)).toBe('-')
    expect(waktu('2026-11-10T03:00:00Z')).toMatch(/Nov 2026 \d{2}\.\d{2}$/)
    expect(waktu(undefined)).toBe('-')
  })

  it('semua status ERP dan kelas model punya tampilan', () => {
    expect(Object.keys(STATUS_ERP).sort()).toEqual(['BARU', 'DIBACA', 'DIIMPOR', 'DITOLAK'])
    expect(Object.keys(NAMA_KELAS).sort()).toEqual(['abnormal', 'empty', 'overripe', 'ripe', 'underripe', 'unripe'])
  })
})
