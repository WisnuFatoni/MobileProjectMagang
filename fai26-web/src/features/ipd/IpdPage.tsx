import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Card, DatePicker, Descriptions, Drawer, Select, Skeleton, Space, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { divisiApi, ipdApi } from '@/api/endpoints'
import type { FilterIpd, IpdBaris, IpdPokokOut } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { TombolEkspor } from '@/components/TombolEkspor'
import { desimal, tanggal, waktu } from '@/lib/format'
import { laporanHarian } from './ekspor'
import { FotoIpd } from './FotoIpd'
import { STATUS_IPD } from './ipdTampil'
import { pilihanEksporSatu } from './pilihanEkspor'
import { TombolEksporCetak } from './TombolEksporCetak'

const HALAMAN = 25

/** Daftar inspeksi panen detail (IPD). Filter hidup di URL supaya bisa dibagikan. Klik baris untuk rincian pokok dan foto. */
export function IpdPage() {
  const [params, setParams] = useSearchParams()
  const [buka, setBuka] = useState<string | null>(null)
  const divisi = useQuery({ queryKey: ['divisi'], queryFn: divisiApi.daftar })

  const filter: FilterIpd = useMemo(
    () => ({
      dari: params.get('dari') ?? undefined,
      sampai: params.get('sampai') ?? undefined,
      status: params.get('status') ?? undefined,
      divisi_kode: params.get('divisi_kode') ?? undefined,
      halaman: Number(params.get('halaman')) || 1,
      per_halaman: HALAMAN,
    }),
    [params],
  )
  const q = useQuery({ queryKey: ['ipd', filter], queryFn: () => ipdApi.daftar(filter), placeholderData: keepPreviousData })

  function ubah(kunci: string, nilai: string | undefined, reset = true) {
    const p = new URLSearchParams(params)
    if (nilai) p.set(kunci, nilai)
    else p.delete(kunci)
    if (reset) p.delete('halaman')
    setParams(p, { replace: true })
  }


  return (
    <>
      <PageHeader
        judul="Inspeksi IPD"
        keterangan="Inspeksi panen detail yang dikirim mandor dan asisten dari HP. Klik baris untuk melihat pokok yang diperiksa, temuan, dan foto bukti."
        aksi={
          <TombolEksporCetak
            divisiAwal={filter.divisi_kode}
            statusAwal={filter.status}
            susun={(detail, foto, k) =>
              laporanHarian(
                'Laporan Inspeksi IPD',
                [
                  ['Periode', k.dari === k.sampai ? tanggal(k.dari) : `${tanggal(k.dari)} sampai ${tanggal(k.sampai)}`],
                  ['Divisi', k.divisiNama ?? 'Semua divisi'],
                  ['Status', k.statusTeks ?? 'Semua status'],
                ],
                detail,
                foto,
              )
            }
          />
        }
      />
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap>
          <DatePicker.RangePicker
            value={filter.dari && filter.sampai ? [dayjs(filter.dari), dayjs(filter.sampai)] : null}
            onChange={(r) => {
              const p = new URLSearchParams(params)
              if (r?.[0] && r[1]) {
                p.set('dari', r[0].format('YYYY-MM-DD'))
                p.set('sampai', r[1].format('YYYY-MM-DD'))
              } else {
                p.delete('dari')
                p.delete('sampai')
              }
              p.delete('halaman')
              setParams(p, { replace: true })
            }}
            format="D MMM YYYY"
            aria-label="Rentang tanggal inspeksi"
          />
          <Select
            allowClear
            placeholder="Semua status"
            style={{ width: 190 }}
            value={filter.status}
            onChange={(v) => ubah('status', v)}
            options={Object.entries(STATUS_IPD).map(([k, v]) => ({ value: k, label: v.teks }))}
            aria-label="Status inspeksi"
          />
          <Select
            allowClear
            placeholder="Semua divisi"
            style={{ width: 170 }}
            value={filter.divisi_kode}
            onChange={(v) => ubah('divisi_kode', v)}
            options={(divisi.data ?? []).map((d) => ({ value: d.kode, label: d.nama }))}
            aria-label="Divisi"
          />
        </Space>
      </Card>

      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<IpdBaris>
          rowKey="id"
          loading={q.isLoading || q.isFetching}
          dataSource={q.data?.item}
          scroll={{ x: 980 }}
          onRow={(r) => ({ onClick: () => setBuka(r.id), style: { cursor: 'pointer' } })}
          pagination={{
            current: q.data?.halaman ?? 1,
            pageSize: HALAMAN,
            total: q.data?.total ?? 0,
            showSizeChanger: false,
            onChange: (h) => ubah('halaman', String(h), false),
          }}
          locale={{ emptyText: 'Belum ada inspeksi pada filter ini.' }}
          columns={[
            { title: 'Tanggal', dataIndex: 'tanggal_inspeksi', width: 120, render: (v: string) => tanggal(v) },
            {
              title: 'Pemanen',
              key: 'pemanen',
              render: (_: unknown, r) => (
                <>
                  <strong>{r.pemanen_nama ?? r.pemanen_hrmb_code}</strong>
                  <div style={{ opacity: 0.65, fontSize: 12 }}>Blok {r.blok_kode ?? '-'} • {r.divisi_kode}</div>
                </>
              ),
            },
            { title: 'Diinspeksi', dataIndex: 'inspektur_nama' },
            { title: 'Pokok', key: 'pokok', align: 'right', width: 90, render: (_: unknown, r) => r.ringkasan.pokok_diperiksa },
            {
              title: 'Loses',
              key: 'persen',
              align: 'right',
              width: 130,
              render: (_: unknown, r) => `${r.ringkasan.pokok_loses} (${desimal(r.ringkasan.persen_pokok_loses)}%)`,
            },
            {
              title: 'Temuan lain',
              key: 'lain',
              align: 'right',
              width: 130,
              render: (_: unknown, r) => `${r.ringkasan.pokok_temuan_lain} (${desimal(r.ringkasan.persen_pokok_temuan_lain)}%)`,
            },
            { title: 'Asisten', dataIndex: 'asisten_nama', render: (v: string | null) => v ?? '-' },
            {
              title: 'Status',
              dataIndex: 'status',
              width: 170,
              render: (v: IpdBaris['status']) => <Tag color={STATUS_IPD[v].warna}>{STATUS_IPD[v].teks}</Tag>,
            },
          ]}
        />
      </Card>
      <IpdDetailDrawer id={buka} onTutup={() => setBuka(null)} />
    </>
  )
}

function IpdDetailDrawer({ id, onTutup }: { id: string | null; onTutup: () => void }) {
  const q = useQuery({ queryKey: ['ipd-detail', id], queryFn: () => ipdApi.detail(id as string), enabled: !!id })
  const d = q.data
  return (
    <Drawer
      open={!!id}
      onClose={onTutup}
      width={640}
      title="Rincian inspeksi IPD"
      destroyOnClose
      extra={d && <TombolEkspor pilihan={pilihanEksporSatu(d, (detail, foto) => laporanHarian(`Inspeksi IPD ${d.pemanen_nama ?? d.pemanen_hrmb_code}`, [], detail, foto))} />}
    >
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      {q.isLoading && <Skeleton active paragraph={{ rows: 8 }} />}
      {d && (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Pemanen">{d.pemanen_nama ?? d.pemanen_hrmb_code}</Descriptions.Item>
            <Descriptions.Item label="Blok dan divisi">Blok {d.blok_kode ?? '-'} • {d.divisi_kode}{d.ancak_no ? ` • ancak ${d.ancak_no}` : ''}</Descriptions.Item>
            <Descriptions.Item label="Tanggal inspeksi">{tanggal(d.tanggal_inspeksi)}</Descriptions.Item>
            <Descriptions.Item label="Diinspeksi oleh">{d.inspektur_nama}</Descriptions.Item>
            <Descriptions.Item label="Kemandoran">{d.mandor_nama ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="Asisten penyetuju">{d.asisten_nama ?? 'Belum diatur'}</Descriptions.Item>
            <Descriptions.Item label="Status">
              <Tag color={STATUS_IPD[d.status].warna}>{STATUS_IPD[d.status].teks}</Tag>
              {d.disetujui_pada && <span style={{ marginLeft: 8, opacity: 0.7 }}>{waktu(d.disetujui_pada)}</span>}
            </Descriptions.Item>
            {d.pesan_tolak && <Descriptions.Item label="Alasan ditolak">{d.pesan_tolak}</Descriptions.Item>}
            {d.catatan && <Descriptions.Item label="Catatan">{d.catatan}</Descriptions.Item>}
          </Descriptions>

          <div>
            <Typography.Title level={5}>Ringkasan loses</Typography.Title>
            <Typography.Paragraph>
              {d.ringkasan.pokok_diperiksa} pokok diperiksa. Loses: {d.ringkasan.pokok_loses} pokok ({desimal(d.ringkasan.persen_pokok_loses)}%), jumlah{' '}
              {d.ringkasan.total_loses}. Temuan lain (pelepah): {d.ringkasan.pokok_temuan_lain} pokok ({desimal(d.ringkasan.persen_pokok_temuan_lain)}%), jumlah{' '}
              {d.ringkasan.total_temuan_lain}.
            </Typography.Paragraph>
            <Table
              size="small"
              rowKey="kode"
              pagination={false}
              dataSource={d.ringkasan.per_kode.filter((k) => k.jumlah > 0)}
              locale={{ emptyText: 'Tidak ada temuan: semua pokok bersih.' }}
              columns={[
                { title: 'Kode', dataIndex: 'kode', width: 70, render: (v: string) => <strong>{v}</strong> },
                {
                  title: 'Jenis',
                  dataIndex: 'jenis',
                  width: 110,
                  render: (v: string) => <Tag color={v === 'LOSES' ? 'orange' : 'default'}>{v === 'LOSES' ? 'Loses' : 'Temuan lain'}</Tag>,
                },
                { title: 'Temuan', dataIndex: 'nama' },
                { title: 'Pokok', dataIndex: 'pokok', align: 'right', width: 80 },
                { title: 'Jumlah', dataIndex: 'jumlah', align: 'right', width: 80 },
                { title: '% pokok', dataIndex: 'persen_pokok', align: 'right', width: 90, render: (v: number) => `${desimal(v)}%` },
              ]}
            />
          </div>

          <div>
            <Typography.Title level={5}>Pokok yang diperiksa ({d.pokok.length})</Typography.Title>
            <Table<IpdPokokOut>
              size="small"
              rowKey={(p) => `${p.no_baris}-${p.no_pokok}`}
              pagination={{ pageSize: 15, showSizeChanger: false, hideOnSinglePage: true }}
              dataSource={d.pokok}
              columns={[
                { title: 'Baris', dataIndex: 'no_baris', width: 80 },
                { title: 'Pokok', dataIndex: 'no_pokok', width: 80 },
                {
                  title: 'Temuan',
                  key: 'temuan',
                  render: (_: unknown, p) =>
                    p.temuan.length === 0 ? (
                      <Tag color="green">Bersih</Tag>
                    ) : (
                      <Space size={4} wrap>
                        {p.temuan.map((t) => (
                          <Tag key={t.kode} color="volcano">{t.kode}{t.jumlah > 1 ? ` ×${t.jumlah}` : ''}</Tag>
                        ))}
                      </Space>
                    ),
                },
              ]}
            />
          </div>

          <div>
            <Typography.Title level={5}>Foto bukti</Typography.Title>
            <FotoIpd foto={d.foto} />
          </div>
        </Space>
      )}
    </Drawer>
  )
}
