import {
  AlertOutlined,
  EnvironmentOutlined,
  MobileOutlined,
  ProfileOutlined,
  ReloadOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Col, DatePicker, Progress, Row, Skeleton, Space, Statistic, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { laporanApi } from '@/api/endpoints'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { STATUS_ERP, angka } from '@/lib/format'
import type { StatusErp } from '@/api/types'
import { BarChart } from './BarChart'

const URUTAN: StatusErp[] = ['BARU', 'DIBACA', 'DIIMPOR', 'DITOLAK']

export function DashboardPage() {
  const [hari, setHari] = useState<Dayjs>(dayjs())
  const tanggal = hari.format('YYYY-MM-DD')
  const q = useQuery({ queryKey: ['ringkasan', tanggal], queryFn: () => laporanApi.ringkasan(tanggal) })

  const r = q.data
  const totalStatus = r ? URUTAN.reduce((n, s) => n + (r.catatan_per_status[s] ?? 0), 0) : 0

  return (
    <>
      <PageHeader
        judul="Dasbor"
        keterangan="Ringkasan catatan panen unit Anda."
        aksi={
          <Space>
            <DatePicker
              value={hari}
              onChange={(d) => d && setHari(d)}
              allowClear={false}
              format="D MMM YYYY"
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              aria-label="Tanggal ringkasan"
            />
            <Button icon={<ReloadOutlined />} onClick={() => void q.refetch()} loading={q.isFetching}>
              Muat ulang
            </Button>
          </Space>
        }
      />

      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      {q.isLoading && <Skeleton active paragraph={{ rows: 6 }} />}

      {r && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} xl={6}>
              <Card className="kartu-statistik">
                <Statistic title="Catatan hari ini" value={r.catatan_hari_ini} prefix={<ProfileOutlined />} />
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Card className="kartu-statistik">
                <Statistic title="Janjang hari ini" value={r.janjang_hari_ini} formatter={(v) => angka(Number(v))} />
              </Card>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Link to="/panen?status_erp=BARU" aria-label="Lihat catatan yang perlu perhatian">
                <Card className="kartu-statistik" hoverable>
                  <Statistic
                    title="Perlu perhatian"
                    value={r.perlu_perhatian}
                    prefix={<AlertOutlined />}
                    valueStyle={r.perlu_perhatian > 0 ? { color: '#ca8a04' } : undefined}
                  />
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Lokasi di luar TPH, GPS lemah atau mati, lokasi palsu
                  </Typography.Text>
                </Card>
              </Link>
            </Col>
            <Col xs={24} sm={12} xl={6}>
              <Link to="/tph" aria-label="Lihat TPH tanpa koordinat">
                <Card className="kartu-statistik" hoverable>
                  <Statistic
                    title="TPH tanpa koordinat"
                    value={r.tph_tanpa_koordinat}
                    suffix={`/ ${angka(r.tph_aktif)}`}
                    prefix={<EnvironmentOutlined />}
                  />
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Dibutuhkan untuk verifikasi lokasi GPS
                  </Typography.Text>
                </Card>
              </Link>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={14}>
              <Card title="Janjang per hari (7 hari terakhir)">
                <BarChart data={r.tren_7_hari} />
              </Card>
            </Col>
            <Col xs={24} lg={10}>
              <Card title="Status catatan di server">
                <Space direction="vertical" style={{ width: '100%' }} size={14}>
                  {URUTAN.map((s) => {
                    const n = r.catatan_per_status[s] ?? 0
                    return (
                      <div key={s}>
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                          <span>{STATUS_ERP[s].teks}</span>
                          <strong>{angka(n)}</strong>
                        </div>
                        <Progress
                          percent={totalStatus ? Math.round((n / totalStatus) * 100) : 0}
                          showInfo={false}
                          size="small"
                          strokeColor={s === 'DITOLAK' ? '#dc2626' : s === 'DIIMPOR' ? '#16a34a' : s === 'DIBACA' ? '#0e7490' : '#ca8a04'}
                        />
                      </div>
                    )
                  })}
                </Space>
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12}>
              <Card>
                <Statistic title="Perangkat aktif" value={r.perangkat_aktif} prefix={<MobileOutlined />} />
              </Card>
            </Col>
            <Col xs={24} sm={12}>
              <Card>
                <Statistic title="Pengguna aktif" value={r.pengguna_aktif} prefix={<TeamOutlined />} />
              </Card>
            </Col>
          </Row>
        </Space>
      )}
    </>
  )
}
