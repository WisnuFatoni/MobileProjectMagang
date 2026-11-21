import { describe, expect, it } from 'vitest'
import type { IpdPokokOut } from '@/api/types'
import { haluskan, indeksTerdekat, jenisPokok, keTitikPeta, pasangkanPokok, teksDurasi, teksJarak } from './jalur'

const titik = keTitikPeta([
  { t: '2026-11-20T03:00:00Z', lat: -2.7, lon: 104.3, ak: 8 },
  { t: '2026-11-20T03:01:00Z', lat: -2.6999, lon: 104.3, ak: null },
  { t: '2026-11-20T03:02:00Z', lat: -2.6998, lon: 104.3001, ak: 5 },
  { t: '2026-11-20T03:03:00Z', lat: -2.6997, lon: 104.3001, ak: 5 },
])
const pokok = (baris: number, no: number, dicatat: string | null, kode: string[]): IpdPokokOut => ({
  no_baris: baris,
  no_pokok: no,
  dicatat_pada: dicatat,
  temuan: kode.map((k) => ({ kode: k, jumlah: 1, catatan: null })),
})

describe('jalur inspeksi di peta', () => {
  it('garis dihaluskan: ujung tetap, titik tengah dirata-rata tiga titik', () => {
    const g = haluskan(titik)
    expect(g[0]).toEqual([-2.7, 104.3])
    expect(g[3]).toEqual([-2.6997, 104.3001])
    expect(g[1][0]).toBeCloseTo((-2.7 + -2.6999 + -2.6998) / 3, 9)
    expect(g).toHaveLength(4)
    expect(haluskan([])).toEqual([])
  })

  it('titik terdekat menurut waktu, termasuk di luar ujung dan di antara dua titik', () => {
    const ms = (s: string) => Date.parse(s)
    expect(indeksTerdekat(titik, ms('2026-11-20T03:01:10Z'))).toBe(1)
    expect(indeksTerdekat(titik, ms('2026-11-20T03:01:40Z'))).toBe(2)
    expect(indeksTerdekat(titik, ms('2026-11-20T02:00:00Z'))).toBe(0)
    expect(indeksTerdekat(titik, ms('2026-11-20T09:00:00Z'))).toBe(3)
    expect(indeksTerdekat([], 1)).toBe(-1)
  })

  it('jenis pokok: loses menang atas temuan lain, pelepah saja = temuan lain, tanpa temuan = bersih', () => {
    const jenis = new Map([['B1', 'LOSES'], ['C', 'TEMUAN']])
    expect(jenisPokok(pokok(1, 1, null, []), jenis)).toBe('BERSIH')
    expect(jenisPokok(pokok(1, 1, null, ['C']), jenis)).toBe('LAIN')
    expect(jenisPokok(pokok(1, 1, null, ['C', 'B1']), jenis)).toBe('LOSES')
  })

  it('pokok dipasangkan dengan titik terdekat; tanpa waktu atau terlalu jauh dari jalur dilewati', () => {
    const jenis = new Map([['B1', 'LOSES']])
    const hasil = pasangkanPokok(
      [
        pokok(120, 1, '2026-11-20T03:00:20Z', []),
        pokok(120, 2, '2026-11-20T03:02:50Z', ['B1']),
        pokok(120, 3, null, ['B1']), // HP lama: tanpa waktu
        pokok(121, 1, '2026-11-20T05:00:00Z', []), // dua jam setelah jalur berakhir
      ],
      titik,
      jenis,
    )
    expect(hasil.map((h) => [h.pokok.no_pokok, h.jenis, h.titik.lat])).toEqual([
      [1, 'BERSIH', -2.7],
      [2, 'LOSES', -2.6997],
    ])
  })

  it('teks jarak dan durasi', () => {
    expect(teksJarak(340.4)).toBe('340 m')
    expect(teksJarak(1250)).toMatch(/^1,25 km$/)
    expect(teksDurasi(0.4)).toBe('kurang dari 1 menit')
    expect(teksDurasi(42)).toBe('42 menit')
    expect(teksDurasi(65)).toBe('1 jam 5 menit')
  })
})
