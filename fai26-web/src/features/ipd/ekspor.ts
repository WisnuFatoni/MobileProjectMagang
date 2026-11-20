import type { IpdBaris, IpdDetail, IpdGrupLoses, IpdKodeAgg, IpdLaporan } from '@/api/types'
import type { BagianEkspor, DokumenEkspor, GambarEkspor, Keterangan, TabelEkspor } from '@/lib/ekspor'
import { tanggal, waktu } from '@/lib/format'
import { STATUS_IPD, temuanTeratas } from './ipdTampil'

/** Persen dibulatkan satu angka desimal tetapi tetap angka, supaya bisa dijumlah atau diurutkan di Excel. */
export const bulatPersen = (v: number): number => Math.round(v * 10) / 10

const JENIS: Record<string, string> = { LOSES: 'Loses', TEMUAN: 'Temuan lain' }
const KELOMPOK: Record<string, string> = { BUAH: 'Buah', BRONDOLAN: 'Brondolan', PELEPAH: 'Pelepah' }

/** Penjelasan rumus yang dicetak di tiap laporan, supaya pembaca (askep, KTU, atasan) tidak perlu bertanya. */
export const CARA_HITUNG =
  'Loses = kehilangan hasil panen: temuan buah dan brondolan (kode A, G, S, M1-M4, B1, B2). Persen loses = jumlah POKOK yang memuat paling sedikit ' +
  'satu temuan loses dibagi jumlah pokok yang diperiksa; satu pokok dihitung sekali walau temuannya banyak. "Jumlah" = total butir/buah dari semua temuan. ' +
  'Temuan lain = kerusakan pelepah (kode C, R) yang tidak memengaruhi hasil panen; dihitung terpisah dan tidak masuk persen loses.'

function tabelGrup(judul: string, namaKolom: string, data: IpdGrupLoses[]): TabelEkspor {
  return {
    judul,
    kolom: [namaKolom, 'Inspeksi', 'Pokok diperiksa', 'Pokok loses', '% loses', 'Pokok temuan lain', '% temuan lain', 'Temuan terbanyak'],
    baris: data.map((g) => [
      g.nama,
      g.inspeksi,
      g.pokok_diperiksa,
      g.pokok_loses,
      bulatPersen(g.persen_pokok_loses),
      g.pokok_temuan_lain,
      bulatPersen(g.persen_pokok_temuan_lain),
      temuanTeratas(g),
    ]),
    kananIdx: [1, 2, 3, 4, 5, 6],
  }
}

/** Arti tiap kode temuan beserta jenisnya, supaya laporan bisa dibaca tanpa tahu singkatannya. */
export function tabelArtiKode(perKode: IpdKodeAgg[]): TabelEkspor {
  return {
    judul: 'Arti kode temuan',
    kolom: ['Kode', 'Temuan', 'Kelompok', 'Jenis'],
    baris: perKode.map((k) => [k.kode, k.nama, KELOMPOK[k.kelompok] ?? k.kelompok, JENIS[k.jenis] ?? k.jenis]),
  }
}

function tabelPerKode(perKode: IpdKodeAgg[], hanyaAda = false): TabelEkspor {
  const data = hanyaAda ? perKode.filter((k) => k.jumlah > 0) : perKode
  return {
    judul: 'Per kode',
    kolom: ['Kode', 'Temuan', 'Jenis', 'Pokok kena', 'Jumlah', '% pokok'],
    baris: data.map((k) => [k.kode, k.nama, JENIS[k.jenis] ?? k.jenis, k.pokok, k.jumlah, bulatPersen(k.persen_pokok)]),
    kananIdx: [3, 4, 5],
  }
}

/** Laporan loses (hanya inspeksi yang sudah disetujui): ringkasan, per kode, lalu per pemanen, blok, divisi, dan tanggal. */
export function dokumenLaporan(r: IpdLaporan, divisiNama?: string): DokumenEkspor {
  const s = r.ringkasan
  return {
    judul: 'Laporan Loses IPD',
    keterangan: [
      ['Periode', `${tanggal(r.dari)} sampai ${tanggal(r.sampai)}`],
      ['Divisi', divisiNama ?? 'Semua divisi'],
      ['Inspeksi disetujui', String(r.inspeksi)],
      ['Pemanen diperiksa', String(r.pemanen)],
      ['Pokok diperiksa', String(s.pokok_diperiksa)],
      ['Loses (pokok kehilangan hasil)', `${bulatPersen(s.persen_pokok_loses)}% (${s.pokok_loses} pokok, jumlah ${s.total_loses})`],
      ['Temuan lain (pelepah)', `${bulatPersen(s.persen_pokok_temuan_lain)}% (${s.pokok_temuan_lain} pokok, jumlah ${s.total_temuan_lain})`],
      ['Cara menghitung', CARA_HITUNG],
    ],
    tabel: [
      tabelArtiKode(s.per_kode),
      tabelPerKode(s.per_kode),
      tabelGrup('Per pemanen', 'Pemanen', r.per_pemanen),
      tabelGrup('Per blok', 'Blok', r.per_blok),
      tabelGrup('Per divisi', 'Divisi', r.per_divisi),
      tabelGrup('Per tanggal', 'Tanggal', r.per_tanggal),
    ],
  }
}

// ---- Rincian per inspeksi, pokok, dan foto ------------------------------------------------------------

interface InfoKode {
  nama: string
  jenis: string
}

function petaKode(d: Pick<IpdDetail, 'ringkasan'>): Map<string, InfoKode> {
  return new Map(d.ringkasan.per_kode.map((k) => [k.kode, { nama: k.nama, jenis: k.jenis }]))
}

/** "A Potong buah mentah x1; B1 Brondolan tak dikutip x6" untuk satu pokok. */
export function teksTemuanPokok(temuan: { kode: string; jumlah: number }[], peta: Map<string, InfoKode>): string {
  return temuan.map((t) => `${t.kode} ${peta.get(t.kode)?.nama ?? ''} x${t.jumlah}`.replace('  ', ' ')).join('; ')
}

/** Dampak satu pokok: Loses, Temuan lain, atau keduanya. Pokok tanpa temuan: Bersih. */
export function dampakPokok(temuan: { kode: string }[], peta: Map<string, InfoKode>): string {
  if (temuan.length === 0) return 'Bersih'
  const loses = temuan.some((t) => (peta.get(t.kode)?.jenis ?? 'LOSES') === 'LOSES')
  const lain = temuan.some((t) => peta.get(t.kode)?.jenis === 'TEMUAN')
  if (loses && lain) return 'Loses + temuan lain'
  return loses ? 'Loses' : 'Temuan lain'
}

function ketInspeksi(d: IpdDetail): Keterangan {
  const s = d.ringkasan
  return [
    ['Pemanen', d.pemanen_nama ?? String(d.pemanen_hrmb_code)],
    ['Blok dan divisi', `Blok ${d.blok_kode ?? '-'} • ${d.divisi_kode}${d.ancak_no ? ` • ancak ${d.ancak_no}` : ''}`],
    ['Tanggal inspeksi', tanggal(d.tanggal_inspeksi)],
    ['Diinspeksi oleh', d.inspektur_nama],
    ['Kemandoran', d.mandor_nama ?? '-'],
    ['Asisten penyetuju', d.asisten_nama ?? 'Belum diatur'],
    ['Status', STATUS_IPD[d.status].teks + (d.disetujui_pada ? ` (${waktu(d.disetujui_pada)})` : '')],
    ['Pokok diperiksa', String(s.pokok_diperiksa)],
    ['Loses', `${bulatPersen(s.persen_pokok_loses)}% • ${s.pokok_loses} pokok kena, jumlah ${s.total_loses}`],
    ['Temuan lain (pelepah)', `${bulatPersen(s.persen_pokok_temuan_lain)}% • ${s.pokok_temuan_lain} pokok, jumlah ${s.total_temuan_lain}`],
    ...(d.pesan_tolak ? ([['Alasan ditolak', d.pesan_tolak]] as Keterangan) : []),
    ...(d.catatan ? ([['Catatan', d.catatan]] as Keterangan) : []),
  ]
}

/** Pokok yang kena temuan, dengan nama temuan dan dampaknya; pokok bersih diringkas di catatan. */
function tabelPokokKena(d: IpdDetail): TabelEkspor {
  const peta = petaKode(d)
  const kena = d.pokok.filter((p) => p.temuan.length > 0)
  return {
    judul: 'Pokok yang kena temuan',
    kolom: ['Baris', 'Pokok', 'Temuan (kode, nama, jumlah)', 'Dampak'],
    baris: kena.map((p) => [p.no_baris, p.no_pokok, teksTemuanPokok(p.temuan, peta), dampakPokok(p.temuan, peta)]),
    kananIdx: [0, 1],
    catatan: `Dari ${d.pokok.length} pokok diperiksa: ${kena.length} kena temuan, ${d.pokok.length - kena.length} bersih.`,
  }
}

/** Satu inspeksi lengkap sebagai bagian dokumen: keterangan, ringkasan per kode, pokok yang kena temuan, dan foto. */
export function bagianInspeksi(d: IpdDetail, gambar: GambarEkspor[]): BagianEkspor {
  return {
    judul: `${tanggal(d.tanggal_inspeksi)} • ${d.pemanen_nama ?? d.pemanen_hrmb_code} • Blok ${d.blok_kode ?? '-'} • ${d.divisi_kode}`,
    keterangan: ketInspeksi(d),
    tabel: [tabelPerKode(d.ringkasan.per_kode, true), tabelPokokKena(d)],
    gambar,
  }
}

/** Satu baris per inspeksi (untuk Excel). */
function tabelInspeksiExcel(daftar: IpdDetail[]): TabelEkspor {
  return {
    judul: 'Inspeksi',
    hanya: 'excel',
    kolom: ['Tanggal', 'Pemanen', 'Blok', 'Divisi', 'Ancak', 'Kemandoran', 'Diinspeksi', 'Asisten', 'Status', 'Pokok diperiksa', 'Pokok loses', '% loses', 'Jumlah loses', 'Pokok temuan lain', '% temuan lain', 'Jumlah temuan lain', 'Foto', 'Catatan'],
    baris: daftar.map((d) => [
      tanggal(d.tanggal_inspeksi),
      d.pemanen_nama ?? String(d.pemanen_hrmb_code),
      d.blok_kode ?? '-',
      d.divisi_kode,
      d.ancak_no ?? '',
      d.mandor_nama ?? '-',
      d.inspektur_nama,
      d.asisten_nama ?? '-',
      STATUS_IPD[d.status].teks,
      d.ringkasan.pokok_diperiksa,
      d.ringkasan.pokok_loses,
      bulatPersen(d.ringkasan.persen_pokok_loses),
      d.ringkasan.total_loses,
      d.ringkasan.pokok_temuan_lain,
      bulatPersen(d.ringkasan.persen_pokok_temuan_lain),
      d.ringkasan.total_temuan_lain,
      d.foto.length,
      d.catatan ?? '',
    ]),
    kananIdx: [9, 10, 11, 12, 13, 14, 15, 16],
  }
}

/** Satu baris per pokok dan temuan (untuk Excel, bisa disaring): pokok bersih ditulis satu baris "Bersih". */
function tabelPokokExcel(daftar: IpdDetail[]): TabelEkspor {
  const baris: (string | number)[][] = []
  for (const d of daftar) {
    const peta = petaKode(d)
    const pemanen = d.pemanen_nama ?? String(d.pemanen_hrmb_code)
    for (const p of d.pokok) {
      if (p.temuan.length === 0) {
        baris.push([tanggal(d.tanggal_inspeksi), pemanen, d.blok_kode ?? '-', d.divisi_kode, p.no_baris, p.no_pokok, '-', 'Bersih', '-', 0, ''])
        continue
      }
      for (const t of p.temuan) {
        const info = peta.get(t.kode)
        baris.push([tanggal(d.tanggal_inspeksi), pemanen, d.blok_kode ?? '-', d.divisi_kode, p.no_baris, p.no_pokok, t.kode, info?.nama ?? '', JENIS[info?.jenis ?? 'LOSES'] ?? '', t.jumlah, t.catatan ?? ''])
      }
    }
  }
  return {
    judul: 'Rincian pokok',
    hanya: 'excel',
    kolom: ['Tanggal', 'Pemanen', 'Blok', 'Divisi', 'Baris', 'Pokok', 'Kode', 'Temuan', 'Jenis', 'Jumlah', 'Catatan'],
    baris,
    kananIdx: [4, 5, 9],
  }
}

/** Laporan loses rinci: laporan ringkas + semua inspeksi disetujui dengan pokok, temuan, dan (bila ada) foto. */
export function dokumenLaporanRinci(lap: IpdLaporan, daftar: IpdDetail[], fotoPer: Map<string, GambarEkspor[]>, divisiNama?: string): DokumenEkspor {
  const dasar = dokumenLaporan(lap, divisiNama)
  return {
    ...dasar,
    judul: 'Laporan Loses IPD Rinci',
    tabel: [...dasar.tabel, tabelInspeksiExcel(daftar), tabelPokokExcel(daftar)],
    bagian: daftar.map((d) => bagianInspeksi(d, fotoPer.get(d.id) ?? [])),
  }
}

/** Daftar inspeksi (menurut filter) ringkas: satu baris per inspeksi. */
export function dokumenDaftar(item: IpdBaris[], filterTeks: string): DokumenEkspor {
  return {
    judul: 'Daftar Inspeksi IPD',
    keterangan: [
      ['Filter', filterTeks],
      ['Jumlah inspeksi', String(item.length)],
      ['Cara menghitung', CARA_HITUNG],
    ],
    tabel: [
      {
        judul: 'Inspeksi',
        kolom: ['Tanggal', 'Pemanen', 'Blok', 'Divisi', 'Kemandoran', 'Diinspeksi', 'Asisten', 'Status', 'Pokok', 'Pokok loses', '% loses', 'Temuan lain', '% temuan lain'],
        baris: item.map((r) => [
          tanggal(r.tanggal_inspeksi),
          r.pemanen_nama ?? String(r.pemanen_hrmb_code),
          r.blok_kode ?? '-',
          r.divisi_kode,
          r.mandor_nama ?? '-',
          r.inspektur_nama,
          r.asisten_nama ?? '-',
          STATUS_IPD[r.status].teks,
          r.ringkasan.pokok_diperiksa,
          r.ringkasan.pokok_loses,
          bulatPersen(r.ringkasan.persen_pokok_loses),
          r.ringkasan.pokok_temuan_lain,
          bulatPersen(r.ringkasan.persen_pokok_temuan_lain),
        ]),
        kananIdx: [8, 9, 10, 11, 12],
      },
    ],
  }
}

/** Daftar inspeksi rinci: ringkas + pokok dan temuan tiap inspeksi (+ foto). */
export function dokumenDaftarRinci(daftar: IpdDetail[], fotoPer: Map<string, GambarEkspor[]>, filterTeks: string): DokumenEkspor {
  const dasar = dokumenDaftar(daftar, filterTeks)
  return {
    ...dasar,
    judul: 'Daftar Inspeksi IPD Rinci',
    tabel: [...dasar.tabel, tabelPokokExcel(daftar)],
    bagian: daftar.map((d) => bagianInspeksi(d, fotoPer.get(d.id) ?? [])),
  }
}

/** Satu inspeksi lengkap: kepala, ringkasan per kode, pokok yang kena temuan (PDF) atau semua pokok (Excel), dan foto. */
export function dokumenDetail(d: IpdDetail, gambar: GambarEkspor[] = []): DokumenEkspor {
  const b = bagianInspeksi(d, gambar)
  return {
    judul: `Inspeksi IPD ${d.pemanen_nama ?? d.pemanen_hrmb_code}`,
    keterangan: [...b.keterangan, ['Cara menghitung', CARA_HITUNG]],
    tabel: [tabelArtiKode(d.ringkasan.per_kode), ...b.tabel, tabelPokokExcel([d])],
    bagian: gambar.length > 0 ? [{ judul: 'Foto bukti', keterangan: [], tabel: [], gambar }] : undefined,
  }
}
