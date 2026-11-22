import { ReloadOutlined } from '@ant-design/icons'
import { useQueries, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Col, DatePicker, Empty, Row, Select, Skeleton, Space, Statistic, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { lazy, Suspense, useMemo, useState } from 'react'
import { divisiApi, ipdApi } from '@/api/endpoints'
import type { Halaman, IpdBaris } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { desimal, tanggal } from '@/lib/format'
import { keTitikPeta, teksDurasi, teksJarak } from '@/features/ipd/jalur'
import { STATUS_IPD } from '@/features/ipd/ipdTampil'
import type { JalurGabungan } from './PetaGabungan'
import { MAKS_JALUR, daftarPetugas, ringkasPerPetugas, ringkasPeta, saringPetugas, statusDari, tanggalLokal, warnaPerPetugas, type ItemPeta, type StatusJalur } from './peta'

// Peta (Leaflet) dimuat hanya saat halaman ini dibuka.
const PetaGabungan = lazy(() => import('./PetaGabungan'))

const PER_HALAMAN = 100
const MAKS_HALAMAN = 5

/** Mengambil seluruh halaman daftar harian (sampai batas pengaman). */
async function ambilSemua(ambil: (halaman: number) => Promise<Halaman<IpdBaris>>): Promise<{ item: IpdBaris[]; total: number }> {
  const hasil: IpdBaris[] = []
  let total = 0
  for (let h = 1; h <= MAKS_HALAMAN; h++) {
    const p = await ambil(h)
    total = p.total
    hasil.push(...p.item)
    if (hasil.length >= p.total || p.item.length === 0) break
  }
  return { item: hasil, total }
}

const TAMPIL_JALUR: Record<StatusJalur, { teks: string; warna: string }> = {
  ada: { teks: 'Ada jalur', warna: 'green' },
  tanpa: { teks: 'Tanpa jalur', warna: 'default' },
  memuat: { teks: 'Memuat', warna: 'blue' },
  gagal: { teks: 'Gagal dimuat', warna: 'red' },
}

const jam = (iso: string) => dayjs(iso).format('HH.mm')

function Kartu({ judul, nilai, keterangan }: { judul: string; nilai: string | number; keterangan?: string }) {
  return (
    <Card size="small" style={{ height: '100%' }}>
      <Statistic title={judul} value={nilai} />
      {keterangan && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {keterangan}
        </Typography.Text>
      )}
    </Card>
  )
}

/**
 * Peta Monitoring IPD: jalur GPS semua inspeksi pada satu hari di satu peta, tiap petugas berwarna sendiri, dapat disaring menurut
 * divisi dan petugas. Tidak memakai endpoint atau tabel baru: daftar harian dari `GET /api/admin/ipd`, lalu jalur tiap inspeksi dari
 * `GET /api/admin/ipd/{id}` yang sudah ada. Karena itu tiap jalur yang dibuka tercatat di audit (LIHAT_JALUR), dan memilih petugas
 * tertentu membatasi jalur yang dibuka.
 */
export function PetaMonitoringPage() {
  const [hari, setHari] = useState<Dayjs>(() => dayjs())
  const [divisi, setDivisi] = useState<string | undefined>()
  const [petugas, setPetugas] = useState<string[]>([])
  const tgl = tanggalLokal(hari.toDate())

  const daftarDivisi = useQuery({ queryKey: ['divisi'], queryFn: divisiApi.daftar })
  const daftar = useQuery({
    queryKey: ['peta', 'daftar', tgl, divisi ?? ''],
    queryFn: () => ambilSemua((h) => ipdApi.daftar({ dari: tgl, sampai: tgl, divisi_kode: divisi, halaman: h, per_halaman: PER_HALAMAN })),
  })

  const semua = useMemo(() => daftar.data?.item ?? [], [daftar.data])
  const warna = useMemo(() => warnaPerPetugas(semua.map((i) => i.inspektur)), [semua])
  const opsiPetugas = useMemo(() => daftarPetugas(semua), [semua])
  const disaring = useMemo(() => saringPetugas(semua, petugas), [semua, petugas])
  const dimuat = useMemo(() => disaring.slice(0, MAKS_JALUR), [disaring])
  const terpotong = disaring.length > dimuat.length

  const detail = useQueries({
    queries: dimuat.map((b) => ({ queryKey: ['peta', 'detail', b.id], queryFn: () => ipdApi.detail(b.id), staleTime: 5 * 60_000, retry: false })),
  })

  // Tanda tangan keadaan pemuatan: dipakai agar `item` dan `jalur` hanya dibangun ulang bila isinya berubah (peta tidak digambar ulang tiap render).
  const tandaTangan = dimuat.map((b, i) => `${b.id}:${statusDari(detail[i].isLoading, detail[i].isError, detail[i].data)}`).join('|')
  const item: ItemPeta[] = useMemo(
    () =>
      dimuat.map((b, i) => ({
        baris: b,
        status: statusDari(detail[i].isLoading, detail[i].isError, detail[i].data),
        detail: detail[i].data ?? null,
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [tandaTangan],
  )

  const jalur: JalurGabungan[] = useMemo(
    () =>
      item.flatMap((i) => {
        const j = i.detail?.jejak
        if (i.status !== 'ada' || !j) return []
        const t = j.titik
        return [
          {
            id: i.baris.id,
            warna: warna.get(i.baris.inspektur) ?? '#1d4ed8',
            titik: keTitikPeta(t),
            keterangan: [
              i.baris.inspektur_nama || i.baris.inspektur,
              `Pemanen: ${i.baris.pemanen_nama ?? '-'}`,
              `Blok ${i.baris.blok_kode ?? '-'} • ${i.baris.divisi_kode}`,
              `${jam(t[0].t)} sampai ${jam(t[t.length - 1].t)} • ${teksJarak(j.jarak_m)} • ${teksDurasi(j.durasi_menit)}`,
              STATUS_IPD[i.baris.status].teks,
            ],
          },
        ]
      }),
    [item, warna],
  )

  const r = ringkasPeta(item)
  const perPetugas = ringkasPerPetugas(item)
  const memuatDaftar = daftar.isPending
  const sedangMemuat = r.memuat > 0

  const ubahHari = (v: Dayjs | null) => {
    if (!v) return
    setHari(v)
    setPetugas([])
  }
  const ubahDivisi = (v: string | undefined) => {
    setDivisi(v)
    setPetugas([])
  }

  return (
    <>
      <PageHeader
        judul="Peta Monitoring IPD"
        keterangan="Jalur GPS semua inspeksi pada satu hari dalam satu peta, tiap petugas berwarna sendiri. Pilih tanggal, divisi, atau petugas."
        aksi={
          <Button icon={<ReloadOutlined />} onClick={() => void daftar.refetch()} loading={daftar.isFetching}>
            Muat ulang
          </Button>
        }
      />

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="Ini data lokasi petugas"
        description="Setiap jalur yang dibuka di peta ini tercatat di menu Audit (aksi LIHAT_JALUR) beserta siapa yang membukanya. Pilih petugas tertentu bila hanya perlu melihat sebagian; hanya jalur yang dipilih yang dibuka."
      />

      <Space wrap style={{ marginBottom: 12 }}>
        <DatePicker value={hari} onChange={ubahHari} allowClear={false} format="D MMM YYYY" disabledDate={(d) => d.isAfter(dayjs(), 'day')} aria-label="Tanggal inspeksi" />
        <Select
          allowClear
          placeholder="Semua divisi"
          style={{ minWidth: 190 }}
          value={divisi}
          onChange={ubahDivisi}
          options={(daftarDivisi.data ?? []).map((d) => ({ value: d.kode, label: d.nama }))}
          aria-label="Divisi"
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="Semua petugas"
          style={{ minWidth: 260 }}
          value={petugas}
          onChange={setPetugas}
          options={opsiPetugas.map((p) => ({ value: p.username, label: p.nama }))}
          maxTagCount="responsive"
          aria-label="Petugas"
        />
      </Space>

      {daftar.isError ? (
        <ErrorState error={daftar.error} onCoba={() => void daftar.refetch()} />
      ) : memuatDaftar ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          {terpotong && (
            <Alert
              type="warning"
              showIcon
              message={`Hanya ${MAKS_JALUR} dari ${disaring.length} inspeksi yang jalurnya dimuat`}
              description="Pilih divisi atau petugas tertentu untuk mempersempit."
            />
          )}
          {daftar.data && daftar.data.total > daftar.data.item.length && (
            <Alert type="warning" showIcon message="Daftar harian dipotong" description={`Hanya ${daftar.data.item.length} dari ${daftar.data.total} inspeksi hari ini yang diambil.`} />
          )}

          <Row gutter={[12, 12]}>
            <Col xs={12} md={6} lg={4}><Kartu judul="Inspeksi" nilai={r.inspeksi} keterangan={tanggal(tgl)} /></Col>
            <Col xs={12} md={6} lg={5}><Kartu judul="Ada jalur" nilai={r.denganJalur} keterangan={r.tanpaJalur > 0 ? `${r.tanpaJalur} tanpa jalur` : undefined} /></Col>
            <Col xs={12} md={6} lg={5}><Kartu judul="Petugas" nilai={r.petugas} /></Col>
            <Col xs={12} md={6} lg={5}><Kartu judul="Total jarak" nilai={teksJarak(r.jarakM)} /></Col>
            {(r.memuat > 0 || r.gagal > 0) && (
              <Col xs={24} lg={5}>
                <Kartu judul="Pemuatan jalur" nilai={r.memuat > 0 ? `${r.memuat} memuat` : 'selesai'} keterangan={r.gagal > 0 ? `${r.gagal} gagal dimuat` : undefined} />
              </Col>
            )}
          </Row>

          {perPetugas.length > 0 && (
            <Space wrap size={[8, 8]}>
              {perPetugas.map((p) => (
                <Tag key={p.username} color={warna.get(p.username)} style={{ padding: '2px 8px', fontSize: 13 }}>
                  {p.nama}: {p.inspeksi} inspeksi{p.denganJalur > 0 ? ` • ${teksJarak(p.jarakM)}` : ''}
                </Tag>
              ))}
            </Space>
          )}

          {semua.length === 0 ? (
            <Empty description={`Tidak ada inspeksi IPD pada ${tanggal(tgl)}${divisi ? ' untuk divisi ini' : ''}.`} />
          ) : jalur.length > 0 ? (
            <Suspense fallback={<Skeleton active paragraph={{ rows: 10 }} />}>
              <PetaGabungan jalur={jalur} tinggi="min(620px, 70vh)" label={`Peta jalur ${jalur.length} inspeksi pada ${tanggal(tgl)}`} />
            </Suspense>
          ) : sedangMemuat ? (
            <Skeleton active paragraph={{ rows: 10 }} />
          ) : (
            <Empty description="Tidak ada jalur GPS pada inspeksi yang dipilih: HP tidak mengirim jalur, akun ini tidak berhak melihat jalur, atau jalurnya sudah dihapus sesuai kebijakan penyimpanan." />
          )}

          {item.length > 0 && (
            <Table<ItemPeta>
              size="small"
              rowKey={(i) => i.baris.id}
              dataSource={item}
              pagination={false}
              scroll={{ x: 'max-content' }}
              columns={[
                {
                  title: 'Petugas',
                  render: (_, i) => (
                    <Space size={6}>
                      <span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 5, background: warna.get(i.baris.inspektur) }} aria-hidden />
                      {i.baris.inspektur_nama || i.baris.inspektur}
                    </Space>
                  ),
                },
                { title: 'Pemanen', render: (_, i) => i.baris.pemanen_nama ?? '-' },
                { title: 'Blok', render: (_, i) => `${i.baris.blok_kode ?? '-'} • ${i.baris.divisi_kode}` },
                { title: 'Jalur', render: (_, i) => <Tag color={TAMPIL_JALUR[i.status].warna}>{TAMPIL_JALUR[i.status].teks}</Tag> },
                { title: 'Waktu', render: (_, i) => (i.detail?.jejak ? `${jam(i.detail.jejak.titik[0].t)} sampai ${jam(i.detail.jejak.titik[i.detail.jejak.titik.length - 1].t)}` : '-') },
                { title: 'Jarak', align: 'right', render: (_, i) => (i.detail?.jejak ? `${desimal(i.detail.jejak.jarak_m)} m` : '-') },
                { title: 'Status', render: (_, i) => <Tag color={STATUS_IPD[i.baris.status].warna}>{STATUS_IPD[i.baris.status].teks}</Tag> },
              ]}
            />
          )}
        </Space>
      )}
    </>
  )
}
