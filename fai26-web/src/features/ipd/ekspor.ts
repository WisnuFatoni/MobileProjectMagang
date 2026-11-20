import type { IpdBaris, IpdDetail, IpdGrupLoses, IpdLaporan } from '@/api/types'
import type { DokumenEkspor, TabelEkspor } from '@/lib/ekspor'
import { tanggal, waktu } from '@/lib/format'
import { STATUS_IPD, temuanTeratas } from './ipdTampil'

/** Persen dibulatkan satu angka desimal tetapi tetap angka, supaya bisa dijumlah atau diurutkan di Excel. */
export const bulatPersen = (v: number): number => Math.round(v * 10) / 10

const JENIS: Record<string, string> = { LOSES: 'Loses', TEMUAN: 'Temuan lain' }

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
      ['Aturan', 'Loses = buah dan brondolan (memengaruhi hasil panen). Pelepah = temuan lain, tidak dihitung loses. Persen = pokok bersangkutan dibagi pokok diperiksa.'],
    ],
    tabel: [
      {
        judul: 'Per kode',
        kolom: ['Kode', 'Temuan', 'Jenis', 'Pokok', 'Jumlah', '% pokok'],
        baris: s.per_kode.map((k) => [k.kode, k.nama, JENIS[k.jenis] ?? k.jenis, k.pokok, k.jumlah, bulatPersen(k.persen_pokok)]),
        kananIdx: [3, 4, 5],
      },
      tabelGrup('Per pemanen', 'Pemanen', r.per_pemanen),
      tabelGrup('Per blok', 'Blok', r.per_blok),
      tabelGrup('Per divisi', 'Divisi', r.per_divisi),
      tabelGrup('Per tanggal', 'Tanggal', r.per_tanggal),
    ],
  }
}

/** Daftar inspeksi menurut filter yang sedang dipakai. */
export function dokumenDaftar(item: IpdBaris[], filterTeks: string): DokumenEkspor {
  return {
    judul: 'Daftar Inspeksi IPD',
    keterangan: [
      ['Filter', filterTeks],
      ['Jumlah inspeksi', String(item.length)],
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

/** Satu inspeksi lengkap: kepala, ringkasan per kode, dan semua pokok yang diperiksa. */
export function dokumenDetail(d: IpdDetail): DokumenEkspor {
  const s = d.ringkasan
  return {
    judul: `Inspeksi IPD ${d.pemanen_nama ?? d.pemanen_hrmb_code}`,
    keterangan: [
      ['Pemanen', d.pemanen_nama ?? String(d.pemanen_hrmb_code)],
      ['Blok dan divisi', `Blok ${d.blok_kode ?? '-'} • ${d.divisi_kode}${d.ancak_no ? ` • ancak ${d.ancak_no}` : ''}`],
      ['Tanggal inspeksi', tanggal(d.tanggal_inspeksi)],
      ['Diinspeksi oleh', d.inspektur_nama],
      ['Kemandoran', d.mandor_nama ?? '-'],
      ['Asisten penyetuju', d.asisten_nama ?? 'Belum diatur'],
      ['Status', STATUS_IPD[d.status].teks + (d.disetujui_pada ? ` (${waktu(d.disetujui_pada)})` : '')],
      ['Pokok diperiksa', String(s.pokok_diperiksa)],
      ['Loses', `${bulatPersen(s.persen_pokok_loses)}% (${s.pokok_loses} pokok, jumlah ${s.total_loses})`],
      ['Temuan lain (pelepah)', `${bulatPersen(s.persen_pokok_temuan_lain)}% (${s.pokok_temuan_lain} pokok, jumlah ${s.total_temuan_lain})`],
      ...(d.pesan_tolak ? ([['Alasan ditolak', d.pesan_tolak]] as [string, string][]) : []),
      ...(d.catatan ? ([['Catatan', d.catatan]] as [string, string][]) : []),
    ],
    tabel: [
      {
        judul: 'Per kode',
        kolom: ['Kode', 'Temuan', 'Jenis', 'Pokok', 'Jumlah', '% pokok'],
        baris: s.per_kode.filter((k) => k.jumlah > 0).map((k) => [k.kode, k.nama, JENIS[k.jenis] ?? k.jenis, k.pokok, k.jumlah, bulatPersen(k.persen_pokok)]),
        kananIdx: [3, 4, 5],
      },
      {
        judul: 'Pokok diperiksa',
        kolom: ['Baris', 'Pokok', 'Temuan'],
        baris: d.pokok.map((p) => [p.no_baris, p.no_pokok, p.temuan.length === 0 ? 'Bersih' : p.temuan.map((t) => `${t.kode} x${t.jumlah}`).join(', ')]),
        kananIdx: [0, 1],
      },
    ],
  }
}
