/**
 * Ekspor tabel ke Excel (.xlsx) dan PDF di browser. Pustaka berat (exceljs, jspdf) dimuat hanya saat tombol ditekan supaya
 * halaman biasa tetap ringan.
 *
 * Dokumen terdiri dari: judul + keterangan, tabel-tabel, dan (opsional) bagian rinci yang memuat tabel kecil dan foto per
 * inspeksi. Tabel bisa dibatasi untuk salah satu format saja: tabel sangat panjang (satu baris per pokok) hanya cocok di Excel,
 * sedangkan di PDF yang dibaca atasan dipakai bagian rinci per inspeksi.
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
  /** Hanya dimasukkan ke format ini; kosong berarti keduanya. */
  hanya?: 'excel' | 'pdf'
  /** Catatan kecil di bawah tabel (PDF) atau di baris terakhir (tidak dipakai di Excel). */
  catatan?: string
}

/** Pasangan label dan nilai di bagian atas dokumen (periode, filter, ringkasan). */
export type Keterangan = [string, string][]

/** Foto yang sudah dikecilkan: JPEG data URL dengan ukuran aslinya (piksel). */
export interface GambarEkspor {
  keterangan: string
  dataUrl: string
  lebar: number
  tinggi: number
}

/** Satu inspeksi (atau bagian lain) lengkap dengan tabelnya dan foto-fotonya. */
export interface BagianEkspor {
  judul: string
  keterangan: Keterangan
  tabel: TabelEkspor[]
  gambar: GambarEkspor[]
}

export interface DokumenEkspor {
  judul: string
  /** Mis. "Periode 1 Nov 2026 sampai 20 Nov 2026" atau nama pembuat. */
  keterangan?: Keterangan
  tabel: TabelEkspor[]
  bagian?: BagianEkspor[]
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

/** Nama lembar Excel: maksimal 31 karakter dan tanpa \ / ? * [ ] : ; tidak boleh kembar (tanpa membedakan huruf besar kecil). */
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

/** Excel: lembar "Ringkasan" (judul + keterangan), satu lembar per tabel, dan lembar "Foto" bila ada foto. */
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
      r.getCell(1).font = { bold: true, color: { argb: 'FF166534' } }
      r.getCell(1).alignment = { vertical: 'top' }
      r.getCell(2).alignment = { wrapText: true, vertical: 'top' }
    }
    ws.getColumn(1).width = 34
    ws.getColumn(2).width = 90
  }

  for (const t of dok.tabel) {
    if (t.hanya === 'pdf') continue
    const ws = wb.addWorksheet(namaLembar(t.judul, dipakai))
    const kepala = ws.addRow(t.kolom)
    kepala.font = { bold: true, color: { argb: 'FFFFFFFF' } }
    kepala.eachCell((c) => {
      c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF15803D' } }
      c.alignment = { vertical: 'middle', wrapText: true }
    })
    for (const b of t.baris) ws.addRow(b.map((v) => (v === undefined ? null : v)))
    ws.views = [{ state: 'frozen', ySplit: 1 }]
    if (t.baris.length > 0) ws.autoFilter = { from: { row: 1, column: 1 }, to: { row: 1, column: t.kolom.length } }
    t.kolom.forEach((k, i) => {
      let maks = k.length
      for (const b of t.baris) maks = Math.max(maks, teks(b[i]).length)
      ws.getColumn(i + 1).width = Math.min(Math.max(maks + 2, 8), 60)
      if (t.kananIdx?.includes(i)) ws.getColumn(i + 1).alignment = { horizontal: 'right' }
    })
  }

  const adaFoto = (dok.bagian ?? []).some((b) => b.gambar.length > 0)
  if (adaFoto) {
    const ws = wb.addWorksheet(namaLembar('Foto', dipakai))
    const kepala = ws.addRow(['Inspeksi', 'Keterangan foto', 'Foto'])
    kepala.font = { bold: true, color: { argb: 'FFFFFFFF' } }
    kepala.eachCell((c) => {
      c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF15803D' } }
    })
    ws.getColumn(1).width = 44
    ws.getColumn(2).width = 40
    ws.getColumn(3).width = 52
    let baris = 2
    for (const b of dok.bagian ?? []) {
      for (const g of b.gambar) {
        const lebar = 360
        const tinggi = Math.round((lebar * g.tinggi) / Math.max(1, g.lebar))
        const r = ws.addRow([b.judul, g.keterangan || 'Tanpa keterangan'])
        r.height = tinggi * 0.75 + 6 // tinggi baris dalam poin
        r.alignment = { vertical: 'top', wrapText: true }
        const id = wb.addImage({ base64: g.dataUrl, extension: 'jpeg' })
        ws.addImage(id, { tl: { col: 2, row: baris - 1 }, ext: { width: lebar, height: tinggi } })
        baris++
      }
    }
  }

  const data = await wb.xlsx.writeBuffer()
  unduh(new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), nama)
}

/** PDF: judul, keterangan, tabel, lalu bagian rinci per inspeksi dengan foto. Kertas A4; mendatar bila kolomnya banyak. */
export async function eksporPdf(dok: DokumenEkspor, nama = namaBerkas(dok.judul, 'pdf')): Promise<void> {
  const { jsPDF } = await import('jspdf')
  const autoTable = (await import('jspdf-autotable')).default
  const tabelPdf = dok.tabel.filter((t) => t.hanya !== 'excel')
  const lebarKolom = Math.max(0, ...tabelPdf.map((t) => t.kolom.length), ...(dok.bagian ?? []).flatMap((b) => b.tabel.map((t) => t.kolom.length)))
  const doc = new jsPDF({ orientation: lebarKolom > 8 ? 'landscape' : 'portrait', unit: 'pt', format: 'a4' })
  const halamanLebar = doc.internal.pageSize.getWidth()
  const halamanTinggi = doc.internal.pageSize.getHeight()
  const kiri = 40
  const lebarIsi = halamanLebar - 2 * kiri
  const batasBawah = halamanTinggi - 50
  let y = 40

  const pastikanRuang = (butuh: number) => {
    if (y + butuh > batasBawah) {
      doc.addPage()
      y = 40
    }
  }
  const akhirTabel = () => (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? y

  function ketKeterangan(ket: Keterangan, ukuran = 9) {
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(ukuran)
    doc.setTextColor(70)
    for (const [k, v] of ket) {
      const baris = doc.splitTextToSize(`${k}: ${v}`, lebarIsi) as string[]
      for (const b of baris) {
        pastikanRuang(ukuran + 4)
        doc.text(b, kiri, y)
        y += ukuran + 3
      }
    }
    doc.setTextColor(0)
  }

  function gambarTabel(t: TabelEkspor) {
    pastikanRuang(60)
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(11)
    doc.text(t.judul, kiri, y)
    if (t.baris.length === 0) {
      doc.setFont('helvetica', 'italic')
      doc.setFontSize(9)
      doc.setTextColor(110)
      doc.text('Tidak ada data.', kiri, y + 14)
      doc.setTextColor(0)
      y += 34
      return
    }
    const kanan = new Set(t.kananIdx ?? [])
    autoTable(doc, {
      startY: y + 6,
      head: [t.kolom],
      body: t.baris.map((b) => b.map(teks)),
      styles: { fontSize: 8, cellPadding: 3 },
      headStyles: { fillColor: [21, 128, 61] },
      margin: { left: kiri, right: kiri },
      columnStyles: Object.fromEntries([...kanan].map((i) => [i, { halign: 'right' }])),
      // Judul kolom angka ikut rata kanan supaya sejajar dengan isinya.
      didParseCell: (h) => {
        if (h.section === 'head' && kanan.has(h.column.index)) h.cell.styles.halign = 'right'
      },
    })
    y = akhirTabel() + 6
    if (t.catatan) {
      doc.setFont('helvetica', 'italic')
      doc.setFontSize(8)
      doc.setTextColor(90)
      const baris = doc.splitTextToSize(t.catatan, lebarIsi) as string[]
      for (const b of baris) {
        pastikanRuang(12)
        doc.text(b, kiri, y + 6)
        y += 10
      }
      doc.setTextColor(0)
    }
    y += 16
  }

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(15)
  doc.text(dok.judul, kiri, y)
  y += 18
  ketKeterangan(dok.keterangan ?? [])
  y += 8

  for (const t of tabelPdf) gambarTabel(t)

  for (const b of dok.bagian ?? []) {
    // Tiap inspeksi mulai di halaman baru supaya mudah dipisah dan dibaca.
    doc.addPage()
    y = 40
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(13)
    const judul = doc.splitTextToSize(b.judul, lebarIsi) as string[]
    for (const j of judul) {
      doc.text(j, kiri, y)
      y += 16
    }
    y += 2
    ketKeterangan(b.keterangan)
    y += 8
    for (const t of b.tabel.filter((x) => x.hanya !== 'excel')) gambarTabel(t)

    if (b.gambar.length > 0) {
      pastikanRuang(40)
      doc.setFont('helvetica', 'bold')
      doc.setFontSize(11)
      doc.text(`Foto bukti (${b.gambar.length})`, kiri, y)
      y += 12
      const jarak = 12
      const lebarFoto = (lebarIsi - jarak) / 2
      let kolom = 0
      let barisTinggi = 0
      for (const g of b.gambar) {
        const tinggiFoto = Math.min(lebarFoto * (g.tinggi / Math.max(1, g.lebar)), 260)
        const lebarGambar = tinggiFoto * (g.lebar / Math.max(1, g.tinggi))
        const butuh = tinggiFoto + 30
        if (kolom === 0) pastikanRuang(butuh)
        const x = kiri + kolom * (lebarFoto + jarak)
        try {
          doc.addImage(g.dataUrl, 'JPEG', x, y, Math.min(lebarGambar, lebarFoto), tinggiFoto)
        } catch {
          doc.setFontSize(8)
          doc.text('(foto tidak bisa dimuat)', x, y + 10)
        }
        doc.setFont('helvetica', 'normal')
        doc.setFontSize(8)
        const cap = doc.splitTextToSize(g.keterangan || 'Tanpa keterangan', lebarFoto) as string[]
        doc.text(cap.slice(0, 3), x, y + tinggiFoto + 10)
        barisTinggi = Math.max(barisTinggi, butuh)
        if (kolom === 1) {
          y += barisTinggi
          kolom = 0
          barisTinggi = 0
        } else {
          kolom = 1
        }
      }
      if (kolom === 1) y += barisTinggi
    }
  }

  const jumlah = doc.getNumberOfPages()
  doc.setFontSize(8)
  doc.setTextColor(120)
  for (let i = 1; i <= jumlah; i++) {
    doc.setPage(i)
    doc.text(`fAI26 • dicetak ${new Date().toLocaleString('id-ID')} • halaman ${i}/${jumlah}`, kiri, halamanTinggi - 20)
  }
  unduh(doc.output('blob'), nama)
}
