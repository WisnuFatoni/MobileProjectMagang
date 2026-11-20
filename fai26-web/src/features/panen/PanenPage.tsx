import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Card, DatePicker, Input, Select, Table, Typography } from 'antd'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { laporanApi } from '@/api/endpoints'
import type { FilterPanen, PanenRingkas } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { StatusErpTag, StatusLokasiTag } from '@/components/StatusTag'
import { STATUS_ERP, angka, desimal, tanggal, waktu } from '@/lib/format'
import { PanenDetailDrawer } from './PanenDetailDrawer'

const HALAMAN = 25

/** Filter hidup di URL supaya bisa dibagikan dan dipakai tautan dari dasbor (mis. ?status_erp=BARU). */
export function PanenPage() {
  const [params, setParams] = useSearchParams()
  const [buka, setBuka] = useState<string | null>(null)
  const [cari, setCari] = useState(params.get('cari') ?? '')

  const filter: FilterPanen = useMemo(
    () => ({
      dari: params.get('dari') ?? undefined,
      sampai: params.get('sampai') ?? undefined,
      status_erp: params.get('status_erp') ?? undefined,
      cari: params.get('cari') ?? undefined,
      halaman: Number(params.get('halaman')) || 1,
      per_halaman: HALAMAN,
    }),
    [params],
  )

  const q = useQuery({ queryKey: ['panen', filter], queryFn: () => laporanApi.panen(filter), placeholderData: keepPreviousData })

  function ubah(kunci: string, nilai: string | undefined, reset = true) {
    const p = new URLSearchParams(params)
    if (nilai) p.set(kunci, nilai)
    else p.delete(kunci)
    if (reset) p.delete('halaman')
    setParams(p, { replace: true })
  }

  return (
    <>
      <PageHeader judul="Catatan Panen" keterangan="Catatan yang sudah dikirim HP dan disetujui krani. Klik baris untuk melihat detail dan foto." />

      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
          <DatePicker.RangePicker
            value={[filter.dari ? dayjs(filter.dari) : null, filter.sampai ? dayjs(filter.sampai) : null]}
            format="D MMM YYYY"
            allowEmpty={[true, true]}
            onChange={(r) => {
              const p = new URLSearchParams(params)
              if (r?.[0]) p.set('dari', r[0].format('YYYY-MM-DD')); else p.delete('dari')
              if (r?.[1]) p.set('sampai', r[1].format('YYYY-MM-DD')); else p.delete('sampai')
              p.delete('halaman')
              setParams(p, { replace: true })
            }}
            aria-label="Rentang tanggal panen"
          />
          <Select
            allowClear
            placeholder="Semua status ERP"
            style={{ width: 190 }}
            value={filter.status_erp}
            options={(Object.keys(STATUS_ERP) as (keyof typeof STATUS_ERP)[]).map((s) => ({ value: s, label: STATUS_ERP[s].teks }))}
            onChange={(v) => ubah('status_erp', v)}
            aria-label="Filter status ERP"
          />
          <Input.Search
            allowClear
            placeholder="Cari kode blok atau TPH"
            style={{ width: 240 }}
            value={cari}
            onChange={(e) => setCari(e.target.value)}
            onSearch={(v) => ubah('cari', v.trim() || undefined)}
            aria-label="Cari catatan"
          />
        </div>
      </Card>

      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<PanenRingkas>
          rowKey="id"
          loading={q.isFetching}
          dataSource={q.data?.item}
          onRow={(r) => ({ onClick: () => setBuka(r.id), style: { cursor: 'pointer' } })}
          pagination={{
            current: filter.halaman,
            pageSize: HALAMAN,
            total: q.data?.total ?? 0,
            showSizeChanger: false,
            showTotal: (t) => `${angka(t)} catatan`,
            onChange: (h) => ubah('halaman', String(h), false),
          }}
          scroll={{ x: 980 }}
          columns={[
            { title: 'Tanggal', dataIndex: 'tanggal_panen', render: (v: string) => tanggal(v) },
            { title: 'Blok / TPH', key: 'lokasi', render: (_: unknown, r) => <strong>{r.blok_kode ?? '-'} / {r.tph_kode ?? '-'}</strong> },
            { title: 'Janjang', dataIndex: 'total_janjang', align: 'right', render: (v: number) => angka(v) },
            { title: 'Brondolan', dataIndex: 'brondolan_kg', align: 'right', render: (v: number) => `${desimal(v)} kg` },
            {
              title: 'Lokasi',
              key: 'status_lokasi',
              render: (_: unknown, r) => (
                <>
                  <StatusLokasiTag status={r.status_lokasi} mock={r.gps_mock} />
                  {r.jarak_ke_tph_m !== null && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{desimal(r.jarak_ke_tph_m, 0)} m</Typography.Text>}
                </>
              ),
            },
            { title: 'Pencatat', dataIndex: 'pencatat', render: (v: string | null) => v ?? '-' },
            { title: 'Disetujui', dataIndex: 'penyetuju', render: (v: string | null) => v ?? '-' },
            { title: 'Status ERP', dataIndex: 'status_erp', render: (v) => <StatusErpTag status={v} /> },
            { title: 'Diterima', dataIndex: 'diterima_server', render: (v: string) => waktu(v) },
          ]}
        />
      </Card>
      <PanenDetailDrawer id={buka} onClose={() => setBuka(null)} />
    </>
  )
}
