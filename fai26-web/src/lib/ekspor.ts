/**
 * Ekspor tabel ke Excel (.xlsx) dan PDF di browser. Pustaka berat (exceljs, jspdf) dimuat hanya saat tombol ditekan supaya
 * halaman biasa tetap ringan.
 */

export type Sel = string | number | null | undefined

/** Satu tabel: kepala kolom dan barisnya. Angka tetap angka di Excel (bisa dijumlah), di PDF diformat. */
export interface TabelEkspor {
  /** Judul bagian (nama lembar di Excel, judul di atas tabel di PDF). */
  judul: string
  kolom: string[]
  baris: Sel[][]
  /** Kolom yang rata kanan (angka), nomor mulai 0. */
  kananIdx?: number[]
}

/** Pasangan label dan nilai di bagian atas dokumen (periode, filter, ringkasan). */
export type Keterangan = [string, string][]

export interface DokumenEkspor {
  judul: string
  /** Mis. "Periode 1 Nov 2026 sampai 20 Nov 2026" atau nama pembuat. */
  keterangan?: Keterangan
  tabel: TabelEkspor[]
}

const bersihNamaBerkas = (s: string) => s.replace(/[^\w\-. ]+/g, '').trim().replace(/\s+/g, '_') || 'ekspor'

/** Nama berkas: judul + tanggal hari ini (YYYY-MM-DD). */
export function namaBerkas(judul: string, ekstensi: 'xlsx' | 'pdf', hariIni = new Date()): string {
  const t = `${hariIni.getFullYear()}-${String(hariIni.getMonth() + 1).padStart(2, '0')}-${String(hariIni.getDate()).padStart(2, '0')}`
  return `${bersihNamaBerkas(judul)}_${t}.${ekstensi}`
}

function unduh(blob: Blob, nama: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nama
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}

/** Nama lembar Excel: maksimal 31 karakter dan tanpa \ / ? * [ ] : */
export function namaLembar(s: string, dipakai: Set<string>): string {
  const dasar = s.replace(/[\\/?*[\]:]/g, ' ').trim().slice(0, 31) || 'Lembar'
  let nama = dasar
  let n = 2
  while (dipakai.has(nama.toLowerCase())) {
    const akhir = ` ${n++}`
    nama = dasar.slice(0, 31 - akhir.length) + akhir
  }
  dipakai.add(nama.toLowerCase())
  return nama
}

const teks = (v: Sel): string => (v === null || v === undefined ? '' : String(v))

/** Excel: satu lembar "Ringkasan" (judul + keterangan) bila ada keterangan, lalu satu lembar per tabel. */
export async function eksporExcel(dok: DokumenEkspor, nama = namaBerkas(dok.judul, 'xlsx')): Promise<void> {
  const ExcelJS = (await import('exceljs')).default
  const wb = new ExcelJS.Workbook()
  wb.creator = 'fAI26'
  wb.created = new Date()
  const dipakai = new Set<string>()

  if (dok.keterangan && dok.keterangan.length > 0) {
    const ws = wb.addWorksheet(namaLembar('Ringkasan', dipakai))
    ws.addRow([dok.judul]).font = { bold: true, size: 14 }
    ws.addRow([])
    for (const [k, v] of dok.keterangan) {
      const r = ws.addRow([k, v])
      r.getCell(1).font = { bold: true }
    }
    ws.getColumn(1).width = 34
    ws.getColumn(2).width = 48
  }

  for (const t of dok.tabel) {
    const ws = wb.addWorksheet(namaLembar(t.judul, dipakai))
    const kepala = ws.addRow(t.kolom)
    kepala.font = { bold: true, color: { argb: 'FFFFFFFF' } }
    kepala.eachCell((c) => {
      c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF15803D' } }
      c.alignment = { vertical: 'middle', wrapText: true }
    })
    for (const b of t.baris) ws.addRow(b.map((v) => (v === undefined ? null : v)))
    ws.views = [{ state: 'frozen', ySplit: 1 }]
    t.kolom.forEach((k, i) => {
      let maks = k.length
      for (const b of t.baris) maks = Math.max(maks, teks(b[i]).length)
      ws.getColumn(i + 1).width = Math.min(Math.max(maks + 2, 8), 60)
      if (t.kananIdx?.includes(i)) ws.getColumn(i + 1).alignment = { horizontal: 'right' }
    })
  }

  const data = await wb.xlsx.writeBuffer()
  unduh(new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), nama)
}

/** PDF: judul, keterangan, lalu tiap tabel berurutan. Kertas A4; mendatar bila kolomnya banyak. */
export async function eksporPdf(dok: DokumenEkspor, nama = namaBerkas(dok.judul, 'pdf')): Promise<void> {
  const { jsPDF } = await import('jspdf')
  const autoTable = (await import('jspdf-autotable')).default
  const lebar = Math.max(0, ...dok.tabel.map((t) => t.kolom.length))
  const doc = new jsPDF({ orientation: lebar > 7 ? 'landscape' : 'portrait', unit: 'pt', format: 'a4' })
  const halamanLebar = doc.internal.pageSize.getWidth()
  let y = 40

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(15)
  doc.text(dok.judul, 40, y)
  y += 18
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(9)
  doc.setTextColor(90)
  for (const [k, v] of dok.keterangan ?? []) {
    const baris = doc.splitTextToSize(`${k}: ${v}`, halamanLebar - 80) as string[]
    for (const b of baris) {
      doc.text(b, 40, y)
      y += 12
    }
  }
  doc.setTextColor(0)
  y += 6

  for (const t of dok.tabel) {
    if (y > doc.internal.pageSize.getHeight() - 90) {
      doc.addPage()
      y = 40
    }
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(11)
    doc.text(t.judul, 40, y)
    autoTable(doc, {
      startY: y + 6,
      head: [t.kolom],
      body: t.baris.map((b) => b.map(teks)),
      styles: { fontSize: 8, cellPadding: 3 },
      headStyles: { fillColor: [21, 128, 61] },
      margin: { left: 40, right: 40 },
      columnStyles: Object.fromEntries((t.kananIdx ?? []).map((i) => [i, { halign: 'right' }])),
    })
    const akhir = (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? y + 40
    y = akhir + 22
  }

  const jumlah = doc.getNumberOfPages()
  doc.setFontSize(8)
  doc.setTextColor(120)
  for (let i = 1; i <= jumlah; i++) {
    doc.setPage(i)
    doc.text(`fAI26 • dicetak ${new Date().toLocaleString('id-ID')} • halaman ${i}/${jumlah}`, 40, doc.internal.pageSize.getHeight() - 20)
  }
  unduh(doc.output('blob'), nama)
}
