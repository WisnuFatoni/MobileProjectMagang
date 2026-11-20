import { describe, expect, it } from 'vitest'
import type { IpdBaris, IpdDetail, IpdKodeAgg, IpdLaporan, IpdRingkasan } from '@/api/types'
import { namaBerkas, namaLembar } from '@/lib/ekspor'
import { bulatPersen, dokumenDaftar, dokumenDetail, dokumenLaporan } from './ekspor'

const kode = (k: string, jenis: 'LOSES' | 'TEMUAN', pokok: number, jumlah: number, persen: number): IpdKodeAgg => ({
  kode: k,
  nama: `Nama ${k}`,
  kelompok: jenis === 'LOSES' ? 'BUAH' : 'PELEPAH',
  jenis,
  pokok,
  jumlah,
  persen_pokok: persen,
})

const ringkasan: IpdRingkasan = {
  pokok_diperiksa: 9,
  pokok_bertemuan: 4,
  persen_pokok_bertemuan: 44.44,
  total_temuan: 14,
  pokok_loses: 3,
  persen_pokok_loses: 33.333,
  total_loses: 13,
  pokok_temuan_lain: 1,
  persen_pokok_temuan_lain: 11.111,
  total_temuan_lain: 1,
  per_kode: [kode('A', 'LOSES', 1, 1, 11.1), kode('C', 'TEMUAN', 1, 1, 11.1), kode('S', 'LOSES', 0, 0, 0)],
}

const baris: IpdBaris = {
  id: 'x',
  tanggal_inspeksi: '2026-11-20',
  divisi_kode: 'DIV1',
  blok_kode: 'A12',
  pemanen_hrmb_code: 90001,
  pemanen_nama: 'Budi',
  ancak_no: '7',
  inspektur: 'mandor1',
  inspektur_nama: 'Mandor Satu',
  mandor_nama: 'Royyy',
  asisten_nama: 'Ferdi',
  status: 'DISETUJUI',
  pesan_tolak: null,
  catatan: null,
  dibuat_pada: '2026-11-20T03:00:00Z',
  disetujui_pada: null,
  ringkasan,
  foto: [],
}

describe('ekspor IPD', () => {
  it('persen dibulatkan satu desimal tetapi tetap angka', () => {
    expect(bulatPersen(33.333)).toBe(33.3)
    expect(typeof bulatPersen(50)).toBe('number')
  })

  it('daftar memuat satu baris per inspeksi dengan loses dan temuan lain terpisah, angka tetap angka', () => {
    const d = dokumenDaftar([baris], 'semua status')
    const t = d.tabel[0]
    expect(t.baris).toHaveLength(1)
    expect(t.kolom.length).toBe(t.baris[0].length)
    const r = t.baris[0]
    expect(r[4]).toBe('Royyy') // kemandoran
    expect(r[7]).toBe('Disetujui')
    expect(r[9]).toBe(3) // pokok loses
    expect(r[10]).toBe(33.3)
    expect(r[11]).toBe(1) // pokok temuan lain
    expect(d.keterangan?.some(([k, v]) => k === 'Jumlah inspeksi' && v === '1')).toBe(true)
  })

  it('laporan: ringkasan menyebut aturan loses, per kode menandai jenis, tiap tabel lengkap kolomnya', () => {
    const lap: IpdLaporan = {
      dari: '2026-11-01',
      sampai: '2026-11-20',
      inspeksi: 1,
      pemanen: 1,
      ringkasan,
      per_kelompok: [],
      per_pemanen: [
        {
          kunci: '1',
          nama: 'Budi',
          inspeksi: 1,
          pokok_diperiksa: 9,
          pokok_bertemuan: 4,
          persen_pokok_bertemuan: 44.4,
          total_temuan: 14,
          pokok_loses: 3,
          persen_pokok_loses: 33.3,
          total_loses: 13,
          pokok_temuan_lain: 1,
          persen_pokok_temuan_lain: 11.1,
          total_temuan_lain: 1,
          temuan_per_kode: { A: 1, C: 1 },
        },
      ],
      per_blok: [],
      per_divisi: [],
      per_tanggal: [],
    }
    const d = dokumenLaporan(lap, 'Divisi 1')
    expect(d.keterangan?.find(([k]) => k === 'Divisi')?.[1]).toBe('Divisi 1')
    expect(d.keterangan?.find(([k]) => k === 'Aturan')?.[1]).toContain('Loses = buah dan brondolan')
    expect(d.tabel.map((t) => t.judul)).toEqual(['Per kode', 'Per pemanen', 'Per blok', 'Per divisi', 'Per tanggal'])
    expect(d.tabel[0].baris.map((b) => b[2])).toEqual(['Loses', 'Temuan lain', 'Loses'])
    for (const t of d.tabel) for (const b of t.baris) expect(b.length).toBe(t.kolom.length)
    expect(d.tabel[1].baris[0][4]).toBe(33.3)
  })

  it('rincian satu inspeksi: pokok bersih ditulis Bersih, temuan memakai kode dan jumlah', () => {
    const det: IpdDetail = {
      ...baris,
      pokok: [
        { no_baris: 120, no_pokok: 1, temuan: [] },
        { no_baris: 121, no_pokok: 1, temuan: [{ kode: 'B1', jumlah: 6, catatan: null }, { kode: 'C', jumlah: 1, catatan: null }] },
      ],
    }
    const d = dokumenDetail(det)
    const pokok = d.tabel.find((t) => t.judul === 'Pokok diperiksa')
    expect(pokok?.baris).toEqual([
      [120, 1, 'Bersih'],
      [121, 1, 'B1 x6, C x1'],
    ])
    expect(d.tabel[0].baris.map((b) => b[0])).toEqual(['A', 'C']) // hanya kode yang punya temuan
  })

  it('nama berkas dan nama lembar aman dipakai', () => {
    expect(namaBerkas('Laporan Loses IPD', 'xlsx', new Date(2026, 10, 20))).toBe('Laporan_Loses_IPD_2026-11-20.xlsx')
    expect(namaBerkas('a/b:c*', 'pdf', new Date(2026, 0, 5))).toBe('abc_2026-01-05.pdf')
    const dipakai = new Set<string>()
    expect(namaLembar('Per pemanen/blok?', dipakai)).toBe('Per pemanen blok')
    expect(namaLembar('Per pemanen/blok?', dipakai)).toBe('Per pemanen blok 2')
    expect(namaLembar('x'.repeat(50), new Set()).length).toBe(31)
  })
})
