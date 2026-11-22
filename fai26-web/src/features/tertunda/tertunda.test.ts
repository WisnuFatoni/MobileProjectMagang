import { describe, expect, it } from 'vitest'
import { gabungRingkasan, hitungPerNama, lamaMenunggu, ringkasTunggu, teksLama, tingkatMenunggu, urutkanTerlama } from './tertunda'

const JAM = 3_600_000
const MENIT = 60_000
const SEKARANG = Date.parse('2026-11-22T10:00:00Z')
const lalu = (ms: number) => new Date(SEKARANG - ms).toISOString()

describe('persetujuan tertunda: lama menunggu', () => {
  it('menghitung selisih dari waktu mulai menunggu', () => {
    expect(lamaMenunggu(lalu(3 * JAM), SEKARANG)).toBe(3 * JAM)
  })

  it('waktu kosong, rusak, atau di masa depan dihitung nol (tidak membuat angka negatif atau NaN)', () => {
    expect(lamaMenunggu(null, SEKARANG)).toBe(0)
    expect(lamaMenunggu('bukan waktu', SEKARANG)).toBe(0)
    expect(lamaMenunggu(new Date(SEKARANG + JAM).toISOString(), SEKARANG)).toBe(0)
  })

  it('teks lama mudah dibaca', () => {
    expect(teksLama(10_000)).toBe('kurang dari 1 menit')
    expect(teksLama(45 * MENIT)).toBe('45 menit')
    expect(teksLama(3 * JAM)).toBe('3 jam')
    expect(teksLama(3 * JAM + 20 * MENIT)).toBe('3 jam 20 menit')
    expect(teksLama(24 * JAM)).toBe('1 hari')
    expect(teksLama(53 * JAM)).toBe('2 hari 5 jam')
  })
})

describe('persetujuan tertunda: tingkat keterlambatan', () => {
  it('normal, mendekati batas (75%), dan terlambat (lebih dari batas)', () => {
    expect(tingkatMenunggu(2 * JAM, 8)).toBe('normal')
    expect(tingkatMenunggu(6 * JAM, 8)).toBe('mendekati') // tepat 75%
    expect(tingkatMenunggu(8 * JAM, 8)).toBe('mendekati') // tepat di batas belum terlambat
    expect(tingkatMenunggu(8 * JAM + 1, 8)).toBe('terlambat')
  })

  it('batas yang lebih longgar menggeser tingkatnya', () => {
    expect(tingkatMenunggu(10 * JAM, 8)).toBe('terlambat')
    expect(tingkatMenunggu(10 * JAM, 24)).toBe('normal')
  })
})

describe('persetujuan tertunda: ringkasan', () => {
  const item = [
    { id: 'a', sejak: lalu(1 * JAM) },
    { id: 'b', sejak: lalu(7 * JAM) },
    { id: 'c', sejak: lalu(30 * JAM) },
  ]

  it('menghitung jumlah, terlambat, mendekati, terlama, dan rata-rata', () => {
    const r = ringkasTunggu(item, 8, SEKARANG)
    expect(r.jumlah).toBe(3)
    expect(r.terlambat).toBe(1)
    expect(r.mendekati).toBe(1)
    expect(r.terlamaMs).toBe(30 * JAM)
    expect(r.rataMs).toBe(Math.round((38 * JAM) / 3))
  })

  it('daftar kosong menghasilkan nol, bukan NaN', () => {
    expect(ringkasTunggu([], 8, SEKARANG)).toEqual({ jumlah: 0, terlambat: 0, mendekati: 0, terlamaMs: 0, rataMs: 0 })
  })

  it('gabungan panen dan IPD menjumlah dan memakai yang terlama', () => {
    const a = ringkasTunggu(item, 8, SEKARANG)
    const b = ringkasTunggu([{ id: 'x', sejak: lalu(50 * JAM) }], 8, SEKARANG)
    const g = gabungRingkasan(a, b)
    expect(g.jumlah).toBe(4)
    expect(g.terlambat).toBe(2)
    expect(g.terlamaMs).toBe(50 * JAM)
    expect(g.rataMs).toBe(Math.round((38 * JAM + 50 * JAM) / 4))
    expect(gabungRingkasan().jumlah).toBe(0)
  })

  it('diurutkan dari yang paling lama menunggu tanpa mengubah daftar asli', () => {
    const urut = urutkanTerlama(item)
    expect(urut.map((i) => i.id)).toEqual(['c', 'b', 'a'])
    expect(item.map((i) => i.id)).toEqual(['a', 'b', 'c'])
  })
})

describe('persetujuan tertunda: per asisten', () => {
  it('menghitung per nama, terbanyak dulu, dan menamai yang kosong', () => {
    expect(hitungPerNama(['Ginting', 'Ferdi', 'Ginting', null, ' ', 'Ginting'])).toEqual([
      { nama: 'Ginting', jumlah: 3 },
      { nama: 'Belum ditentukan', jumlah: 2 },
      { nama: 'Ferdi', jumlah: 1 },
    ])
  })
})
