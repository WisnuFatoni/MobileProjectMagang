import { Progress, Space, Table, Tag, Typography } from 'antd'
import type { PemanenProduktif } from '@/api/types'
import { angka, desimal } from '@/lib/format'

/**
 * Ranking produktivitas pemanen satu hari. Hanya menampilkan janjang, mutu (persen matang), dan brondolan dari
 * catatan yang sudah dikirim. Premi (rupiah) tidak dihitung di sini: tarif dan basis premi dimiliki ERP.
 */
export function RankingPemanen({ data, rata }: { data: PemanenProduktif[]; rata: number }) {
  const maks = Math.max(...data.map((p) => p.janjang), 1)
  return (
    <Table<PemanenProduktif>
      rowKey="hrmb_code"
      dataSource={data}
      pagination={false}
      size="middle"
      locale={{ emptyText: 'Belum ada catatan panen pada tanggal ini.' }}
      columns={[
        { title: '#', dataIndex: 'peringkat', width: 56, align: 'center', render: (v: number) => <strong>{v}</strong> },
        {
          title: 'Pemanen',
          key: 'pemanen',
          render: (_: unknown, p) => (
            <>
              <strong>{p.nama || 'Pemanen tidak ada di master'}</strong>
              <div style={{ opacity: 0.65 }} className="tabel-sel-kode">
                {p.hrmb_code}
              </div>
            </>
          ),
        },
        { title: 'Catatan', dataIndex: 'catatan', align: 'right', width: 90, render: (v: number) => angka(v) },
        {
          title: 'Janjang',
          key: 'janjang',
          width: 240,
          render: (_: unknown, p) => (
            <Space direction="vertical" size={2} style={{ width: '100%' }}>
              <strong>{angka(p.janjang)}</strong>
              <Progress
                percent={Math.round((p.janjang / maks) * 100)}
                showInfo={false}
                size="small"
                strokeColor="#15803d"
                aria-label={`Janjang ${p.nama}`}
              />
            </Space>
          ),
        },
        { title: 'Matang', dataIndex: 'persen_matang', align: 'right', width: 100, render: (v: number) => `${desimal(v)}%` },
        { title: 'Brondolan', dataIndex: 'brondolan_kg', align: 'right', width: 110, render: (v: number) => `${desimal(v)} kg` },
        {
          title: 'Keterangan',
          key: 'ket',
          render: (_: unknown, p) => (
            <Space size={4} wrap>
              {p.janjang > rata && <Tag color="green">Di atas rata-rata</Tag>}
              {p.janjang < rata && <Tag>Di bawah rata-rata</Tag>}
              {p.janjang === rata && <Tag color="blue">Sama dengan rata-rata</Tag>}
              {p.menunggu_persetujuan > 0 && <Tag color="gold">{p.menunggu_persetujuan} menunggu persetujuan</Tag>}
            </Space>
          ),
        },
      ]}
      footer={() => (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Catatan yang ditolak tidak dihitung. Premi dihitung di ERP berdasarkan tarif dan basis perusahaan.
        </Typography.Text>
      )}
    />
  )
}
