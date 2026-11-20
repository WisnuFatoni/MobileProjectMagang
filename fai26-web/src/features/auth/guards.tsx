import { Button, Result, Spin } from 'antd'
import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthProvider'

/** Halaman di dalam ini hanya untuk yang sudah login. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, cobaLagi } = useAuth()
  const lokasi = useLocation()
  if (status === 'gangguan') {
    return (
      <Result
        status="warning"
        title="Server belum bisa dihubungi"
        subTitle="Sesi Anda masih tersimpan. Server mungkin sedang sibuk atau jaringan terputus."
        extra={<Button type="primary" onClick={cobaLagi}>Coba lagi</Button>}
      />
    )
  }
  if (status === 'memuat') {
    return (
      <div style={{ display: 'grid', placeItems: 'center', alignContent: 'center', gap: 12, height: '100vh' }} role="status">
        <Spin size="large" />
        <span style={{ opacity: 0.65 }}>Memuat sesi...</span>
      </div>
    )
  }
  if (status === 'keluar') return <Navigate to="/login" replace state={{ dari: lokasi.pathname + lokasi.search }} />
  return <>{children}</>
}

/** Halaman di dalam ini hanya untuk pemegang permission tertentu. */
export function RequireIzin({ izin, children }: { izin: string; children: ReactNode }) {
  const { bisa } = useAuth()
  if (!bisa(izin)) {
    return (
      <Result
        status="403"
        title="Tidak punya akses"
        subTitle="Akun Anda tidak memegang hak yang dibutuhkan halaman ini. Hubungi admin bila perlu."
        extra={
          <Link to="/">
            <Button type="primary">Ke beranda</Button>
          </Link>
        }
      />
    )
  }
  return <>{children}</>
}
