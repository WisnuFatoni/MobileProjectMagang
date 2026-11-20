import { describe, expect, it } from 'vitest'
import { pratinjauMassal } from './massal'

describe('pratinjauMassal', () => {
  it('menampilkan contoh nomor dengan nol di depan dan jumlahnya', () => {
    expect(pratinjauMassal(1, 20, 2)).toBe('01, 02, ... 20 (20 TPH)')
    expect(pratinjauMassal(5, 7, 3)).toBe('005, 006, 007 (3 TPH)')
    expect(pratinjauMassal(3, 3, 2)).toBe('03 (1 TPH)')
  })

  it('kosong bila isian belum lengkap atau terbalik', () => {
    expect(pratinjauMassal(null, 5, 2)).toBe('')
    expect(pratinjauMassal(5, 2, 2)).toBe('')
    expect(pratinjauMassal(1, 5, undefined)).toBe('')
  })
})
