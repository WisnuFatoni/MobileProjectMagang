import { describe, expect, it } from 'vitest'
import type { HakAksesRole } from '@/api/types'
import { dariServer, hitungPerubahan, kotakDikunci, ubahKotak } from './perubahan'

const role: HakAksesRole[] = [
  { role_id: 1, kode: 'KRANI', nama: 'Krani', bawaan: true, permission: ['panen.kirim', 'panen.lihat'] },
  { role_id: 4, kode: 'ADMIN', nama: 'Admin', bawaan: true, permission: ['role.kelola', 'user.kelola'] },
]

describe('perubahan hak akses', () => {
  it('draft awal sama dengan server: tidak ada perubahan', () => {
    expect(hitungPerubahan(role, dariServer(role))).toEqual([])
  })

  it('mencatat yang ditambah dan dicabut per role, urut abjad', () => {
    let d = dariServer(role)
    d = ubahKotak(d, 1, 'laporan.lihat', true)
    d = ubahKotak(d, 1, 'panen.kirim', false)
    d = ubahKotak(d, 1, 'audit.lihat', true)
    expect(hitungPerubahan(role, d)).toEqual([
      { roleId: 1, kode: 'KRANI', tambah: ['audit.lihat', 'laporan.lihat'], cabut: ['panen.kirim'] },
    ])
  })

  it('menambah lalu membatalkan lagi tidak dihitung sebagai perubahan', () => {
    let d = dariServer(role)
    d = ubahKotak(d, 1, 'laporan.lihat', true)
    d = ubahKotak(d, 1, 'laporan.lihat', false)
    expect(hitungPerubahan(role, d)).toEqual([])
  })

  it('ubahKotak tidak memutasi draft lama', () => {
    const awal = dariServer(role)
    const baru = ubahKotak(awal, 1, 'laporan.lihat', true)
    expect(awal[1].has('laporan.lihat')).toBe(false)
    expect(baru[1].has('laporan.lihat')).toBe(true)
    expect(baru[4]).toBe(awal[4]) // role lain dipakai bersama, tidak disalin
  })

  it('mengosongkan semua hak sebuah role tercatat sebagai pencabutan semuanya', () => {
    let d = dariServer(role)
    d = ubahKotak(d, 1, 'panen.kirim', false)
    d = ubahKotak(d, 1, 'panen.lihat', false)
    expect(hitungPerubahan(role, d)).toEqual([{ roleId: 1, kode: 'KRANI', tambah: [], cabut: ['panen.kirim', 'panen.lihat'] }])
  })

  it('hanya role.kelola milik ADMIN yang dikunci', () => {
    expect(kotakDikunci('ADMIN', 'role.kelola')).toBe(true)
    expect(kotakDikunci('ADMIN', 'user.kelola')).toBe(false)
    expect(kotakDikunci('KRANI', 'role.kelola')).toBe(false)
  })
})
