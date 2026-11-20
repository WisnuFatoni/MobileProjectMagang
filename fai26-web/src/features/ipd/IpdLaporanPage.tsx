import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Col, DatePicker, Row, Select, Skeleton, Space, Statistic, Table, Tabs, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { divisiApi, ipdApi } from '@/api/endpoints'
import type { IpdGrupLoses } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { angka, desimal } from '@/lib/format'
import { BatangLoses } from './BatangLoses'
import { NAMA_KELOMPOK, temuanTeratas, warnaLoses } from './ipdTampil'

function TabelGrup({ data, judul }: { data: IpdGrupLoses[]; judul: string }) {
  return (
    <Table<IpdGrupLoses>
      rowKey="kunci"
      size="middle"
      pagination={{ pageSize: 15, showSizeChanger: false, hideOnSinglePage: true }}
      dataSource={data}
      locale={{ emptyText: 'Belum ada inspeksi yang disetujui pada rentang ini.' }}
      scroll={{ x: 760 }}
      columns={[
        { title: judul, dataIndex: 'nama', render: (v: string) => <strong>{v}</strong> },
        { title: 'Inspeksi', dataIndex: 'inspeksi', align: 'right', width: 90 },
        { title: 'Pokok diperiksa', dataIndex: 'pokok_diperiksa', align: 'right', width: 130, render: (v: number) => angka(v) },
        { title: 'Pokok bertemuan', dataIndex: 'pokok_bertemuan', align: 'right', width: 130, render: (v: number) => angka(v) },
        {
          title: 'Loses',
          dataIndex: 'persen_pokok_bertemuan',
          align: 'right',
          width: 90,
          render: (v: number) => <strong style={{ color: warnaLoses(v) }}>{desimal(v)}%</strong>,
        },
        { title: 'Temuan terbanyak', key: 'teratas', render: (_: unknown, g) => temuanTeratas(g) },
      ]}
    />
  )
}

/**
 * Laporan loses dari inspeksi IPD yang sudah disetujui asisten: loses per kode temuan, per kelompok (buah, brondolan, pelepah),
 * per pemanen, blok, divisi, dan tanggal. Persen = pokok bertemuan dibagi pokok diperiksa.
 */
export function IpdLaporanPage() {
  const [rentang, setRentang] = useState<[Dayjs, Dayjs]>([dayjs().subtract(29, 'day'), dayjs()])
  const [divisiKode, setDivisiKode] = useState<string | undefined>()
  const dari = rentang[0].format('YYYY-MM-DD')
  const sampai = rentang[1].format('YYYY-MM-DD')
  const divisi = useQuery({ queryKey: ['divisi'], queryFn: divisiApi.daftar })
  const q = useQuery({
    queryKey: ['ipd-laporan', dari, sampai, divisiKode],
    queryFn: () => ipdApi.laporan({ dari, sampai, divisi_kode: divisiKode }),
  })
  const r = q.data

  return (
    <>
      <PageHeader
        judul="Laporan Loses IPD"
        keterangan="Kehilangan hasil di titik panen dari inspeksi yang sudah disetujui asisten: brondolan tidak dikutip, buah tinggal, dan temuan lain."
        aksi={
          <Space wrap>
            <DatePicker.RangePicker
              value={rentang}
              allowClear={false}
              onChange={(v) => v?.[0] && v[1] && setRentang([v[0], v[1]])}
              format="D MMM YYYY"
              disabledDate={(d) => d.isAfter(dayjs(), 'day')}
              aria-label="Rentang tanggal laporan"
            />
            <Select
              allowClear
              placeholder="Semua divisi"
              style={{ width: 160 }}
              value={divisiKode}
              onChange={setDivisiKode}
              options={(divisi.data ?? []).map((d) => ({ value: d.kode, label: d.nama }))}
              aria-label="Divisi"
            />
            <Button icon={<ReloadOutlined />} onClick={() => void q.refetch()} loading={q.isFetching}>
              Muat ulang
            </Button>
          </Space>
        }
      />

      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      {q.isLoading && <Skeleton active paragraph={{ rows: 8 }} />}

      {r && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}>
              <Card className="kartu-statistik">
                <Statistic title="Inspeksi disetujui" value={r.inspeksi} formatter={(v) => angka(Number(v))} />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card className="kartu-statistik">
                <Statistic title="Pokok diperiksa" value={r.ringkasan.pokok_diperiksa} formatter={(v) => angka(Number(v))} />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card className="kartu-statistik">
                <Statistic
                  title="Pokok bertemuan (loses)"
                  value={r.ringkasan.persen_pokok_bertemuan}
                  suffix="%"
                  valueStyle={{ color: warnaLoses(r.ringkasan.persen_pokok_bertemuan) }}
                  formatter={(v) => desimal(Number(v))}
                />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card className="kartu-statistik">
                <Statistic title="Total temuan" value={r.ringkasan.total_temuan} formatter={(v) => angka(Number(v))} />
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            {r.per_kelompok.map((k) => (
              <Col xs={24} md={8} key={k.kelompok}>
                <Card className="kartu-statistik">
                  <Statistic
                    title={`${NAMA_KELOMPOK[k.kelompok] ?? k.kelompok}: pokok bertemuan`}
                    value={k.persen_pokok}
                    suffix="%"
                    formatter={(v) => desimal(Number(v))}
                  />
                  <Typography.Text type="secondary">{angka(k.pokok)} pokok • jumlah temuan {angka(k.jumlah)}</Typography.Text>
                </Card>
              </Col>
            ))}
          </Row>

          <Card title="Loses per kode temuan">
            <BatangLoses data={r.ringkasan.per_kode} />
          </Card>

          <Card styles={{ body: { paddingTop: 0 } }}>
            <Tabs
              items={[
                { key: 'pemanen', label: 'Per pemanen', children: <TabelGrup data={r.per_pemanen} judul="Pemanen" /> },
                { key: 'blok', label: 'Per blok', children: <TabelGrup data={r.per_blok} judul="Blok" /> },
                { key: 'divisi', label: 'Per divisi', children: <TabelGrup data={r.per_divisi} judul="Divisi" /> },
                { key: 'tanggal', label: 'Per tanggal', children: <TabelGrup data={r.per_tanggal} judul="Tanggal" /> },
              ]}
            />
          </Card>
        </Space>
      )}
    </>
  )
}
