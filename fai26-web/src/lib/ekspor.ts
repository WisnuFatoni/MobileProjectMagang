/**
 * Ekspor laporan ke Excel (.xlsx) dan PDF di browser. Pustaka berat (exceljs, jspdf) dimuat hanya saat tombol ditekan.
 *
 * Satu laporan = SATU lembar (Excel) atau satu dokumen berurutan (PDF), dikelompokkan per tanggal. Di bawah tiap tanggal ada
 * blok per inspeksi (ancak/pemanen): kepala keterangan, hasil, tabel temuan, dan (opsional) foto bukti. Bagian atas memuat
 * keterangan laporan, rumus, dan ringkasan per kode.
 */

export type Sel = string | number | null | undefined

/** Pasangan label dan nilai di bagian atas dokumen (periode, filter, ringkasan). */
export type Keterangan = [string, string][]

/** Foto yang sudah dikecilkan: JPEG data URL dengan ukuran aslinya (piksel). */
export interface GambarEkspor {
  keterangan: string
  dataUrl: string
  lebar: number
  tinggi: number
}

export interface TabelKecil {
  judul: string
  kolom: string[]
  baris: Sel[][]
  /** Kolom yang rata kanan (angka), nomor mulai 0. */
  kananIdx?: number[]
}

/** Satu inspeksi (satu ancak/pemanen) dengan temuannya. */
export interface BlokInspeksi {
  /** Mis. "1. ALFARIZI • Blok 10C • Ancak 7 • Divisi 1". */
  judul: string
  /** Baris keterangan di bawah judul (kemandoran, inspektur, asisten, status, catatan). */
  meta: string[]
  /** Hasil satu baris: pokok diperiksa, loses, temuan lain. */
  hasil: string
  kolom: string[]
  baris: Sel[][]
  kananIdx?: number[]
  /** Di bawah tabel, mis. "Pokok bersih: 7 dari 10." */
  penutup: string
  gambar: GambarEkspor[]
}

export interface HariEkspor {
  /** Mis. "Jumat, 20 November 2026". */
  judul: string
  /** Ringkasan hari itu. */
  ringkas: string
  inspeksi: BlokInspeksi[]
}

export interface LaporanEkspor {
  judul: string
  keterangan: Keterangan
  /** Tabel kecil di atas (arti kode dan ringkasan per kode). */
  ringkasKode?: TabelKecil
  hari: HariEkspor[]
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

const teks = (v: Sel): string => (v === null || v === undefined ? '' : String(v))

/** Perkiraan jumlah baris teks (untuk tinggi baris sel gabungan yang membungkus teks). */
export function perkiraanBaris(isi: string, karakterPerBaris: number): number {
  return Math.max(1, Math.ceil(isi.length / Math.max(1, karakterPerBaris)))
}

const HIJAU_TUA = 'FF15803D'
const HIJAU_MUDA = 'FFDCFCE7'
const ABU = 'FFF3F4F6'

/**
 * Excel: SATU lembar "Laporan". Atas: judul, keterangan, rumus, ringkasan per kode. Lalu per tanggal (pita hijau tua), dan di
 * bawahnya tiap inspeksi (pita hijau muda, keterangan, hasil, tabel temuan, foto).
 */
export async function eksporExcel(lap: LaporanEkspor, nama = namaBerkas(lap.judul, 'xlsx')): Promise<void> {
  const ExcelJS = (await import('exceljs')).default
  const wb = new ExcelJS.Workbook()
  wb.creator = 'fAI26'
  wb.created = new Date()
  const ws = wb.addWorksheet('Laporan', { views: [{ showGridLines: false }] })

  // Kolom: Baris, Pokok, Kode, Temuan, Dampak, Jumlah, Catatan.
  const lebar = [9, 9, 9, 46, 20, 10, 34]
  const N = lebar.length
  lebar.forEach((w, i) => (ws.getColumn(i + 1).width = w))
  const totalLebar = lebar.reduce((a, b) => a + b, 0)
  let r = 1

  function gabung(isi: string, o: { tebal?: boolean; miring?: boolean; ukuran?: number; warna?: string; latar?: string; tinggiMin?: number } = {}) {
    ws.mergeCells(r, 1, r, N)
    const sel = ws.getCell(r, 1)
    sel.value = isi
    sel.font = { bold: o.tebal, italic: o.miring, size: o.ukuran ?? 11, color: o.warna ? { argb: o.warna } : undefined }
    sel.alignment = { wrapText: true, vertical: 'middle' }
    if (o.latar) sel.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: o.latar } }
    const baris = perkiraanBaris(isi, Math.round(totalLebar * (11 / (o.ukuran ?? 11)) * 0.95))
    ws.getRow(r).height = Math.max(o.tinggiMin ?? 0, baris * ((o.ukuran ?? 11) + 5))
    r++
  }
  const kosong = () => void r++

  gabung(lap.judul, { tebal: true, ukuran: 16 })
  for (const [k, v] of lap.keterangan) {
    const teksBaris = `${k}: ${v}`
    gabung(teksBaris, { ukuran: 10 })
  }
  kosong()

  if (lap.ringkasKode) {
    const t = lap.ringkasKode
    gabung(t.judul, { tebal: true, ukuran: 12 })
    const kepala = ws.getRow(r)
    // Tabel kecil memakai kolom Baris..Catatan apa adanya (kolom ke-1 sampai ke-n).
    t.kolom.forEach((k, i) => {
      const c = kepala.getCell(i + 1)
      c.value = k
      c.font = { bold: true, color: { argb: 'FFFFFFFF' } }
      c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: HIJAU_TUA } }
    })
    r++
    for (const b of t.baris) {
      const baris = ws.getRow(r)
      b.forEach((v, i) => {
        const c = baris.getCell(i + 1)
        c.value = v === undefined ? null : v
        if (t.kananIdx?.includes(i)) c.alignment = { horizontal: 'right' }
      })
      r++
    }
    kosong()
  }

  for (const hari of lap.hari) {
    gabung(hari.judul, { tebal: true, ukuran: 13, warna: 'FFFFFFFF', latar: HIJAU_TUA, tinggiMin: 24 })
    gabung(hari.ringkas, { ukuran: 10, latar: ABU })
    kosong()
    for (const b of hari.inspeksi) {
      gabung(b.judul, { tebal: true, ukuran: 12, latar: HIJAU_MUDA, tinggiMin: 22 })
      for (const m of b.meta) gabung(m, { ukuran: 10 })
      gabung(b.hasil, { tebal: true, ukuran: 10 })
      if (b.baris.length > 0) {
        const kepala = ws.getRow(r)
        b.kolom.forEach((k, i) => {
          const c = kepala.getCell(i + 1)
          c.value = k
          c.font = { bold: true, color: { argb: 'FFFFFFFF' } }
          c.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF166534' } }
          c.alignment = { horizontal: b.kananIdx?.includes(i) ? 'right' : 'left' }
        })
        r++
        for (const baris of b.baris) {
          const row = ws.getRow(r)
          baris.forEach((v, i) => {
            const c = row.getCell(i + 1)
            c.value = v === undefined ? null : v
            c.alignment = { horizontal: b.kananIdx?.includes(i) ? 'right' : 'left', vertical: 'top', wrapText: true }
            c.border = { bottom: { style: 'hair', color: { argb: 'FFD1D5DB' } } }
          })
          r++
        }
      }
      gabung(b.penutup, { miring: true, ukuran: 10 })
      for (const g of b.gambar) {
        gabung(`Foto: ${g.keterangan || 'tanpa keterangan'}`, { ukuran: 9 })
        const lebarPx = 300
        const tinggiPx = Math.round((lebarPx * g.tinggi) / Math.max(1, g.lebar))
        ws.getRow(r).height = tinggiPx * 0.75 + 4
        const id = wb.addImage({ base64: g.dataUrl, extension: 'jpeg' })
        ws.addImage(id, { tl: { col: 0.1, row: r - 1 }, ext: { width: lebarPx, height: tinggiPx } })
        r++
      }
      kosong()
    }
    kosong()
  }

  const data = await wb.xlsx.writeBuffer()
  unduh(new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), nama)
}

/** PDF: judul, keterangan, ringkasan per kode, lalu per tanggal dan per inspeksi berurutan (tanpa memaksa halaman baru). */
export async function eksporPdf(lap: LaporanEkspor, nama = namaBerkas(lap.judul, 'pdf')): Promise<void> {
  const { jsPDF } = await import('jspdf')
  const autoTable = (await import('jspdf-autotable')).default
  const doc = new jsPDF({ orientation: 'portrait', unit: 'pt', format: 'a4' })
  const halamanLebar = doc.internal.pageSize.getWidth()
  const halamanTinggi = doc.internal.pageSize.getHeight()
  const kiri = 36
  const lebarIsi = halamanLebar - 2 * kiri
  const batasBawah = halamanTinggi - 44
  let y = 40

  const pastikanRuang = (butuh: number) => {
    if (y + butuh > batasBawah) {
      doc.addPage()
      y = 40
    }
  }
  const akhirTabel = () => (doc as unknown as { lastAutoTable?: { finalY: number } }).lastAutoTable?.finalY ?? y

  function paragraf(isi: string, ukuran: number, gaya: 'normal' | 'bold' | 'italic', warna = 40) {
    doc.setFont('helvetica', gaya)
    doc.setFontSize(ukuran)
    doc.setTextColor(warna)
    for (const b of doc.splitTextToSize(isi, lebarIsi) as string[]) {
      pastikanRuang(ukuran + 4)
      doc.text(b, kiri, y)
      y += ukuran + 3
    }
    doc.setTextColor(0)
  }

  function tabel(judul: string | null, kolom: string[], baris: Sel[][], kananIdx: number[] | undefined, kepalaWarna: [number, number, number]) {
    if (judul) {
      pastikanRuang(50)
      doc.setFont('helvetica', 'bold')
      doc.setFontSize(10)
      doc.text(judul, kiri, y)
      y += 4
    }
    const kanan = new Set(kananIdx ?? [])
    autoTable(doc, {
      startY: y + 2,
      head: [kolom],
      body: baris.map((b) => b.map(teks)),
      styles: { fontSize: 8, cellPadding: 2.5 },
      headStyles: { fillColor: kepalaWarna },
      margin: { left: kiri, right: kiri },
      columnStyles: Object.fromEntries([...kanan].map((i) => [i, { halign: 'right' }])),
      didParseCell: (h) => {
        if (h.section === 'head' && kanan.has(h.column.index)) h.cell.styles.halign = 'right'
      },
    })
    y = akhirTabel() + 6
  }

  function pita(isi: string, ukuran: number, warnaIsi: [number, number, number], warnaTeks: number) {
    const baris = doc.splitTextToSize(isi, lebarIsi - 12) as string[]
    const tinggi = baris.length * (ukuran + 3) + 8
    pastikanRuang(tinggi + 40)
    doc.setFillColor(...warnaIsi)
    doc.rect(kiri, y - ukuran, lebarIsi, tinggi, 'F')
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(ukuran)
    doc.setTextColor(warnaTeks)
    baris.forEach((b, i) => doc.text(b, kiri + 6, y + 2 + i * (ukuran + 3)))
    doc.setTextColor(0)
    y += tinggi - ukuran + 8
  }

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(16)
  doc.text(lap.judul, kiri, y)
  y += 20
  for (const [k, v] of lap.keterangan) paragraf(`${k}: ${v}`, 9, 'normal', 70)
  y += 6
  if (lap.ringkasKode) {
    tabel(lap.ringkasKode.judul, lap.ringkasKode.kolom, lap.ringkasKode.baris, lap.ringkasKode.kananIdx, [21, 128, 61])
    y += 6
  }

  for (const hari of lap.hari) {
    pita(hari.judul, 12, [21, 128, 61], 255)
    paragraf(hari.ringkas, 9, 'normal', 70)
    y += 4
    for (const b of hari.inspeksi) {
      pita(b.judul, 10, [220, 252, 231], 20)
      for (const m of b.meta) paragraf(m, 9, 'normal', 60)
      paragraf(b.hasil, 9, 'bold')
      y += 2
      if (b.baris.length > 0) tabel(null, b.kolom, b.baris, b.kananIdx, [22, 101, 52])
      paragraf(b.penutup, 8.5, 'italic', 90)
      if (b.gambar.length > 0) {
        y += 4
        const jarak = 10
        const lebarFoto = (lebarIsi - 2 * jarak) / 3
        let kolom = 0
        let tinggiBaris = 0
        for (const g of b.gambar) {
          const tinggiFoto = Math.min(lebarFoto * (g.tinggi / Math.max(1, g.lebar)), 170)
          const lebarGambar = Math.min(tinggiFoto * (g.lebar / Math.max(1, g.tinggi)), lebarFoto)
          const butuh = tinggiFoto + 26
          if (kolom === 0) pastikanRuang(butuh)
          const x = kiri + kolom * (lebarFoto + jarak)
          try {
            doc.addImage(g.dataUrl, 'JPEG', x, y, lebarGambar, tinggiFoto)
          } catch {
            doc.setFontSize(8)
            doc.text('(foto tidak bisa dimuat)', x, y + 10)
          }
          doc.setFont('helvetica', 'normal')
          doc.setFontSize(7.5)
          doc.setTextColor(70)
          doc.text((doc.splitTextToSize(g.keterangan || 'Tanpa keterangan', lebarFoto) as string[]).slice(0, 2), x, y + tinggiFoto + 9)
          doc.setTextColor(0)
          tinggiBaris = Math.max(tinggiBaris, butuh)
          if (++kolom === 3) {
            y += tinggiBaris
            kolom = 0
            tinggiBaris = 0
          }
        }
        if (kolom > 0) y += tinggiBaris
      }
      y += 10
    }
    y += 6
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
