import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { eksporExcel, eksporPdf, type DokumenEkspor } from './ekspor'

const dok: DokumenEkspor = {
  judul: 'Uji Ekspor',
  keterangan: [['Periode', '1 Nov 2026 sampai 20 Nov 2026']],
  tabel: [
    { judul: 'Per kode', kolom: ['Kode', 'Temuan', 'Pokok'], baris: [['A', 'Potong buah mentah', 3], ['C', '=1+1', 0]], kananIdx: [2] },
    { judul: 'Per kode', kolom: ['Kode'], baris: [] },
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
