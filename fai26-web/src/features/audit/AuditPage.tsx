import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { Card, DatePicker, Input, Select, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { laporanApi } from '@/api/endpoints'
import type { AuditItem, FilterAudit } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { angka, waktu } from '@/lib/format'
import { rapikanJson } from './rapikan'

const HALAMAN = 25
const TABEL = ['app_user', 'blok', 'divisi', 'device', 'fai_panen', 'panen_header', 'panen_grading', 'pemanen', 'tph']
const WARNA_AKSI: Record<string, string> = { BUAT: 'green', UBAH: 'blue', HAPUS: 'red', LOGIN: 'cyan', LOGIN_GAGAL: 'orange', SETUJU: 'purple', TERIMA: 'green', RESET_PASSWORD: 'gold', AKTIFKAN: 'green', NONAKTIFKAN: 'red', LIHAT_JALUR: 'magenta', HAPUS_JEJAK_LAMA: 'volcano' }

export function AuditPage() {
  const [params, setParams] = useSearchParams()
  const [cari, setCari] = useState(params.get('cari') ?? '')
  const filter: FilterAudit = useMemo(
    () => ({
      tabel: params.get('tabel') ?? undefined,
      aksi: params.get('aksi') ?? undefined,
      dari: params.get('dari') ?? undefined,
      sampai: params.get('sampai') ?? undefined,
      cari: params.get('cari') ?? undefined,
      halaman: Number(params.get('halaman')) || 1,
      per_halaman: HALAMAN,
    }),
    [params],
  )
  const q = useQuery({ queryKey: ['audit', filter], queryFn: () => laporanApi.audit(filter), placeholderData: keepPreviousData })

  function ubah(kunci: string, nilai: string | undefined, reset = true) {
    const p = new URLSearchParams(params)
    if (nilai) p.set(kunci, nilai)
    else p.delete(kunci)
    if (reset) p.delete('halaman')
    setParams(p, { replace: true })
  }

  return (
    <>
      <PageHeader judul="Audit Log" keterangan="Jejak semua perubahan penting: siapa, kapan, dari perangkat mana. Tabel ini hanya bisa ditambah, tidak bisa diubah atau dihapus." />
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
            aria-label="Rentang tanggal audit"
          />
          <Select allowClear placeholder="Semua tabel" style={{ width: 170 }} value={filter.tabel} options={TABEL.map((t) => ({ value: t, label: t }))} onChange={(v) => ubah('tabel', v)} aria-label="Filter tabel" />
          <Select allowClear placeholder="Semua aksi" style={{ width: 170 }} value={filter.aksi} options={Object.keys(WARNA_AKSI).map((a) => ({ value: a, label: a }))} onChange={(v) => ubah('aksi', v)} aria-label="Filter aksi" />
          <Input.Search allowClear placeholder="Cari username, id, atau keterangan" style={{ width: 280 }} value={cari} onChange={(e) => setCari(e.target.value)} onSearch={(v) => ubah('cari', v.trim() || undefined)} aria-label="Cari audit" />
        </div>
      </Card>

      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<AuditItem>
          rowKey="audit_id"
          loading={q.isFetching}
          dataSource={q.data?.item}
          size="small"
          pagination={{ current: filter.halaman, pageSize: HALAMAN, total: q.data?.total ?? 0, showSizeChanger: false, showTotal: (t) => `${angka(t)} baris`, onChange: (h) => ubah('halaman', String(h), false) }}
          scroll={{ x: 900 }}
          expandable={{
            rowExpandable: (r) => !!(r.nilai_lama || r.nilai_baru),
            expandedRowRender: (r) => (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
                {r.nilai_lama && <div><Typography.Text type="secondary">Sebelum</Typography.Text><pre className="tabel-sel-kode" style={{ margin: 0 }}>{rapikanJson(r.nilai_lama)}</pre></div>}
                {r.nilai_baru && <div><Typography.Text type="secondary">Sesudah</Typography.Text><pre className="tabel-sel-kode" style={{ margin: 0 }}>{rapikanJson(r.nilai_baru)}</pre></div>}
              </div>
            ),
          }}
          columns={[
            { title: 'Waktu', dataIndex: 'waktu', width: 170, render: (v: string) => waktu(v) },
            { title: 'Aksi', dataIndex: 'aksi', width: 150, render: (v: string) => <Tag color={WARNA_AKSI[v] ?? 'default'}>{v}</Tag> },
            { title: 'Tabel', dataIndex: 'tabel', width: 130, className: 'tabel-sel-kode' },
            { title: 'Baris', dataIndex: 'baris_id', ellipsis: true, className: 'tabel-sel-kode', width: 170 },
            { title: 'Pengguna', key: 'user', render: (_: unknown, r) => r.username ?? (r.user_id !== null ? `#${r.user_id}` : '-') },
            { title: 'Keterangan', dataIndex: 'keterangan', ellipsis: true, render: (v: string | null) => v ?? '' },
            { title: 'IP', dataIndex: 'ip', width: 120, className: 'tabel-sel-kode', render: (v: string | null) => v ?? '' },
          ]}
        />
      </Card>
    </>
  )
}
