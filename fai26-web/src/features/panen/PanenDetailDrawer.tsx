import { EnvironmentOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Descriptions, Drawer, Skeleton, Table, Timeline, Typography } from 'antd'
import { laporanApi } from '@/api/endpoints'
import type { Grading } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { StatusErpTag, StatusLokasiTag } from '@/components/StatusTag'
import { NAMA_KELAS, angka, desimal, tanggal, waktu } from '@/lib/format'
import { FotoGaleri } from './FotoGaleri'

export function PanenDetailDrawer({ id, onClose }: { id: string | null; onClose: () => void }) {
  const q = useQuery({ queryKey: ['panen', 'detail', id], queryFn: () => laporanApi.detailPanen(id!), enabled: !!id })
  const d = q.data

  return (
    <Drawer open={!!id} onClose={onClose} width={640} title="Detail catatan panen" destroyOnClose>
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      {q.isLoading && <Skeleton active paragraph={{ rows: 10 }} />}
      {d && (
        <>
          {/* Jumlah kolom tetap 2: lebar laci tetap, bukan lebar layar (kolom responsif membuat sel sempit dan teks tertumpuk huruf per huruf). */}
          <Descriptions column={2} size="small" bordered labelStyle={{ width: 120 }} contentStyle={{ minWidth: 120 }}>
            <Descriptions.Item label="Tanggal panen">{tanggal(d.tanggal_panen)}</Descriptions.Item>
            <Descriptions.Item label="Blok / TPH">{d.blok_kode ?? '-'} / {d.tph_kode ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="Total janjang"><strong>{angka(d.total_janjang)}</strong></Descriptions.Item>
            <Descriptions.Item label="Brondolan">{desimal(d.brondolan_kg)} kg</Descriptions.Item>
            <Descriptions.Item label="Status ERP"><StatusErpTag status={d.status_erp} /></Descriptions.Item>
            <Descriptions.Item label="Lokasi"><StatusLokasiTag status={d.status_lokasi} mock={d.gps_mock} /></Descriptions.Item>
            <Descriptions.Item label="Pencatat">{d.pencatat ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="Disetujui">{d.penyetuju ?? '-'}{d.disetujui_pada ? ` · ${waktu(d.disetujui_pada)}` : ''}</Descriptions.Item>
            <Descriptions.Item label="Dicatat di HP">{waktu(d.dibuat_pada)}</Descriptions.Item>
            <Descriptions.Item label="Diterima server">{waktu(d.diterima_server)}</Descriptions.Item>
            <Descriptions.Item label="OID blok ERP">{d.asm3_oid}</Descriptions.Item>
            <Descriptions.Item label="Model AI">{d.model_versi ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="GPS" span={2}>
              {d.gps_lat !== null && d.gps_lon !== null ? (
                <>
                  <a href={`https://www.openstreetmap.org/?mlat=${d.gps_lat}&mlon=${d.gps_lon}#map=18/${d.gps_lat}/${d.gps_lon}`} target="_blank" rel="noreferrer">
                    <EnvironmentOutlined /> {desimal(d.gps_lat, 5)}, {desimal(d.gps_lon, 5)}
                  </a>
                  {d.gps_akurasi_m !== null && ` · akurasi ±${desimal(d.gps_akurasi_m, 0)} m`}
                  {d.jarak_ke_tph_m !== null && ` · ${desimal(d.jarak_ke_tph_m, 0)} m dari TPH`}
                </>
              ) : (
                'Tidak ada titik GPS'
              )}
            </Descriptions.Item>
            {d.catatan && <Descriptions.Item label="Catatan" span={2}>{d.catatan}</Descriptions.Item>}
          </Descriptions>

          <Typography.Title level={5} style={{ marginTop: 24 }}>Hasil grading</Typography.Title>
          <Table<Grading>
            size="small"
            rowKey="kelas_kode"
            pagination={false}
            dataSource={d.grading}
            columns={[
              { title: 'Kelas', dataIndex: 'kelas_kode', render: (v: string) => NAMA_KELAS[v] ?? v },
              { title: 'Kolom ERP', dataIndex: 'erp_kolom', render: (v: string | null) => v ?? <Typography.Text type="secondary">belum ada</Typography.Text>, className: 'tabel-sel-kode' },
              { title: 'Kamera', dataIndex: 'jml_ai', align: 'right' },
              { title: 'Akhir', dataIndex: 'jml_final', align: 'right', render: (v: number, g) => <strong>{v}{g.diubah_manual ? ' *' : ''}</strong> },
            ]}
            summary={(rows) => (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={2}><strong>Total</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">{rows.reduce((n, r) => n + r.jml_ai, 0)}</Table.Summary.Cell>
                <Table.Summary.Cell index={2} align="right"><strong>{rows.reduce((n, r) => n + r.jml_final, 0)}</strong></Table.Summary.Cell>
              </Table.Summary.Row>
            )}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>* dikoreksi krani dari hitungan kamera</Typography.Text>

          <Typography.Title level={5} style={{ marginTop: 24 }}>Pemanen</Typography.Title>
          {d.pemanen.length === 0 ? (
            <Typography.Text type="secondary">Tidak dipilih.</Typography.Text>
          ) : (
            <Table size="small" rowKey={(p) => String(p.hrmb_code)} pagination={false} dataSource={d.pemanen} columns={[
              { title: 'Nama', dataIndex: 'nama', render: (v: string | null) => v ?? <Typography.Text type="secondary">tidak ada di master</Typography.Text> },
              { title: 'Kode ERP', dataIndex: 'hrmb_code', className: 'tabel-sel-kode' },
              { title: 'Janjang', dataIndex: 'jjg_alokasi', align: 'right' },
            ]} />
          )}

          <Typography.Title level={5} style={{ marginTop: 24 }}>Foto bukti</Typography.Title>
          <FotoGaleri foto={d.foto} />

          {d.impor_log.length > 0 && (
            <>
              <Typography.Title level={5} style={{ marginTop: 24 }}>Riwayat impor ERP</Typography.Title>
              <Timeline
                items={d.impor_log.map((l) => ({
                  color: l.hasil === 'OK' ? 'green' : 'red',
                  children: (
                    <>
                      <strong>{l.hasil === 'OK' ? 'Berhasil' : 'Gagal'}</strong> · {waktu(l.waktu_impor)}
                      {l.pesan_error && <div>{l.pesan_error}</div>}
                    </>
                  ),
                }))}
              />
            </>
          )}
        </>
      )}
    </Drawer>
  )
}
