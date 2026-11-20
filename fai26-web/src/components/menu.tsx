import {
  AppstoreOutlined,
  AuditOutlined,
  ClusterOutlined,
  DashboardOutlined,
  EnvironmentOutlined,
  MobileOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  TrophyOutlined,
  UserOutlined,
} from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { Link } from 'react-router-dom'
import { halamanBoleh } from '@/lib/izin'

const IKON: Record<string, React.ReactNode> = {
  '/': <DashboardOutlined />,
  '/panen': <ProfileOutlined />,
  '/produktivitas': <TrophyOutlined />,
  '/blok': <AppstoreOutlined />,
  '/tph': <EnvironmentOutlined />,
  '/divisi': <ClusterOutlined />,
  '/pemanen': <TeamOutlined />,
  '/pengguna': <UserOutlined />,
  '/hak-akses': <SafetyCertificateOutlined />,
  '/perangkat': <MobileOutlined />,
  '/audit': <AuditOutlined />,
}

/** Menu yang ditampilkan menurut permission; judul grup memisahkan laporan, master, dan administrasi. */
export function bangunMenu(profil: { permission: string[] } | null): MenuProps['items'] {
  const boleh = halamanBoleh(profil)
  const kelompok: { judul: string; path: string[] }[] = [
    { judul: 'Laporan', path: ['/', '/panen', '/produktivitas'] },
    { judul: 'Master', path: ['/blok', '/tph', '/divisi', '/pemanen'] },
    { judul: 'Administrasi', path: ['/pengguna', '/hak-akses', '/perangkat', '/audit'] },
  ]
  const items: MenuProps['items'] = []
  for (const k of kelompok) {
    const anak = boleh.filter((h) => k.path.includes(h.path))
    if (anak.length === 0) continue
    items.push({
      type: 'group',
      key: `g-${k.judul}`,
      label: k.judul,
      children: anak.map((h) => ({ key: h.path, icon: IKON[h.path], label: <Link to={h.path}>{h.judul}</Link> })),
    })
  }
  return items
}
