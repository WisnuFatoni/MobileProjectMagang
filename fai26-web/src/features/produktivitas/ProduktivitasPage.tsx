import { ReloadOutlined, TeamOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Col, DatePicker, Row, Skeleton, Space, Statistic } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { laporanApi } from '@/api/endpoints'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { angka, desimal } from '@/lib/format'
import { RankingPemanen } from './RankingPemanen'

export function ProduktivitasPage() {
  const [hari, setHari] = useState<Dayjs>(dayjs())
  const tanggal = hari.format('YYYY-MM-DD')
  const q = useQuery({ queryKey: ['produktivitas', tanggal], queryFn: () => laporanApi.produktivitas(tanggal) })
  const r = q.data

  return (
    <>
      <PageHeader
        judul="Produktivitas Pemanen"
        keterangan="Ranking janjang tiap pemanen pada satu hari, dari catatan panen yang sudah dikirim. Premi (rupiah) dihitung di ERP, bukan di sini."
        aksi={
          <Space>
            <DatePicker
              value={hari}
              onChange={(d) => d && setHari(d)}
              allowClear={false}
              format="D MMM YYYY"
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              aria-label="Tanggal produktivitas"
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
            <Col xs={24} sm={8}>
              <Card className="kartu-statistik">
                <Statistic title="Pemanen yang mencatat" value={r.jumlah_pemanen} prefix={<TeamOutlined />} />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card className="kartu-statistik">
                <Statistic title="Total janjang" value={r.total_janjang} formatter={(v) => angka(Number(v))} />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card className="kartu-statistik">
                <Statistic title="Rata-rata janjang per pemanen" value={r.rata_janjang} formatter={(v) => desimal(Number(v))} />
              </Card>
            </Col>
          </Row>
          <Card title="Ranking produktivitas harian" styles={{ body: { padding: 0 } }}>
            <RankingPemanen data={r.pemanen} rata={r.rata_janjang} />
          </Card>
        </Space>
      )}
    </>
  )
}
