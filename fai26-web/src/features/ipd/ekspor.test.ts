import { describe, expect, it } from 'vitest'
import type { IpdDetail, IpdKodeAgg, IpdRingkasan } from '@/api/types'
import { namaBerkas, perkiraanBaris } from '@/lib/ekspor'
import { blokInspeksi, bulatPersen, dampakPokok, kelompokkanPerHari, laporanHarian, ringkasKode, teksTemuanPokok } from './ekspor'

const kode = (k: string, nama: string, jenis: 'LOSES' | 'TEMUAN', pokok: number, jumlah: number, persen: number): IpdKodeAgg => ({
  kode: k,
  nama,
  kelompok: jenis === 'LOSES' ? 'BUAH' : 'PELEPAH',
  jenis,
  pokok,
  jumlah,
  persen_pokok: persen,
})

const ringkasan: IpdRingkasan = {
  pokok_diperiksa: 5,
  pokok_bertemuan: 2,
  persen_pokok_bertemuan: 40,
  total_temuan: 8,
  pokok_loses: 2,
  persen_pokok_loses: 40,
  total_loses: 7,
  pokok_temuan_lain: 1,
  persen_pokok_temuan_lain: 20,
  total_temuan_lain: 1,
  per_kode: [kode('B1', 'Brondolan tak dikutip', 'LOSES', 1, 6, 20), kode('A', 'Potong buah mentah', 'LOSES', 1, 1, 20), kode('C', 'Pelepah sengkleh', 'TEMUAN', 1, 1, 20)],
}

const det = (id: string, tgl: string, nama: string): IpdDetail => ({
  id,
  tanggal_inspeksi: tgl,
  divisi_kode: 'DIV1',
  blok_kode: 'A12',
  pemanen_hrmb_code: 90001,
  pemanen_nama: nama,
  ancak_no: '7',
  inspektur: 'mandor1',
  inspektur_nama: 'Mandor Satu',
  mandor_nama: 'Royyy',
  asisten_nama: 'Ferdi',
  status: 'DISETUJUI',
  pesan_tolak: null,
  catatan: null,
  dibuat_pada: `${tgl}T03:00:00Z`,
  disetujui_pada: null,
  ringkasan,
  foto: [],
  pokok: [
    { no_baris: 120, no_pokok: 1, temuan: [] },
    { no_baris: 120, no_pokok: 2, temuan: [{ kode: 'B1', jumlah: 6, catatan: null }] },
    { no_baris: 121, no_pokok: 5, temuan: [{ kode: 'A', jumlah: 1, catatan: null }, { kode: 'C', jumlah: 1, catatan: null }] },
    { no_baris: 121, no_pokok: 6, temuan: [] },
    { no_baris: 121, no_pokok: 7, temuan: [] },
  ],
})

describe('ekspor IPD: satu lembar per tanggal', () => {
  it('persen dibulatkan satu desimal tetapi tetap angka', () => {
    expect(bulatPersen(33.333)).toBe(33.3)
    expect(typeof bulatPersen(50)).toBe('number')
  })

  it('dampak dan teks temuan per pokok memakai nama temuan dan jenisnya', () => {
    const peta = new Map([
      ['A', { nama: 'Potong buah mentah', jenis: 'LOSES' }],
      ['C', { nama: 'Pelepah', jenis: 'TEMUAN' }],
    ])
    expect(dampakPokok([], peta)).toBe('Bersih')
    expect(dampakPokok([{ kode: 'A' }], peta)).toBe('Loses')
    expect(dampakPokok([{ kode: 'C' }], peta)).toBe('Temuan lain')
    expect(dampakPokok([{ kode: 'A' }, { kode: 'C' }], peta)).toBe('Loses + temuan lain')
    expect(teksTemuanPokok([{ kode: 'A', jumlah: 2 }, { kode: 'C', jumlah: 1 }], peta)).toBe('A Potong buah mentah x2; C Pelepah x1')
  })

  it('satu blok inspeksi: ancak dan pemanen di judul, siapa yang menginspeksi, hasil, dan SEMUA temuan per pokok', () => {
    const b = blokInspeksi(det('a', '2026-11-20', 'ALFARIZI'), 1, [])
    expect(b.judul).toBe('1. ALFARIZI  •  Blok A12  •  Ancak 7  •  DIV1')
    expect(b.meta[0]).toContain('Kemandoran: Royyy')
    expect(b.meta[0]).toContain('Diinspeksi oleh: Mandor Satu')
    expect(b.meta[0]).toContain('Asisten: Ferdi')
    expect(b.hasil).toContain('5 pokok diperiksa')
    expect(b.hasil).toContain('Loses 40% (2 pokok, jumlah 7)')
    expect(b.hasil).toContain('Temuan lain 20% (1 pokok, jumlah 1)')
    // Satu baris per temuan; pokok bersih tidak dicantumkan tetapi dihitung di penutup.
    expect(b.baris).toEqual([
      [120, 2, 'B1', 'Brondolan tak dikutip', 'Loses', 6, ''],
      [121, 5, 'A', 'Potong buah mentah', 'Loses', 1, ''],
      [121, 5, 'C', 'Pelepah sengkleh', 'Temuan lain', 1, ''],
    ])
    for (const baris of b.baris) expect(baris.length).toBe(b.kolom.length)
    expect(b.penutup).toBe('Pokok bersih: 3 dari 5. Pokok yang kena temuan: 2.')
  })

  it('inspeksi tanpa temuan ditulis semua bersih', () => {
    const d = { ...det('a', '2026-11-20', 'BUDI'), pokok: [{ no_baris: 1, no_pokok: 1, temuan: [] }] }
    const b = blokInspeksi(d, 2, [])
    expect(b.baris).toEqual([])
    expect(b.penutup).toBe('Semua 1 pokok bersih, tidak ada temuan.')
  })

  it('dikelompokkan per tanggal (urut naik), lalu per pemanen, dengan ringkasan harian', () => {
    const hari = kelompokkanPerHari(
      [det('3', '2026-11-20', 'CAHYO'), det('1', '2026-11-13', 'BUDI'), det('2', '2026-11-20', 'ALFARIZI')],
      new Map([['2', [{ keterangan: 'buah tinggal', dataUrl: 'data:image/jpeg;base64,AA', lebar: 4, tinggi: 3 }]]]),
    )
    expect(hari.map((h) => h.judul)).toEqual(['Inspeksi tanggal Jumat, 13 November 2026', 'Inspeksi tanggal Jumat, 20 November 2026'])
    expect(hari[1].inspeksi.map((b) => b.judul.split('  •  ')[0])).toEqual(['1. ALFARIZI', '2. CAHYO'])
    expect(hari[1].ringkas).toContain('2 inspeksi')
    expect(hari[1].ringkas).toContain('10 pokok diperiksa')
    expect(hari[1].ringkas).toContain('Loses 40% (4 pokok)')
    expect(hari[1].inspeksi[0].gambar[0].keterangan).toBe('buah tinggal')
    expect(hari[1].inspeksi[1].gambar).toEqual([])
  })

  it('laporan: keterangan atas memuat rumus, ringkasan per kode menjumlah semua inspeksi', () => {
    const daftar = [det('1', '2026-11-13', 'BUDI'), det('2', '2026-11-20', 'ALFARIZI')]
    const l = laporanHarian('Laporan Loses IPD', [['Periode', '1 sampai 20 Nov']], daftar, new Map())
    expect(l.keterangan.find(([k]) => k === 'Cara menghitung')?.[1]).toContain('Persen loses = jumlah POKOK')
    expect(l.keterangan.find(([k]) => k === 'Jumlah inspeksi')?.[1]).toBe('2')
    expect(l.keterangan.find(([k]) => k === 'Pokok diperiksa')?.[1]).toBe('10')
    expect(l.hari).toHaveLength(2)
    const t = ringkasKode(daftar)
    expect(t.kolom).toEqual(['Kode', 'Pokok', 'Jumlah', 'Temuan', 'Jenis', '% pokok'])
    expect(t.baris.find((b) => b[0] === 'B1')).toEqual(['B1', 2, 12, 'Brondolan tak dikutip', 'Loses', 20])
    expect(l.ringkasKode?.baris.every((b) => b.length === t.kolom.length)).toBe(true)
  })

  it('nama berkas aman dipakai dan perkiraan baris teks wajar', () => {
    expect(namaBerkas('Laporan Loses IPD', 'xlsx', new Date(2026, 10, 20))).toBe('Laporan_Loses_IPD_2026-11-20.xlsx')
    expect(namaBerkas('a/b:c*', 'pdf', new Date(2026, 0, 5))).toBe('abc_2026-01-05.pdf')
    expect(perkiraanBaris('x'.repeat(250), 100)).toBe(3)
    expect(perkiraanBaris('', 100)).toBe(1)
  })
})
