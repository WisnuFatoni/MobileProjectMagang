import { describe, expect, it } from 'vitest'
import type { IpdBaris, IpdDetail, IpdKodeAgg, IpdLaporan, IpdRingkasan } from '@/api/types'
import { namaBerkas, namaLembar } from '@/lib/ekspor'
import { bulatPersen, dampakPokok, dokumenDaftar, dokumenDetail, dokumenLaporan, dokumenLaporanRinci, teksTemuanPokok } from './ekspor'

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
    expect(d.keterangan?.find(([k]) => k === 'Cara menghitung')?.[1]).toContain('Persen loses = jumlah POKOK')
    expect(d.tabel.map((t) => t.judul)).toEqual(['Arti kode temuan', 'Per kode', 'Per pemanen', 'Per blok', 'Per divisi', 'Per tanggal'])
    expect(d.tabel[0].baris.map((b) => b[3])).toEqual(['Loses', 'Temuan lain', 'Loses'])
    expect(d.tabel[1].baris.map((b) => b[2])).toEqual(['Loses', 'Temuan lain', 'Loses'])
    for (const t of d.tabel) for (const b of t.baris) expect(b.length).toBe(t.kolom.length)
    expect(d.tabel[2].baris[0][4]).toBe(33.3)
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
    // PDF: hanya pokok yang kena temuan, dengan nama temuan dan dampaknya; yang bersih diringkas di catatan.
    const kena = d.tabel.find((t) => t.judul === 'Pokok yang kena temuan')
    expect(kena?.baris).toEqual([[121, 1, 'B1 x6; C Nama C x1', 'Loses + temuan lain']])
    expect(kena?.catatan).toBe('Dari 2 pokok diperiksa: 1 kena temuan, 1 bersih.')
    // Excel: semua pokok, satu baris per temuan, pokok bersih tetap ada.
    const excel = d.tabel.find((t) => t.judul === 'Rincian pokok')
    expect(excel?.hanya).toBe('excel')
    expect(excel?.baris.map((b) => [b[4], b[5], b[6], b[8], b[9]])).toEqual([[120, 1, '-', '-', 0], [121, 1, 'B1', 'Loses', 6], [121, 1, 'C', 'Temuan lain', 1]])
    expect(d.tabel.find((t) => t.judul === 'Per kode')?.baris.map((b) => b[0])).toEqual(['A', 'C']) // hanya kode yang punya temuan
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

  it('laporan rinci: satu bagian per inspeksi dengan foto, dan lembar Excel inspeksi serta pokok', () => {
    const det: IpdDetail = { ...baris, foto: [{ id: 'f1', keterangan: 'buah tinggal', urut: 0 }], pokok: [{ no_baris: 120, no_pokok: 3, temuan: [{ kode: 'A', jumlah: 1, catatan: null }] }] }
    const lap = { dari: '2026-11-01', sampai: '2026-11-20', inspeksi: 1, pemanen: 1, ringkasan, per_kelompok: [], per_pemanen: [], per_blok: [], per_divisi: [], per_tanggal: [] } as IpdLaporan
    const foto = new Map([['x', [{ keterangan: 'buah tinggal', dataUrl: 'data:image/jpeg;base64,AA', lebar: 4, tinggi: 3 }]]])
    const d = dokumenLaporanRinci(lap, [det], foto)
    expect(d.bagian).toHaveLength(1)
    expect(d.bagian?.[0].gambar[0].keterangan).toBe('buah tinggal')
    expect(d.bagian?.[0].judul).toContain('Budi')
    expect(d.tabel.filter((t) => t.hanya === 'excel').map((t) => t.judul)).toEqual(['Inspeksi', 'Rincian pokok'])
    const inspeksi = d.tabel.find((t) => t.judul === 'Inspeksi')
    for (const b of inspeksi?.baris ?? []) expect(b.length).toBe(inspeksi?.kolom.length)
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
