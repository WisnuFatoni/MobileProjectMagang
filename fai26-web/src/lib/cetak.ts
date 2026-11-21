import type { LaporanEkspor, Sel } from './ekspor'

const ESC: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
/** Semua teks dari data (nama, catatan, keterangan foto) dilewatkan ini sebelum masuk HTML cetak. */
export const esc = (v: Sel): string => (v === null || v === undefined ? '' : String(v)).replace(/[&<>"']/g, (c) => ESC[c])

const CSS = `
@page { size: A4; margin: 14mm; }
* { box-sizing: border-box; }
body { font-family: Arial, Helvetica, sans-serif; font-size: 10.5px; color: #111; margin: 0; }
h1 { font-size: 20px; margin: 0 0 6px; }
h3 { font-size: 12px; margin: 12px 0 4px; }
.ket { margin: 1px 0; color: #374151; }
table { width: 100%; border-collapse: collapse; margin: 4px 0; }
thead { display: table-header-group; }
th { background: #166534; color: #fff; text-align: left; padding: 3px 5px; font-size: 10px; }
td { padding: 3px 5px; border-bottom: 1px solid #e5e7eb; vertical-align: top; }
tr { break-inside: avoid; }
.r { text-align: right; }
.hari { margin-top: 14px; }
.pita-hari { background: #15803d; color: #fff; font-weight: bold; font-size: 13px; padding: 5px 8px; break-after: avoid; }
.ringkas { background: #f3f4f6; padding: 3px 8px; margin-bottom: 8px; break-after: avoid; }
.insp { margin: 0 0 12px; }
.kepala { break-inside: avoid; break-after: avoid; }
.pita-insp { background: #dcfce7; font-weight: bold; font-size: 12px; padding: 4px 8px; }
.meta { color: #374151; margin-top: 2px; }
.hasil { font-weight: bold; margin: 3px 0 4px; }
.penutup { font-style: italic; color: #4b5563; margin: 2px 0 4px; }
.foto { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
figure { margin: 0; width: calc(33.3% - 6px); break-inside: avoid; }
img { max-width: 100%; max-height: 60mm; display: block; }
figcaption { font-size: 9px; color: #4b5563; margin-top: 2px; }
`

function tabel(kolom: string[], baris: Sel[][], kanan: number[] | undefined): string {
  const kls = (i: number) => (kanan?.includes(i) ? ' class="r"' : '')
  return (
    `<table><thead><tr>${kolom.map((k, i) => `<th${kls(i)}>${esc(k)}</th>`).join('')}</tr></thead><tbody>` +
    baris.map((b) => `<tr>${b.map((v, i) => `<td${kls(i)}>${esc(v)}</td>`).join('')}</tr>`).join('') +
    '</tbody></table>'
  )
}

/**
 * Laporan sebagai halaman HTML siap cetak (A4), isinya sama dengan Excel dan PDF. Tabel mengulang kepala di halaman baru, baris
 * tidak terpotong, dan kepala tiap inspeksi tidak ditinggal sendirian di dasar halaman.
 */
export function htmlLaporan(lap: LaporanEkspor): string {
  const ket = lap.keterangan.map(([k, v]) => `<div class="ket"><b>${esc(k)}:</b> ${esc(v)}</div>`).join('')
  const kode = lap.ringkasKode ? `<h3>${esc(lap.ringkasKode.judul)}</h3>${tabel(lap.ringkasKode.kolom, lap.ringkasKode.baris, lap.ringkasKode.kananIdx)}` : ''
  const hari = lap.hari
    .map((h) => {
      const isi = h.inspeksi
        .map((b) => {
          const foto =
            b.gambar.length === 0
              ? ''
              : '<div class="foto">' +
                b.gambar.map((g) => `<figure><img src="${g.dataUrl.replace(/"/g, '')}" alt=""><figcaption>${esc(g.keterangan || 'Tanpa keterangan')}</figcaption></figure>`).join('') +
                '</div>'
          return (
            `<div class="insp"><div class="kepala"><div class="pita-insp">${esc(b.judul)}</div>` +
            b.meta.map((m) => `<div class="meta">${esc(m)}</div>`).join('') +
            `<div class="hasil">${esc(b.hasil)}</div></div>` +
            (b.baris.length > 0 ? tabel(b.kolom, b.baris, b.kananIdx) : '') +
            `<div class="penutup">${esc(b.penutup)}</div>${foto}</div>`
          )
        })
        .join('')
      return `<section class="hari"><div class="pita-hari">${esc(h.judul)}</div><div class="ringkas">${esc(h.ringkas)}</div>${isi}</section>`
    })
    .join('')
  return `<!doctype html><html lang="id"><head><meta charset="utf-8"><title>${esc(lap.judul)}</title><style>${CSS}</style></head><body><h1>${esc(lap.judul)}</h1>${ket}${kode}${hari}</body></html>`
}

/**
 * Mencetak laporan langsung: halaman cetak dimuat di bingkai tersembunyi lalu dialog cetak peramban dibuka (di situ juga bisa
 * "Simpan sebagai PDF"). Menunggu semua foto selesai dimuat supaya tidak tercetak kosong.
 */
export async function cetakLaporan(lap: LaporanEkspor): Promise<void> {
  const bingkai = document.createElement('iframe')
  bingkai.setAttribute('aria-hidden', 'true')
  bingkai.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;'
  document.body.appendChild(bingkai)
  await new Promise<void>((selesai) => {
    bingkai.onload = () => selesai()
    bingkai.srcdoc = htmlLaporan(lap)
  })
  const jendela = bingkai.contentWindow
  if (!jendela) {
    bingkai.remove()
    throw new Error('Jendela cetak tidak tersedia')
  }
  await Promise.all(
    [...jendela.document.images].map((i) => (i.complete ? Promise.resolve() : new Promise<void>((ok) => { i.onload = () => ok(); i.onerror = () => ok() }))),
  )
  jendela.onafterprint = () => bingkai.remove()
  jendela.focus()
  jendela.print()
  // Cadangan bila peramban tidak memicu afterprint.
  setTimeout(() => bingkai.remove(), 5 * 60_000)
}
