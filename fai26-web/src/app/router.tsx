import { Button, Result } from 'antd'
import { lazy } from 'react'
import { createBrowserRouter, Link, Navigate } from 'react-router-dom'
import { AppLayout } from '@/components/AppLayout'
import { RequireAuth } from '@/features/auth/guards'
import { LoginPage } from '@/features/auth/LoginPage'
import { IZIN } from '@/lib/izin'
import { Halaman } from './Halaman'

// Halaman dimuat sesuai kebutuhan supaya muatan awal kecil.
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const PanenPage = lazy(() => import('@/features/panen/PanenPage').then((m) => ({ default: m.PanenPage })))
const TertundaPage = lazy(() => import('@/features/tertunda/TertundaPage').then((m) => ({ default: m.TertundaPage })))
const ProduktivitasPage = lazy(() => import('@/features/produktivitas/ProduktivitasPage').then((m) => ({ default: m.ProduktivitasPage })))
const IpdPage = lazy(() => import('@/features/ipd/IpdPage').then((m) => ({ default: m.IpdPage })))
const IpdLaporanPage = lazy(() => import('@/features/ipd/IpdLaporanPage').then((m) => ({ default: m.IpdLaporanPage })))
const BlokPage = lazy(() => import('@/features/master/BlokPage').then((m) => ({ default: m.BlokPage })))
const TphPage = lazy(() => import('@/features/master/TphPage').then((m) => ({ default: m.TphPage })))
const DivisiPage = lazy(() => import('@/features/master/DivisiPage').then((m) => ({ default: m.DivisiPage })))
const PemanenPage = lazy(() => import('@/features/master/PemanenPage').then((m) => ({ default: m.PemanenPage })))
const CetakQrPage = lazy(() => import('@/features/qr/CetakQrPage').then((m) => ({ default: m.CetakQrPage })))
const PenggunaPage = lazy(() => import('@/features/pengguna/PenggunaPage').then((m) => ({ default: m.PenggunaPage })))
const HakAksesPage = lazy(() => import('@/features/hakakses/HakAksesPage').then((m) => ({ default: m.HakAksesPage })))
const PerangkatPage = lazy(() => import('@/features/perangkat/PerangkatPage').then((m) => ({ default: m.PerangkatPage })))
const AuditPage = lazy(() => import('@/features/audit/AuditPage').then((m) => ({ default: m.AuditPage })))

export const rute = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Halaman izin={IZIN.laporanLihat}><DashboardPage /></Halaman> },
      { path: 'panen', element: <Halaman izin={IZIN.panenLihat}><PanenPage /></Halaman> },
      { path: 'tertunda', element: <Halaman izin={IZIN.panenLihat}><TertundaPage /></Halaman> },
      { path: 'produktivitas', element: <Halaman izin={IZIN.laporanLihat}><ProduktivitasPage /></Halaman> },
      { path: 'ipd', element: <Halaman izin={IZIN.ipdLihat}><IpdPage /></Halaman> },
      { path: 'ipd/laporan', element: <Halaman izin={IZIN.laporanIpd}><IpdLaporanPage /></Halaman> },
      { path: 'blok', element: <Halaman izin={IZIN.masterLihat}><BlokPage /></Halaman> },
      { path: 'tph', element: <Halaman izin={IZIN.masterLihat}><TphPage /></Halaman> },
      { path: 'tph/cetak', element: <Halaman izin={IZIN.masterLihat}><CetakQrPage /></Halaman> },
      { path: 'divisi', element: <Halaman izin={IZIN.masterLihat}><DivisiPage /></Halaman> },
      { path: 'pemanen', element: <Halaman izin={IZIN.masterLihat}><PemanenPage /></Halaman> },
      { path: 'pengguna', element: <Halaman izin={IZIN.userKelola}><PenggunaPage /></Halaman> },
      { path: 'hak-akses', element: <Halaman izin={IZIN.roleKelola}><HakAksesPage /></Halaman> },
      { path: 'perangkat', element: <Halaman izin={IZIN.deviceKelola}><PerangkatPage /></Halaman> },
      { path: 'audit', element: <Halaman izin={IZIN.auditLihat}><AuditPage /></Halaman> },
      {
        path: '*',
        element: (
          <Result
            status="404"
            title="Halaman tidak ditemukan"
            extra={<Link to="/"><Button type="primary">Ke beranda</Button></Link>}
          />
        ),
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]

/** Flag masa depan React Router v7 dinyalakan sekarang supaya migrasi nanti tanpa kejutan dan konsol bersih. */
export const router = createBrowserRouter(rute, {
  future: {
    v7_relativeSplatPath: true,
    v7_fetcherPersist: true,
    v7_normalizeFormMethod: true,
    v7_partialHydration: true,
    v7_skipActionErrorRevalidation: true,
  },
})
