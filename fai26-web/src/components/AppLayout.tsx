import { KeyOutlined, LogoutOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons'
import { Avatar, Button, Dropdown, Layout, Menu, Space, Tag, Tooltip, Typography, type MenuProps } from 'antd'
import { useMemo, useState } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { useTema } from '@/app/tema'
import { useAuth } from '@/features/auth/AuthProvider'
import { GantiPasswordModal } from '@/features/auth/GantiPasswordModal'
import { halamanBoleh } from '@/lib/izin'
import { kunciAktif } from '@/lib/menuAktif'
import { bangunMenu } from './menu'
import { Logo } from './Logo'
import './layout.css'

export function AppLayout() {
  const { profil, keluar } = useAuth()
  const { mode, ganti } = useTema()
  const lokasi = useLocation()
  const [lipat, setLipat] = useState(false)
  const [bukaGanti, setBukaGanti] = useState(false)
  // Setelah admin mengatur ulang password, pengguna wajib menggantinya sebelum memakai aplikasi.
  const wajibGanti = profil?.wajib_ganti_password === true
  const menu = useMemo(() => bangunMenu(profil), [profil])
  const judul = halamanBoleh(profil).find((h) => h.path === kunciAktif(lokasi.pathname))?.judul ?? 'fAI26'

  const menuAkun: MenuProps['items'] = [
    {
      key: 'info',
      disabled: true,
      label: (
        <div>
          <strong>{profil?.nama}</strong>
          <div style={{ fontSize: 12 }}>@{profil?.username} · {profil?.unit_kode}</div>
        </div>
      ),
    },
    { type: 'divider' },
    { key: 'password', icon: <KeyOutlined />, label: 'Ganti password', onClick: () => setBukaGanti(true) },
    { key: 'keluar', icon: <LogoutOutlined />, label: 'Keluar', danger: true, onClick: () => void keluar() },
  ]

  return (
    <Layout className="app" hasSider>
      <Layout.Sider
        breakpoint="lg"
        collapsedWidth={0}
        width={248}
        collapsible
        collapsed={lipat}
        onCollapse={setLipat}
        className="app__sider"
        theme="dark"
        zeroWidthTriggerStyle={{ top: 14 }}
      >
        <Link to="/" className="app__merek">
          <Logo ukuran={34} />
          <span>
            <strong>fAI26</strong>
            <small>Admin</small>
          </span>
        </Link>
        <Menu theme="dark" mode="inline" selectedKeys={[kunciAktif(lokasi.pathname)]} items={menu} />
      </Layout.Sider>

      <Layout>
        <Layout.Header className="app__header">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {judul}
          </Typography.Title>
          <Space size="middle">
            <Tooltip title={mode === 'gelap' ? 'Tema terang' : 'Tema gelap'}>
              <Button
                type="text"
                shape="circle"
                aria-label="Ganti tema"
                icon={mode === 'gelap' ? <SunOutlined /> : <MoonOutlined />}
                onClick={ganti}
              />
            </Tooltip>
            <Dropdown menu={{ items: menuAkun }} trigger={['click']} placement="bottomRight">
              <Button type="text" className="app__akun" aria-label="Menu akun">
                <Avatar size={30} style={{ background: '#15803d' }}>
                  {profil?.nama.slice(0, 1).toUpperCase()}
                </Avatar>
                <span className="app__akun-nama">
                  {profil?.nama}
                  <Tag bordered={false} color="green" style={{ marginLeft: 8 }}>
                    {profil?.role[0] ?? '-'}
                  </Tag>
                </span>
              </Button>
            </Dropdown>
          </Space>
        </Layout.Header>
        <Layout.Content className="app__isi">
          <Outlet />
        </Layout.Content>
      </Layout>
      <GantiPasswordModal buka={bukaGanti || wajibGanti} wajib={wajibGanti} onTutup={() => setBukaGanti(false)} />
    </Layout>
  )
}
