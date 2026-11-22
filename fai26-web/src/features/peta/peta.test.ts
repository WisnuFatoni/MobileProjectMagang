import { describe, expect, it } from 'vitest'
import type { IpdBaris, IpdDetail } from '@/api/types'
import { MAKS_JALUR, PALET_PETUGAS, daftarPetugas, ringkasPerPetugas, ringkasPeta, saringPetugas, statusDari, tanggalLokal, warnaPerPetugas, type ItemPeta } from './peta'

const baris = (id: string, inspektur: string, nama: string): IpdBaris => ({ id, inspektur, inspektur_nama: nama }) as unknown as IpdBaris
const detail = (jarak: number | null, titik = 3): IpdDetail =>
  ({ jejak: jarak === null ? null : { titik: Array.from({ length: titik }, (_, i) => ({ t: '2026-11-21T03:00:00Z', lat: -2.7 + i / 1000, lon: 104.3, ak: 5 })), jarak_m: jarak, durasi_menit: 5 } }) as unknown as IpdDetail
const item = (id: string, u: string, nama: string, status: ItemPeta['status'], jarak: number | null = null): ItemPeta => ({
  baris: baris(id, u, nama),
  status,
  detail: status === 'ada' ? detail(jarak ?? 0) : null,
})

describe('peta monitoring: warna petugas', () => {
  it('menetapkan warna menurut urutan nama sehingga tetap walau urutan data berubah', () => {
    const a = warnaPerPetugas(['rony', 'adi', 'rony', 'budi'])
    const b = warnaPerPetugas(['budi', 'rony', 'adi'])
    expect([...a.keys()]).toEqual(['adi', 'budi', 'rony'])
    expect(a.get('adi')).toBe(PALET_PETUGAS[0])
    expect(a.get('rony')).toBe(PALET_PETUGAS[2])
    expect(b.get('adi')).toBe(a.get('adi'))
    expect(b.get('rony')).toBe(a.get('rony'))
  })

  it('memakai ulang warna dari awal bila petugas lebih banyak dari palet', () => {
    const banyak = Array.from({ length: PALET_PETUGAS.length + 2 }, (_, i) => `p${String(i).padStart(2, '0')}`)
    const w = warnaPerPetugas(banyak)
    expect(w.get(banyak[PALET_PETUGAS.length])).toBe(PALET_PETUGAS[0])
  })
})

describe('peta monitoring: penyaringan petugas', () => {
  const data = [{ inspektur: 'a' }, { inspektur: 'b' }, { inspektur: 'a' }]

  it('daftar petugas kosong berarti semua petugas', () => {
    expect(saringPetugas(data, [])).toHaveLength(3)
  })

  it('menyaring menurut petugas yang dipilih dan tidak mengubah daftar asli', () => {
    expect(saringPetugas(data, ['a'])).toHaveLength(2)
    expect(saringPetugas(data, ['b', 'x'])).toHaveLength(1)
    expect(saringPetugas(data, ['x'])).toHaveLength(0)
    expect(data).toHaveLength(3)
  })

  it('pilihan petugas tanpa duplikat, terurut menurut nama, dan memakai username bila nama kosong', () => {
    const p = daftarPetugas([
      { inspektur: 'rony', inspektur_nama: 'Rony' },
      { inspektur: 'adi', inspektur_nama: 'Adi' },
      { inspektur: 'rony', inspektur_nama: 'Rony' },
      { inspektur: 'x1', inspektur_nama: '' },
    ])
    expect(p).toEqual([
      { username: 'adi', nama: 'Adi' },
      { username: 'rony', nama: 'Rony' },
      { username: 'x1', nama: 'x1' },
    ])
  })
})

describe('peta monitoring: ringkasan', () => {
  const daftar = [item('1', 'a', 'Adi', 'ada', 100.04), item('2', 'a', 'Adi', 'ada', 50.5), item('3', 'b', 'Budi', 'tanpa'), item('4', 'b', 'Budi', 'memuat'), item('5', 'c', 'Cici', 'gagal')]

  it('menghitung inspeksi menurut keadaan jalur, jumlah petugas, dan total jarak', () => {
    expect(ringkasPeta(daftar)).toEqual({ inspeksi: 5, denganJalur: 2, tanpaJalur: 1, memuat: 1, gagal: 1, petugas: 3, jarakM: 150.5 })
  })

  it('daftar kosong menghasilkan nol, bukan NaN', () => {
    expect(ringkasPeta([])).toEqual({ inspeksi: 0, denganJalur: 0, tanpaJalur: 0, memuat: 0, gagal: 0, petugas: 0, jarakM: 0 })
  })

  it('ringkasan per petugas: jumlah, yang berjalur, dan jarak', () => {
    expect(ringkasPerPetugas(daftar)).toEqual([
      { username: 'a', nama: 'Adi', inspeksi: 2, denganJalur: 2, jarakM: 150.5 },
      { username: 'b', nama: 'Budi', inspeksi: 2, denganJalur: 0, jarakM: 0 },
      { username: 'c', nama: 'Cici', inspeksi: 1, denganJalur: 0, jarakM: 0 },
    ])
  })
})

describe('peta monitoring: status jalur dan tanggal', () => {
  it('galat didahulukan, lalu memuat, lalu ada atau tanpa jalur', () => {
    expect(statusDari(false, true, undefined)).toBe('gagal')
    expect(statusDari(true, false, undefined)).toBe('memuat')
    expect(statusDari(false, false, undefined)).toBe('memuat')
    expect(statusDari(false, false, detail(null))).toBe('tanpa')
    expect(statusDari(false, false, detail(10, 0))).toBe('tanpa')
    expect(statusDari(false, false, detail(10))).toBe('ada')
  })

  it('tanggal lokal memakai jam setempat, bukan UTC', () => {
    expect(tanggalLokal(new Date(2026, 10, 22, 0, 30))).toBe('2026-11-22')
    expect(tanggalLokal(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
  })

  it('batas muat serentak cukup kecil untuk menjaga jumlah catatan audit', () => {
    expect(MAKS_JALUR).toBeLessThanOrEqual(100)
  })
})
