import dayjs from 'dayjs'
import 'dayjs/locale/id'
import type { IpdDetail, IpdKodeAgg, IpdRingkasan } from '@/api/types'
import type { BlokInspeksi, GambarEkspor, HariEkspor, Keterangan, LaporanEkspor, TabelKecil } from '@/lib/ekspor'
import { STATUS_IPD } from './ipdTampil'
import { teksDurasi, teksJarak } from './jalur'

/** Persen dibulatkan satu angka desimal tetapi tetap angka. */
export const bulatPersen = (v: number): number => Math.round(v * 10) / 10

const JENIS: Record<string, string> = { LOSES: 'Loses', TEMUAN: 'Temuan lain' }

/** Penjelasan rumus yang dicetak di tiap laporan, supaya pembaca (askep, KTU, atasan) tidak perlu bertanya. */
export const CARA_HITUNG =
  'Loses = kehilangan hasil panen: temuan buah dan brondolan (kode A, G, S, M1-M4, B1, B2). Persen loses = jumlah POKOK yang memuat paling sedikit ' +
  'satu temuan loses dibagi jumlah pokok yang diperiksa; satu pokok dihitung sekali walau temuannya banyak. "Jumlah" = total butir/buah dari semua temuan. ' +
  'Temuan lain = kerusakan pelepah (kode C, R) yang tidak memengaruhi hasil panen; dihitung terpisah dan tidak masuk persen loses.'

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

const KOLOM_TEMUAN = ['Baris', 'Pokok', 'Kode', 'Temuan', 'Dampak', 'Jumlah', 'Catatan']

/** Satu inspeksi sebagai blok: ancak/pemanen, siapa yang menginspeksi, hasilnya, dan SEMUA temuan per pokok. */
export function blokInspeksi(d: IpdDetail, nomor: number, gambar: GambarEkspor[]): BlokInspeksi {
  const peta = petaKode(d)
  const s = d.ringkasan
  const baris: (string | number)[][] = []
  let kena = 0
  for (const p of d.pokok) {
    if (p.temuan.length === 0) continue
    kena++
    for (const t of p.temuan) {
      const info = peta.get(t.kode)
      baris.push([p.no_baris, p.no_pokok, t.kode, info?.nama ?? '', JENIS[info?.jenis ?? 'LOSES'] ?? '', t.jumlah, t.catatan ?? ''])
    }
  }
  const meta = [
    `Kemandoran: ${d.mandor_nama ?? '-'}  •  Diinspeksi oleh: ${d.inspektur_nama}  •  Asisten: ${d.asisten_nama ?? 'belum diatur'}  •  Status: ${STATUS_IPD[d.status].teks}`,
    ...(d.jejak ? [`Jalur GPS: ${teksJarak(d.jejak.jarak_m)} ditempuh dalam ${teksDurasi(d.jejak.durasi_menit)} (${d.jejak.titik.length} titik)`] : []),
    ...(d.pesan_tolak ? [`Alasan ditolak: ${d.pesan_tolak}`] : []),
    ...(d.catatan ? [`Catatan inspeksi: ${d.catatan}`] : []),
  ]
  const hasil =
    `${s.pokok_diperiksa} pokok diperiksa  •  Loses ${bulatPersen(s.persen_pokok_loses)}% (${s.pokok_loses} pokok, jumlah ${s.total_loses})` +
    `  •  Temuan lain ${bulatPersen(s.persen_pokok_temuan_lain)}% (${s.pokok_temuan_lain} pokok, jumlah ${s.total_temuan_lain})`
  const penutup =
    kena === 0
      ? `Semua ${d.pokok.length} pokok bersih, tidak ada temuan.`
      : `Pokok bersih: ${d.pokok.length - kena} dari ${d.pokok.length}. Pokok yang kena temuan: ${kena}.`
  return {
    judul: `${nomor}. ${d.pemanen_nama ?? d.pemanen_hrmb_code}  •  Blok ${d.blok_kode ?? '-'}  •  Ancak ${d.ancak_no ?? '-'}  •  ${d.divisi_kode}`,
    meta,
    hasil,
    kolom: KOLOM_TEMUAN,
    baris,
    kananIdx: [0, 1, 5],
    penutup,
    gambar,
  }
}

function jumlahkan(daftar: IpdDetail[]): { diperiksa: number; loses: number; lain: number } {
  let diperiksa = 0
  let loses = 0
  let lain = 0
  for (const d of daftar) {
    diperiksa += d.ringkasan.pokok_diperiksa
    loses += d.ringkasan.pokok_loses
    lain += d.ringkasan.pokok_temuan_lain
  }
  return { diperiksa, loses, lain }
}

const persen = (a: number, b: number) => (b === 0 ? 0 : bulatPersen((a / b) * 100))

/** Mengelompokkan inspeksi per tanggal (urut naik), lalu per pemanen di dalam tanggal. */
export function kelompokkanPerHari(daftar: IpdDetail[], fotoPer: Map<string, GambarEkspor[]>): HariEkspor[] {
  const peta = new Map<string, IpdDetail[]>()
  for (const d of daftar) peta.set(d.tanggal_inspeksi, [...(peta.get(d.tanggal_inspeksi) ?? []), d])
  return [...peta.keys()]
    .sort()
    .map((tgl) => {
      const isi = (peta.get(tgl) ?? []).slice().sort((a, b) => (a.pemanen_nama ?? '').localeCompare(b.pemanen_nama ?? '') || a.dibuat_pada.localeCompare(b.dibuat_pada))
      const j = jumlahkan(isi)
      return {
        judul: `Inspeksi tanggal ${dayjs(tgl).locale('id').format('dddd, D MMMM YYYY')}`,
        ringkas: `${isi.length} inspeksi  •  ${j.diperiksa} pokok diperiksa  •  Loses ${persen(j.loses, j.diperiksa)}% (${j.loses} pokok)  •  Temuan lain ${persen(j.lain, j.diperiksa)}% (${j.lain} pokok)`,
        inspeksi: isi.map((d, i) => blokInspeksi(d, i + 1, fotoPer.get(d.id) ?? [])),
      }
    })
}

/** Arti tiap kode beserta ringkasannya (pokok kena, jumlah, persen) dari seluruh inspeksi yang dimuat. */
export function ringkasKode(daftar: IpdDetail[]): TabelKecil {
  const total = daftar.reduce((n, d) => n + d.ringkasan.pokok_diperiksa, 0)
  const agg = new Map<string, IpdKodeAgg & { pokokSum: number; jumlahSum: number }>()
  for (const d of daftar) {
    for (const k of d.ringkasan.per_kode) {
      const a = agg.get(k.kode) ?? { ...k, pokokSum: 0, jumlahSum: 0 }
      a.pokokSum += k.pokok
      a.jumlahSum += k.jumlah
      agg.set(k.kode, a)
    }
  }
  return {
    judul: 'Arti kode temuan dan ringkasan per kode',
    // Urutan kolom mengikuti lebar kolom lembar Excel (sempit, sempit, sempit, lebar, sedang, sempit).
    kolom: ['Kode', 'Pokok', 'Jumlah', 'Temuan', 'Jenis', '% pokok'],
    baris: [...agg.values()].map((k) => [k.kode, k.pokokSum, k.jumlahSum, k.nama, JENIS[k.jenis] ?? k.jenis, persen(k.pokokSum, total)]),
    kananIdx: [1, 2, 5],
  }
}

/** Laporan (semua inspeksi yang dimuat) satu lembar, dikelompokkan per tanggal. */
export function laporanHarian(judul: string, keteranganAtas: Keterangan, daftar: IpdDetail[], fotoPer: Map<string, GambarEkspor[]>, ringkasan?: IpdRingkasan): LaporanEkspor {
  const j = jumlahkan(daftar)
  const lose = ringkasan ? ringkasan.persen_pokok_loses : persen(j.loses, j.diperiksa)
  const lain = ringkasan ? ringkasan.persen_pokok_temuan_lain : persen(j.lain, j.diperiksa)
  return {
    judul,
    keterangan: [
      ...keteranganAtas,
      ['Jumlah inspeksi', String(daftar.length)],
      ['Pokok diperiksa', String(j.diperiksa)],
      ['Loses (pokok kehilangan hasil)', `${bulatPersen(lose)}% (${j.loses} pokok)`],
      ['Temuan lain (pelepah)', `${bulatPersen(lain)}% (${j.lain} pokok)`],
      ['Cara menghitung', CARA_HITUNG],
    ],
    ringkasKode: ringkasKode(daftar),
    hari: kelompokkanPerHari(daftar, fotoPer),
  }
}
