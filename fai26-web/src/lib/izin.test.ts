import { describe, expect, it } from 'vitest'
import { HALAMAN, halamanAwal, halamanBoleh, punyaIzin } from './izin'

const admin = { permission: ['master.lihat', 'master.kelola', 'user.kelola', 'role.kelola', 'device.kelola', 'laporan.lihat', 'panen.lihat', 'audit.lihat', 'ipd.lihat', 'laporan.ipd'] }
const krani = { permission: ['master.lihat', 'panen.lihat', 'panen.kirim'] }

describe('izin', () => {
  it('punyaIzin menangani profil kosong', () => {
    expect(punyaIzin(null, 'master.lihat')).toBe(false)
    expect(punyaIzin(undefined, 'master.lihat')).toBe(false)
    expect(punyaIzin(admin, 'user.kelola')).toBe(true)
    expect(punyaIzin(krani, 'user.kelola')).toBe(false)
  })

  it('admin melihat semua halaman, urutannya mengikuti menu', () => {
    expect(halamanBoleh(admin).map((h) => h.path)).toEqual(HALAMAN.map((h) => h.path))
  })

  it('halaman Hak Akses hanya untuk pemegang role.kelola', () => {
    const tanpa = { permission: ['master.lihat', 'user.kelola', 'device.kelola', 'laporan.lihat', 'panen.lihat', 'audit.lihat'] }
    expect(halamanBoleh(admin).map((h) => h.path)).toContain('/hak-akses')
    expect(halamanBoleh(tanpa).map((h) => h.path)).not.toContain('/hak-akses')
  })

  it('krani hanya melihat halaman yang haknya dimiliki', () => {
    expect(halamanBoleh(krani).map((h) => h.path)).toEqual(['/panen', '/tertunda', '/blok', '/tph', '/divisi', '/pemanen'])
  })

  it('halaman awal: dasbor untuk yang punya laporan, kalau tidak halaman pertama yang boleh', () => {
    expect(halamanAwal(admin)).toBe('/')
    expect(halamanAwal(krani)).toBe('/panen')
    expect(halamanAwal({ permission: [] })).toBeNull()
    expect(halamanAwal(null)).toBeNull()
  })
})
