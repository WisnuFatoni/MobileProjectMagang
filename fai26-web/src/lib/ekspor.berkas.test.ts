import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { eksporExcel, eksporPdf, type LaporanEkspor } from './ekspor'

const dok: LaporanEkspor = {
  judul: 'Uji Ekspor',
  keterangan: [['Periode', '1 Nov 2026 sampai 20 Nov 2026']],
  ringkasKode: { judul: 'Ringkasan per kode', kolom: ['Kode', 'Pokok', 'Jumlah', 'Temuan', 'Jenis', '% pokok'], baris: [['A', 3, 4, 'Potong buah mentah', 'Loses', 30]], kananIdx: [1, 2, 5] },
  hari: [
    {
      judul: 'Inspeksi tanggal Jumat, 20 November 2026',
      ringkas: '1 inspeksi',
      inspeksi: [
        {
          judul: '1. ALFARIZI  •  Blok 10C  •  Ancak 7  •  DIV1',
          meta: ['Kemandoran: Royyy'],
          hasil: '10 pokok diperiksa',
          kolom: ['Baris', 'Pokok', 'Kode', 'Temuan', 'Dampak', 'Jumlah', 'Catatan'],
          baris: [[120, 2, 'A', '=1+1', 'Loses', 1, '']],
          kananIdx: [0, 1, 5],
          penutup: 'Pokok bersih: 9 dari 10.',
          gambar: [],
        },
      ],
    },
  ],
}

/** jsdom belum punya Blob.arrayBuffer: baca lewat FileReader. */
function baca(b: Blob): Promise<Uint8Array> {
  return new Promise((ok, gagal) => {
    const r = new FileReader()
    r.onload = () => ok(new Uint8Array(r.result as ArrayBuffer))
    r.onerror = () => gagal(r.error)
    r.readAsArrayBuffer(b)
  })
}

describe('ekspor berkas (Excel dan PDF) benar-benar menghasilkan berkas', () => {
  const blobs: Blob[] = []
  const nama: string[] = []

  beforeEach(() => {
    blobs.length = 0
    nama.length = 0
    vi.stubGlobal('URL', {
      createObjectURL: (b: Blob) => {
        blobs.push(b)
        return 'blob:uji'
      },
      revokeObjectURL: () => undefined,
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      nama.push(this.download)
    })
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('Excel: berkas xlsx (zip) terunduh dengan nama bertanggal', async () => {
    await eksporExcel(dok, 'uji.xlsx')
    expect(nama).toEqual(['uji.xlsx'])
    expect(blobs).toHaveLength(1)
    const bytes = await baca(blobs[0])
    expect(bytes.length).toBeGreaterThan(500)
    expect(String.fromCharCode(bytes[0], bytes[1])).toBe('PK') // tanda berkas zip (xlsx)
  }, 30000)

  it('PDF: berkas berawalan %PDF terunduh', async () => {
    await eksporPdf(dok, 'uji.pdf')
    expect(nama).toEqual(['uji.pdf'])
    expect(blobs).toHaveLength(1)
    const bytes = await baca(blobs[0])
    expect(String.fromCharCode(...bytes.slice(0, 4))).toBe('%PDF')
  }, 30000)
})
