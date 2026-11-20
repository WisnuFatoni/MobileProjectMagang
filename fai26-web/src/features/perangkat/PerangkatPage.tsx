import { useQuery } from '@tanstack/react-query'
import { Card, Popconfirm, Switch, Table, Tag, Typography } from 'antd'
import { perangkatApi } from '@/api/endpoints'
import { idPerangkat } from '@/api/http'
import type { Perangkat } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { waktu } from '@/lib/format'
import { useAksi } from '@/lib/useAksi'

export function PerangkatPage() {
  const q = useQuery({ queryKey: ['perangkat'], queryFn: perangkatApi.daftar })
  const saya = idPerangkat()
  const atur = useAksi({
    fn: (v: { id: string; aktif: boolean }) => perangkatApi.atur(v.id, v.aktif),
    segarkan: [['perangkat'], ['ringkasan']],
    sukses: (_h, v) => (v.aktif ? 'Perangkat diaktifkan' : 'Perangkat dinonaktifkan dan sesinya dicabut'),
  })

  return (
    <>
      <PageHeader judul="Perangkat" keterangan="HP krani dan browser web admin yang pernah login. Menonaktifkan perangkat mencabut sesinya dan menolak login berikutnya, misalnya bila HP hilang." />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<Perangkat>
          rowKey="device_id"
          loading={q.isLoading}
          dataSource={q.data}
          pagination={{ pageSize: 25, hideOnSinglePage: true }}
          scroll={{ x: 860 }}
          columns={[
            {
              title: 'Perangkat',
              key: 'nama',
              render: (_: unknown, d) => (
                <>
                  <strong>{d.nama ?? 'Tanpa nama'}</strong>
                  {d.device_id === saya && <Tag color="green" style={{ marginLeft: 8 }}>Perangkat ini</Tag>}
                  <div className="tabel-sel-kode" style={{ opacity: 0.65 }}>{d.device_id.slice(0, 8)}…</div>
                </>
              ),
            },
            { title: 'Sistem', dataIndex: 'os_versi', render: (v: string | null) => v ?? '-' },
            { title: 'Versi aplikasi', dataIndex: 'app_versi', render: (v: string | null) => v ?? '-' },
            { title: 'Sinkron terakhir', dataIndex: 'terakhir_sinkron', render: (v: string | null) => waktu(v) },
            {
              title: 'Aktif',
              dataIndex: 'aktif',
              width: 110,
              render: (aktif: boolean, d) =>
                d.device_id === saya ? (
                  <Typography.Text type="secondary">Dipakai</Typography.Text>
                ) : aktif ? (
                  <Popconfirm title="Nonaktifkan perangkat ini?" description="Sesinya dicabut dan login dari perangkat ini ditolak." okText="Nonaktifkan" okButtonProps={{ danger: true }} cancelText="Batal" onConfirm={() => atur.mutate({ id: d.device_id, aktif: false })}>
                    <Switch checked aria-label={`Nonaktifkan ${d.nama ?? d.device_id}`} />
                  </Popconfirm>
                ) : (
                  <Switch checked={false} loading={atur.isPending} onChange={() => atur.mutate({ id: d.device_id, aktif: true })} aria-label={`Aktifkan ${d.nama ?? d.device_id}`} />
                ),
            },
          ]}
        />
      </Card>
    </>
  )
}
