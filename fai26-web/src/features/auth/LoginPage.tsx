import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '@/api/http'
import { Logo } from '@/components/Logo'
import { halamanAwal } from '@/lib/izin'
import { useAuth } from './AuthProvider'
import './login.css'

interface Nilai {
  username: string
  password: string
}

const POIN = [
  'Master blok, TPH, dan pemanen yang turun otomatis ke HP krani',
  'Cetak QR TPH sesuai spesifikasi, siap dipasang di lapangan',
  'Pantau catatan panen, lokasi GPS, dan foto bukti',
]

export function LoginPage() {
  const { status, profil, masuk, keluar } = useAuth()
  const navigate = useNavigate()
  const lokasi = useLocation() as { state?: { dari?: string } }
  const [galat, setGalat] = useState<string | null>(null)
  const [proses, setProses] = useState(false)

  if (status === 'masuk' && halamanAwal(profil)) {
    return <Navigate to={lokasi.state?.dari ?? halamanAwal(profil)!} replace />
  }

  async function kirim(v: Nilai) {
    setGalat(null)
    setProses(true)
    try {
      const p = await masuk(v.username.trim(), v.password)
      const awal = halamanAwal(p)
      if (!awal) {
        await keluar()
        setGalat('Akun ini tidak punya akses ke web admin. Gunakan akun admin, asisten, atau auditor.')
        return
      }
      navigate(lokasi.state?.dari ?? awal, { replace: true })
    } catch (e) {
      setGalat(e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')
    } finally {
      setProses(false)
    }
  }

  return (
    <div className="login">
      <aside className="login__merek" aria-hidden="true">
        <div className="login__merek-isi">
          <Logo ukuran={56} />
          <h1>fAI26 Admin</h1>
          <p className="login__tagline">Satu tempat untuk mengelola data panen sawit dari TPH sampai ERP.</p>
          <ul>
            {POIN.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        </div>
        <small>fAI26 · pencatatan panen dan grading TBS</small>
      </aside>

      <main className="login__form">
        <div className="login__kartu">
          <div className="login__logo-kecil">
            <Logo ukuran={40} />
          </div>
          <Typography.Title level={2} style={{ marginBottom: 4 }}>
            Masuk
          </Typography.Title>
          <Typography.Paragraph type="secondary">Gunakan akun yang diberikan admin kebun.</Typography.Paragraph>

          {galat && <Alert type="error" showIcon message={galat} style={{ marginBottom: 16 }} role="alert" />}

          <Form<Nilai> layout="vertical" onFinish={kirim} requiredMark={false} autoComplete="on" size="large">
            <Form.Item
              name="username"
              label="Username"
              rules={[{ required: true, whitespace: true, message: 'Isi username' }]}
            >
              <Input prefix={<UserOutlined />} autoComplete="username" autoFocus placeholder="contoh: admin1" />
            </Form.Item>
            <Form.Item name="password" label="Password" rules={[{ required: true, message: 'Isi password' }]}>
              <Input.Password prefix={<LockOutlined />} autoComplete="current-password" placeholder="Password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={proses} style={{ height: 46, marginTop: 8 }}>
              Masuk
            </Button>
          </Form>
        </div>
      </main>
    </div>
  )
}
