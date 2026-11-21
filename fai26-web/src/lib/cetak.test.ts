import { describe, expect, it } from 'vitest'
import { htmlLaporan } from './cetak'
import type { LaporanEkspor } from './ekspor'

const lap: LaporanEkspor = {
  judul: 'Laporan <b>Uji</b>',
  keterangan: [['Periode', '1 Nov & 20 Nov']],
  ringkasKode: { judul: 'Ringkasan per kode', kolom: ['Kode', 'Pokok'], baris: [['A', 3]], kananIdx: [1] },
  hari: [
    {
      judul: 'Inspeksi tanggal Jumat, 20 November 2026',
      ringkas: '1 inspeksi',
      inspeksi: [
        {
          judul: '1. ALFARIZI',
          meta: ['Kemandoran: Royyy'],
          hasil: '10 pokok diperiksa',
          kolom: ['Baris', 'Temuan'],
          baris: [[120, '<script>alert(1)</script>']],
          kananIdx: [0],
          penutup: 'Pokok bersih: 9 dari 10.',
          gambar: [{ keterangan: 'foto "satu"', dataUrl: 'data:image/jpeg;base64,AAAA', lebar: 4, tinggi: 3 }],
        },
      ],
    },
  ],
}

describe('halaman cetak', () => {
  it('memuat tanggal, inspeksi, tabel, dan foto', () => {
    const h = htmlLaporan(lap)
    expect(h).toContain('Inspeksi tanggal Jumat, 20 November 2026')
    expect(h).toContain('1. ALFARIZI')
    expect(h).toContain('Pokok bersih: 9 dari 10.')
    expect(h).toContain('<img src="data:image/jpeg;base64,AAAA"')
    expect(h).toContain('@page { size: A4')
  })

  it('semua teks dari data di-escape (tidak ada HTML atau skrip yang lolos)', () => {
    const h = htmlLaporan(lap)
    expect(h).not.toContain('<script>')
    expect(h).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(h).toContain('Laporan &lt;b&gt;Uji&lt;/b&gt;')
    expect(h).toContain('1 Nov &amp; 20 Nov')
    expect(h).toContain('foto &quot;satu&quot;')
  })
})
