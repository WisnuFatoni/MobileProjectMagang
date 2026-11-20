import dayjs from 'dayjs'
import 'dayjs/locale/id'
import type { StatusErp, StatusLokasi } from '@/api/types'

dayjs.locale('id')

export function angka(n: number | null | undefined): string {
  return n === null || n === undefined ? '-' : new Intl.NumberFormat('id-ID').format(n)
}

export function desimal(n: number | null | undefined, digit = 1): string {
  return n === null || n === undefined ? '-' : new Intl.NumberFormat('id-ID', { maximumFractionDigits: digit }).format(n)
}

/** 10 Nov 2026 */
export function tanggal(iso: string | null | undefined): string {
  return iso ? dayjs(iso).format('D MMM YYYY') : '-'
}

/** 10 Nov 2026 14.30 (jam setempat browser) */
export function waktu(iso: string | null | undefined): string {
  return iso ? dayjs(iso).format('D MMM YYYY HH.mm') : '-'
}

export const STATUS_ERP: Record<StatusErp, { teks: string; warna: string }> = {
  BARU: { teks: 'Baru', warna: 'gold' },
  DIBACA: { teks: 'Dibaca ERP', warna: 'blue' },
  DIIMPOR: { teks: 'Diimpor', warna: 'green' },
  DITOLAK: { teks: 'Ditolak ERP', warna: 'red' },
}

export const STATUS_LOKASI: Record<StatusLokasi, { teks: string; warna: string }> = {
  DI_TPH: { teks: 'Di TPH', warna: 'green' },
  DI_LUAR: { teks: 'Di luar TPH', warna: 'red' },
  AKURASI_RENDAH: { teks: 'GPS kurang akurat', warna: 'orange' },
  TANPA_GPS: { teks: 'Tanpa GPS', warna: 'default' },
  TPH_TANPA_KOORDINAT: { teks: 'TPH belum ada koordinat', warna: 'default' },
}

/** Nama kelas untuk tampilan (sama dengan aplikasi HP). */
export const NAMA_KELAS: Record<string, string> = {
  unripe: 'Mentah',
  underripe: 'Kurang matang',
  ripe: 'Matang',
  overripe: 'Lewat matang',
  empty: 'Tandan kosong',
  abnormal: 'Abnormal',
}
