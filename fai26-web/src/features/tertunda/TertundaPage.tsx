import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Row, Select, Skeleton, Space, Statistic, Table, Tag, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ipdApi, laporanApi } from '@/api/endpoints'
import type { Halaman, IpdBaris, PanenRingkas } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { angka, tanggal, waktu } from '@/lib/format'
import { IZIN } from '@/lib/izin'
import {
  BATAS_BAWAAN_JAM,
  PILIHAN_BATAS_JAM,
  gabungRingkasan,
  hitungPerNama,
  lamaMenunggu,
  ringkasTunggu,
  teksLama,
  tingkatMenunggu,
  urutkanTerlama,
  type Tingkat,
} from './tertunda'

const PER_HALAMAN = 100
const MAKS_HALAMAN = 5
const SEGARKAN_MS = 60_000

/** Waktu sekarang yang diperbarui berkala, supaya lama menunggu ikut bertambah tanpa memuat ulang data. */
function useSekarang(intervalMs: number): number {
  const [n, setN] = useState(() => Date.now())
  useEffect(() => {
    const t = setInterval(() => setN(Date.now()), intervalMs)
    return () => clearInterval(t)
  }, [intervalMs])
  return n
}

/** Mengambil seluruh halaman (sampai batas pengaman). Daftar server terurut terbaru dulu, jadi diurutkan ulang di sini. */
async function ambilSemua<T>(ambil: (halaman: number) => Promise<Halaman<T>>): Promise<{ item: T[]; total: number }> {
  const hasil: T[] = []
  let total = 0
  for (let h = 1; h <= MAKS_HALAMAN; h++) {
    const p = await ambil(h)
    total = p.total
    hasil.push(...p.item)
    if (hasil.length >= p.total || p.item.length === 0) break
  }
  return { item: hasil, total }
}

const TAMPIL: Record<Tingkat, { teks: string; warna: string }> = {
  normal: { teks: 'Wajar', warna: 'green' },
  mendekati: { teks: 'Mendekati batas', warna: 'orange' },
  terlambat: { teks: 'Terlambat', warna: 'red' },
}

function Menunggu({ sejak, batasJam, sekarang }: { sejak: string; batasJam: number; sekarang: number }) {
  const lama = lamaMenunggu(sejak, sekarang)
  const t = TAMPIL[tingkatMenunggu(lama, batasJam)]
  return (
    <Space size={8} wrap>
      <strong>{teksLama(lama)}</strong>
      <Tag color={t.warna}>{t.teks}</Tag>
    </Space>
  )
}

function Kartu({ judul, nilai, keterangan, warna }: { judul: string; nilai: string | number; keterangan?: string; warna?: string }) {
  return (
    <Card size="small" style={{ height: '100%' }}>
      <Statistic title={judul} value={nilai} valueStyle={warna ? { color: warna } : undefined} />
      {keterangan && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {keterangan}
        </Typography.Text>
      )}
    </Card>
  )
}

/**
 * Dasbor Persetujuan Tertunda: catatan panen yang menunggu krani dan inspeksi IPD yang menunggu asisten, diurutkan dari yang paling
 * lama menunggu, dengan penanda keterlambatan. Tidak memakai endpoint atau tabel baru: daftar diambil dari endpoint admin yang
 * sudah ada dengan filter status menunggu, lalu lama menunggu dihitung di sini. Diperbarui otomatis tiap 1 menit.
 */
export function TertundaPage() {
  const { bisa } = useAuth()
  const lihatPanen = bisa(IZIN.panenLihat)
  const lihatIpd = bisa(IZIN.ipdLihat)
  const [batasJam, setBatasJam] = useState<number>(BATAS_BAWAAN_JAM)
  const sekarang = useSekarang(30_000)

  const panen = useQuery({
    queryKey: ['tertunda', 'panen'],
    queryFn: () => ambilSemua<PanenRingkas>((h) => laporanApi.panen({ status_erp: 'MENUNGGU', halaman: h, per_halaman: PER_HALAMAN })),
    enabled: lihatPanen,
    refetchInterval: SEGARKAN_MS,
  })
  const ipd = useQuery({
    queryKey: ['tertunda', 'ipd'],
    queryFn: () => ambilSemua<IpdBaris>((h) => ipdApi.daftar({ status: 'MENUNGGU', halaman: h, per_halaman: PER_HALAMAN })),
    enabled: lihatIpd,
    refetchInterval: SEGARKAN_MS,
  })

  const barisPanen = useMemo(() => urutkanTerlama((panen.data?.item ?? []).map((p) => ({ ...p, sejak: p.diterima_server }))), [panen.data])
  const barisIpd = useMemo(() => urutkanTerlama((ipd.data?.item ?? []).map((i) => ({ ...i, sejak: i.dibuat_pada }))), [ipd.data])
  const perAsisten = useMemo(() => hitungPerNama(barisIpd.map((i) => i.asisten_nama)), [barisIpd])

  const rPanen = ringkasTunggu(barisPanen, batasJam, sekarang)
  const rIpd = ringkasTunggu(barisIpd, batasJam, sekarang)
  const r = gabungRingkasan(rPanen, rIpd)

  const memuat = (lihatPanen && panen.isPending) || (lihatIpd && ipd.isPending)
  const terpotong = (panen.data && panen.data.total > panen.data.item.length) || (ipd.data && ipd.data.total > ipd.data.item.length)
  const diperbarui = Math.max(panen.dataUpdatedAt, ipd.dataUpdatedAt)
  const segarkan = () => {
    void panen.refetch()
    void ipd.refetch()
  }

  return (
    <>
      <PageHeader
        judul="Persetujuan Tertunda"
        keterangan="Catatan panen yang menunggu krani dan inspeksi IPD yang menunggu asisten, dari yang paling lama menunggu. Diperbarui otomatis tiap 1 menit."
        aksi={
          <Space wrap>
            <Typography.Text type="secondary">Batas wajar</Typography.Text>
            <Select
              value={batasJam}
              onChange={setBatasJam}
              style={{ width: 110 }}
              options={PILIHAN_BATAS_JAM.map((j) => ({ value: j, label: `${j} jam` }))}
              aria-label="Batas wajar menunggu"
            />
            <Button icon={<ReloadOutlined />} onClick={segarkan} loading={panen.isFetching || ipd.isFetching}>
              Muat ulang
            </Button>
          </Space>
        }
      />

      {memuat ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}>
              <Kartu judul="Menunggu persetujuan" nilai={angka(r.jumlah)} keterangan={`${angka(rPanen.jumlah)} panen • ${angka(rIpd.jumlah)} IPD`} />
            </Col>
            <Col xs={12} md={6}>
              <Kartu
                judul="Terlambat"
                nilai={angka(r.terlambat)}
                keterangan={`menunggu lebih dari ${batasJam} jam`}
                warna={r.terlambat > 0 ? '#b91c1c' : undefined}
              />
            </Col>
            <Col xs={12} md={6}>
              <Kartu judul="Mendekati batas" nilai={angka(r.mendekati)} keterangan="75% sampai 100% dari batas" warna={r.mendekati > 0 ? '#d97706' : undefined} />
            </Col>
            <Col xs={12} md={6}>
              <Kartu judul="Terlama menunggu" nilai={r.jumlah === 0 ? '-' : teksLama(r.terlamaMs)} keterangan={r.jumlah === 0 ? undefined : `rata-rata ${teksLama(r.rataMs)}`} />
            </Col>
          </Row>

          {terpotong && (
            <Alert
              type="warning"
              showIcon
              message="Daftar dipotong"
              description={`Hanya ${MAKS_HALAMAN * PER_HALAMAN} catatan per jenis yang dimuat. Periksa daftar lengkap di halaman Catatan Panen atau Inspeksi IPD.`}
            />
          )}

          {lihatPanen && (
            <Card
              title="Catatan panen menunggu krani"
              extra={<Link to="/panen?status_erp=MENUNGGU">Buka daftar</Link>}
            >
              {panen.isError ? (
                <ErrorState error={panen.error} onCoba={() => void panen.refetch()} />
              ) : (
                <Table
                  rowKey="id"
                  size="middle"
                  pagination={{ pageSize: 10, showSizeChanger: false, hideOnSinglePage: true }}
                  dataSource={barisPanen}
                  locale={{ emptyText: 'Tidak ada catatan yang menunggu. Semua sudah diperiksa krani.' }}
                  scroll={{ x: 760 }}
                  columns={[
                    { title: 'Menunggu', key: 'lama', width: 230, render: (_: unknown, p) => <Menunggu sejak={p.sejak} batasJam={batasJam} sekarang={sekarang} /> },
                    { title: 'Tanggal panen', dataIndex: 'tanggal_panen', width: 130, render: (v: string) => tanggal(v) },
                    { title: 'Blok / TPH', key: 'lokasi', render: (_: unknown, p) => <strong>{p.blok_kode ?? '-'} / {p.tph_kode ?? '-'}</strong> },
                    { title: 'Janjang', dataIndex: 'total_janjang', align: 'right', width: 90, render: (v: number) => angka(v) },
                    { title: 'Pencatat', dataIndex: 'pencatat', render: (v: string | null) => v ?? '-' },
                    { title: 'Diterima server', dataIndex: 'diterima_server', width: 160, render: (v: string) => waktu(v) },
                  ]}
                />
              )}
            </Card>
          )}

          {lihatIpd && (
            <Card title="Inspeksi IPD menunggu asisten" extra={<Link to="/ipd">Buka daftar</Link>}>
              {ipd.isError ? (
                <ErrorState error={ipd.error} onCoba={() => void ipd.refetch()} />
              ) : (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {perAsisten.length > 0 && (
                    <Space size={[8, 8]} wrap>
                      <Typography.Text type="secondary">Per asisten:</Typography.Text>
                      {perAsisten.map((a) => (
                        <Tag key={a.nama}>
                          {a.nama}: {a.jumlah}
                        </Tag>
                      ))}
                    </Space>
                  )}
                  <Table
                    rowKey="id"
                    size="middle"
                    pagination={{ pageSize: 10, showSizeChanger: false, hideOnSinglePage: true }}
                    dataSource={barisIpd}
                    locale={{ emptyText: 'Tidak ada inspeksi yang menunggu. Semua sudah diperiksa asisten.' }}
                    scroll={{ x: 860 }}
                    columns={[
                      { title: 'Menunggu', key: 'lama', width: 230, render: (_: unknown, i) => <Menunggu sejak={i.sejak} batasJam={batasJam} sekarang={sekarang} /> },
                      { title: 'Tanggal inspeksi', dataIndex: 'tanggal_inspeksi', width: 140, render: (v: string) => tanggal(v) },
                      {
                        title: 'Pemanen',
                        key: 'pemanen',
                        render: (_: unknown, i) => (
                          <div>
                            <strong>{i.pemanen_nama ?? '-'}</strong>
                            <div style={{ fontSize: 12, opacity: 0.7 }}>Blok {i.blok_kode ?? '-'} • {i.divisi_kode}</div>
                          </div>
                        ),
                      },
                      { title: 'Pokok', key: 'pokok', align: 'right', width: 80, render: (_: unknown, i) => angka(i.ringkasan.pokok_diperiksa) },
                      { title: 'Diinspeksi oleh', dataIndex: 'inspektur_nama', render: (v: string) => v || '-' },
                      { title: 'Asisten penyetuju', dataIndex: 'asisten_nama', render: (v: string | null) => v ?? 'Belum ditentukan' },
                    ]}
                  />
                </Space>
              )}
            </Card>
          )}

          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Lama menunggu dihitung sejak catatan diterima server (panen) atau inspeksi dibuat (IPD), dan bertambah sendiri tiap 30 detik.
            {diperbarui > 0 && ` Data terakhir dimuat ${waktu(new Date(diperbarui).toISOString())}.`}
          </Typography.Text>
        </Space>
      )}
    </>
  )
}
