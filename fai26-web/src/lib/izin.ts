import type { Profil } from '@/api/types'

/** Permission yang dipakai web admin (nama sama dengan di server). */
export const IZIN = {
  masterLihat: 'master.lihat',
  masterKelola: 'master.kelola',
  userKelola: 'user.kelola',
  roleKelola: 'role.kelola',
  deviceKelola: 'device.kelola',
  laporanLihat: 'laporan.lihat',
  panenLihat: 'panen.lihat',
  auditLihat: 'audit.lihat',
  ipdLihat: 'ipd.lihat',
  laporanIpd: 'laporan.ipd',
  ipdJejak: 'ipd.jejak',
} as const

export function punyaIzin(profil: Pick<Profil, 'permission'> | null | undefined, izin: string): boolean {
  return !!profil && profil.permission.includes(izin)
}

export interface HalamanApp {
  path: string
  judul: string
  izin: string
}

/**
 * Halaman yang bisa dibuka, menurut permission. Urutannya juga urutan menu. Server tetap memeriksa ulang di setiap
 * panggilan; ini hanya menyembunyikan menu yang pasti ditolak.
 */
export const HALAMAN: HalamanApp[] = [
  { path: '/', judul: 'Dasbor', izin: IZIN.laporanLihat },
  { path: '/panen', judul: 'Catatan Panen', izin: IZIN.panenLihat },
  { path: '/tertunda', judul: 'Persetujuan Tertunda', izin: IZIN.panenLihat },
  { path: '/produktivitas', judul: 'Produktivitas Pemanen', izin: IZIN.laporanLihat },
  { path: '/ipd', judul: 'Inspeksi IPD', izin: IZIN.ipdLihat },
  { path: '/ipd/peta', judul: 'Peta Monitoring IPD', izin: IZIN.ipdJejak },
  { path: '/ipd/laporan', judul: 'Laporan Loses IPD', izin: IZIN.laporanIpd },
  { path: '/blok', judul: 'Blok', izin: IZIN.masterLihat },
  { path: '/tph', judul: 'TPH dan QR', izin: IZIN.masterLihat },
  { path: '/divisi', judul: 'Divisi', izin: IZIN.masterLihat },
  { path: '/pemanen', judul: 'Pemanen', izin: IZIN.masterLihat },
  { path: '/pengguna', judul: 'Pengguna', izin: IZIN.userKelola },
  { path: '/hak-akses', judul: 'Hak Akses', izin: IZIN.roleKelola },
  { path: '/perangkat', judul: 'Perangkat', izin: IZIN.deviceKelola },
  { path: '/audit', judul: 'Audit Log', izin: IZIN.auditLihat },
]

export function halamanBoleh(profil: Pick<Profil, 'permission'> | null | undefined): HalamanApp[] {
  return HALAMAN.filter((h) => punyaIzin(profil, h.izin))
}

/** Halaman pertama yang boleh dibuka (tujuan setelah login), atau null bila tidak ada. */
export function halamanAwal(profil: Pick<Profil, 'permission'> | null | undefined): string | null {
  return halamanBoleh(profil)[0]?.path ?? null
}
