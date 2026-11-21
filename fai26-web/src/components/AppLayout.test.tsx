import { describe, expect, it } from 'vitest'
import { kunciAktif } from '@/lib/menuAktif'
import { bangunMenu } from './menu'

type Grup = { type: 'group'; label: string; children: { key: string }[] }

const grup = (permission: string[]) => (bangunMenu({ permission }) as Grup[]).map((g) => ({ judul: g.label, anak: g.children.map((c) => c.key) }))

describe('menu yang menyala', () => {
  it('alamat bersarang menyalakan halamannya sendiri, bukan induknya', () => {
    expect(kunciAktif('/ipd/laporan')).toBe('/ipd/laporan')
    expect(kunciAktif('/ipd')).toBe('/ipd')
    expect(kunciAktif('/ipd/')).toBe('/ipd')
    expect(kunciAktif('/tph/cetak')).toBe('/tph')
    expect(kunciAktif('/')).toBe('/')
    expect(kunciAktif('/hak-akses')).toBe('/hak-akses')
  })
})

describe('menu', () => {
  it('admin melihat tiga kelompok lengkap', () => {
    const m = grup(['master.lihat', 'user.kelola', 'device.kelola', 'laporan.lihat', 'panen.lihat', 'audit.lihat'])
    expect(m).toEqual([
      { judul: 'Laporan', anak: ['/', '/panen', '/produktivitas'] },
      { judul: 'Master', anak: ['/blok', '/tph', '/divisi', '/pemanen'] },
      { judul: 'Administrasi', anak: ['/pengguna', '/perangkat', '/audit'] },
    ])
  })

  it('pemegang role.kelola melihat Hak Akses di grup Administrasi, antara Pengguna dan Perangkat', () => {
    const m = grup(['user.kelola', 'role.kelola', 'device.kelola'])
    expect(m).toEqual([{ judul: 'Administrasi', anak: ['/pengguna', '/hak-akses', '/perangkat'] }])
  })

  it('ASKEP dan KTU melihat laporan, catatan panen, produktivitas, dan IPD tanpa menu master atau administrasi', () => {
    const m = grup(['panen.lihat', 'laporan.lihat', 'master.lihat', 'ipd.lihat', 'laporan.ipd'])
    expect(m.map((g) => g.judul)).toEqual(['Laporan', 'IPD', 'Master'])
    expect(m[1].anak).toEqual(['/ipd', '/ipd/laporan'])
  })

  it('menu IPD hanya tampil bagi pemegang hak IPD, dan laporan terpisah dari daftar', () => {
    expect(grup(['ipd.lihat'])).toEqual([{ judul: 'IPD', anak: ['/ipd'] }])
    expect(grup(['laporan.ipd'])).toEqual([{ judul: 'IPD', anak: ['/ipd/laporan'] }])
  })

  it('kelompok tanpa halaman yang boleh dibuka tidak ditampilkan', () => {
    const m = grup(['master.lihat'])
    expect(m).toEqual([{ judul: 'Master', anak: ['/blok', '/tph', '/divisi', '/pemanen'] }])
    expect(grup([])).toEqual([])
  })
})
