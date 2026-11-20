import { CopyOutlined, DeleteOutlined, EditOutlined, EnvironmentOutlined, PlusOutlined, PrinterOutlined, QrcodeOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, App, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Tooltip, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { blokApi, tphApi } from '@/api/endpoints'
import type { IdDibuat, OkQr, Tph, TphInput, TphMassalInput } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { QrSvg } from '@/features/qr/QrSvg'
import { desimal } from '@/lib/format'
import { IZIN } from '@/lib/izin'
import { pratinjauMassal } from './massal'
import { useAksi } from '@/lib/useAksi'

type Edit = Tph | 'baru' | null

export function TphPage() {
  const { bisa } = useAuth()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const kelola = bisa(IZIN.masterKelola)
  const [params, setParams] = useSearchParams()
  const blokId = Number(params.get('blok')) || undefined

  const blok = useQuery({ queryKey: ['blok'], queryFn: blokApi.daftar })
  const q = useQuery({ queryKey: ['tph', blokId ?? 'semua'], queryFn: () => tphApi.daftar(blokId) })
  const [cari, setCari] = useState('')
  const [pilih, setPilih] = useState<number[]>([])
  const [edit, setEdit] = useState<Edit>(null)
  const [massal, setMassal] = useState(false)
  const [lihatQr, setLihatQr] = useState<Tph | null>(null)
  const [form] = Form.useForm<TphInput>()
  const [formMassal] = Form.useForm<TphMassalInput>()
  const nilaiMassal = Form.useWatch([], formMassal)
  const noBaru = Form.useWatch('no_tph', form)
  const blokBaru = Form.useWatch('blok_id', form)

  const data = useMemo(() => {
    const k = cari.trim().toLowerCase()
    return (q.data ?? []).filter((t) => !k || t.no_tph.toLowerCase().includes(k) || t.qr_kode.toLowerCase().includes(k))
  }, [q.data, cari])

  const segarkan = [['tph'], ['blok'], ['ringkasan']]
  const simpan = useAksi<IdDibuat | OkQr, TphInput>({
    fn: (v) => (edit && edit !== 'baru' ? tphApi.ubah(edit.tph_id, v) : tphApi.buat(v)),
    segarkan,
    sukses: (h) => ('qr_berubah' in h && h.qr_berubah === true ? 'TPH tersimpan. QR berubah: cetak ulang.' : 'TPH tersimpan'),
    onSukses: () => setEdit(null),
  })
  const buatMassal = useAksi({
    fn: (v: TphMassalInput) => tphApi.massal(v),
    segarkan,
    sukses: (h) => `${h.dibuat} TPH dibuat`,
    onSukses: () => setMassal(false),
  })
  const hapus = useAksi({ fn: (id: number) => tphApi.hapus(id), segarkan, sukses: 'TPH dihapus' })

  function buka(d: Edit) {
    setEdit(d)
    form.resetFields()
    if (d && d !== 'baru') form.setFieldsValue({ blok_id: d.blok_id, no_tph: d.no_tph, lat: d.lat, lon: d.lon, aktif: d.aktif })
    else form.setFieldsValue({ aktif: true, blok_id: blokId })
  }

  const qrBerubah = edit && edit !== 'baru' && ((noBaru !== undefined && noBaru !== edit.no_tph) || (blokBaru !== undefined && blokBaru !== edit.blok_id))

  function cetak() {
    if (pilih.length > 0) navigate(`/tph/cetak?ids=${pilih.join(',')}${blokId ? `&blok=${blokId}` : ''}`)
    else navigate(`/tph/cetak${blokId ? `?blok=${blokId}` : ''}`)
  }

  const opsiBlok = (blok.data ?? []).map((b) => ({ value: b.blok_id, label: `Blok ${b.kode}${b.nama ? ` · ${b.nama}` : ''}` }))

  return (
    <>
      <PageHeader
        judul="TPH dan QR"
        keterangan="Tempat pengumpulan hasil. Isi QR dibuat otomatis dari unit, blok, dan nomor TPH, lalu HP mengenalinya dari master."
        aksi={
          <>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Semua blok"
              style={{ width: 200 }}
              value={blokId}
              options={opsiBlok}
              onChange={(v) => {
                setPilih([])
                setParams(v ? { blok: String(v) } : {})
              }}
              aria-label="Filter blok"
            />
            <Input allowClear placeholder="Cari nomor" value={cari} onChange={(e) => setCari(e.target.value)} style={{ width: 150 }} aria-label="Cari TPH" />
            <Button icon={<PrinterOutlined />} onClick={cetak}>
              {pilih.length > 0 ? `Cetak ${pilih.length} QR terpilih` : 'Cetak QR'}
            </Button>
            {kelola && (
              <>
                <Button icon={<ThunderboltOutlined />} onClick={() => { formMassal.resetFields(); formMassal.setFieldsValue({ blok_id: blokId, dari: 1, sampai: 10, lebar: 2 }); setMassal(true) }}>
                  Buat berurutan
                </Button>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => buka('baru')}>Tambah TPH</Button>
              </>
            )}
          </>
        }
      />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<Tph>
          rowKey="tph_id"
          loading={q.isLoading}
          dataSource={data}
          rowSelection={{ selectedRowKeys: pilih, onChange: (k) => setPilih(k as number[]), getCheckboxProps: (t) => ({ disabled: !t.aktif }) }}
          pagination={{ pageSize: 50, hideOnSinglePage: true, showTotal: (t) => `${t} TPH` }}
          scroll={{ x: 860 }}
          columns={[
            { title: 'Blok', dataIndex: 'blok_kode', sorter: (a, b) => a.blok_kode.localeCompare(b.blok_kode), render: (v: string) => <strong>{v}</strong> },
            { title: 'No. TPH', dataIndex: 'no_tph', sorter: (a, b) => a.no_tph.localeCompare(b.no_tph, undefined, { numeric: true }) },
            {
              title: 'Isi QR',
              dataIndex: 'qr_kode',
              render: (v: string) => (
                <Space size={4}>
                  <span className="tabel-sel-kode">{v}</span>
                  <Tooltip title="Salin">
                    <Button type="text" size="small" icon={<CopyOutlined />} aria-label="Salin isi QR" onClick={() => void navigator.clipboard?.writeText(v).then(() => message.success('Isi QR disalin'))} />
                  </Tooltip>
                </Space>
              ),
            },
            {
              title: 'Koordinat',
              key: 'koordinat',
              render: (_: unknown, t) =>
                t.lat !== null && t.lon !== null ? (
                  <a href={`https://www.openstreetmap.org/?mlat=${t.lat}&mlon=${t.lon}#map=18/${t.lat}/${t.lon}`} target="_blank" rel="noreferrer">
                    <EnvironmentOutlined /> {desimal(t.lat, 5)}, {desimal(t.lon, 5)}
                  </a>
                ) : (
                  <Tag>Belum ada</Tag>
                ),
            },
            { title: 'Status', dataIndex: 'aktif', render: (v: boolean) => (v ? <Tag color="green">Aktif</Tag> : <Tag>Nonaktif</Tag>) },
            {
              title: 'Aksi',
              key: 'aksi',
              width: 150,
              fixed: 'right' as const,
              render: (_: unknown, d: Tph) => (
                <Space>
                  <Tooltip title="Lihat QR">
                    <Button size="small" icon={<QrcodeOutlined />} onClick={() => setLihatQr(d)} aria-label={`Lihat QR ${d.blok_kode} ${d.no_tph}`} />
                  </Tooltip>
                  {kelola && (
                    <>
                      <Button size="small" icon={<EditOutlined />} onClick={() => buka(d)} aria-label={`Ubah TPH ${d.blok_kode} ${d.no_tph}`} />
                      <Popconfirm title={`Hapus TPH ${d.blok_kode}/${d.no_tph}?`} description="QR-nya tidak berlaku lagi." okText="Hapus" okButtonProps={{ danger: true }} cancelText="Batal" onConfirm={() => hapus.mutate(d.tph_id)}>
                        <Button size="small" danger icon={<DeleteOutlined />} aria-label={`Hapus TPH ${d.blok_kode} ${d.no_tph}`} />
                      </Popconfirm>
                    </>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal open={lihatQr !== null} title={lihatQr ? `QR Blok ${lihatQr.blok_kode} / TPH ${lihatQr.no_tph}` : ''} footer={<Button onClick={() => setLihatQr(null)}>Tutup</Button>} onCancel={() => setLihatQr(null)} width={420}>
        {lihatQr && (
          <div style={{ textAlign: 'center' }}>
            <QrSvg teks={lihatQr.qr_kode} style={{ width: '100%', maxWidth: 320, border: '1px solid #e5e7eb' }} />
            <Typography.Paragraph copyable className="tabel-sel-kode" style={{ marginTop: 12 }}>{lihatQr.qr_kode}</Typography.Paragraph>
            <Link to={`/tph/cetak?ids=${lihatQr.tph_id}`}><Button icon={<PrinterOutlined />}>Cetak QR ini</Button></Link>
          </div>
        )}
      </Modal>

      <Modal
        open={edit !== null}
        title={edit === 'baru' ? 'Tambah TPH' : 'Ubah TPH'}
        okText="Simpan"
        cancelText="Batal"
        confirmLoading={simpan.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEdit(null)}
        destroyOnClose
        forceRender
      >
        {qrBerubah && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="QR akan berubah" description="Mengganti blok atau nomor membuat QR baru. QR lama yang sudah dipasang tidak berlaku lagi." />}
        <Form<TphInput> form={form} layout="vertical" onFinish={(v) => simpan.mutate(v)} requiredMark="optional">
          <Form.Item name="blok_id" label="Blok" rules={[{ required: true, message: 'Pilih blok' }]}>
            <Select showSearch optionFilterProp="label" placeholder="Pilih blok" options={opsiBlok} />
          </Form.Item>
          <Form.Item name="no_tph" label="Nomor TPH" rules={[{ required: true, whitespace: true, message: 'Isi nomor' }, { max: 10, message: 'Maksimal 10 karakter' }, { pattern: /^[^|]*$/, message: 'Tidak boleh berisi |' }]} extra="Tulis dengan angka nol di depan secara konsisten, contoh 07.">
            <Input placeholder="contoh: 07" />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }} align="start">
            <Form.Item style={{ flex: 1 }} name="lat" label="Lintang (lat)" dependencies={['lon']} rules={[({ getFieldValue }) => ({ validator: (_, v) => (v === null || v === undefined) === (getFieldValue('lon') === null || getFieldValue('lon') === undefined) ? Promise.resolve() : Promise.reject(new Error('Isi lat dan lon bersamaan')) })]}>
              <InputNumber style={{ width: '100%' }} min={-90} max={90} step={0.00001} />
            </Form.Item>
            <Form.Item style={{ flex: 1 }} name="lon" label="Bujur (lon)" dependencies={['lat']}>
              <InputNumber style={{ width: '100%' }} min={-180} max={180} step={0.00001} />
            </Form.Item>
          </Space>
          <Form.Item name="aktif" label="Aktif" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={massal}
        title="Buat TPH berurutan"
        okText="Buat"
        cancelText="Batal"
        confirmLoading={buatMassal.isPending}
        onOk={() => formMassal.submit()}
        onCancel={() => setMassal(false)}
        destroyOnClose
        forceRender
      >
        <Form<TphMassalInput> form={formMassal} layout="vertical" onFinish={(v) => buatMassal.mutate(v)} requiredMark="optional">
          <Form.Item name="blok_id" label="Blok" rules={[{ required: true, message: 'Pilih blok' }]}>
            <Select showSearch optionFilterProp="label" placeholder="Pilih blok" options={opsiBlok} />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }} align="start">
            <Form.Item style={{ flex: 1 }} name="dari" label="Dari nomor" rules={[{ required: true, message: 'Isi' }]}>
              <InputNumber style={{ width: '100%' }} min={1} />
            </Form.Item>
            <Form.Item style={{ flex: 1 }} name="sampai" label="Sampai nomor" rules={[{ required: true, message: 'Isi' }]}>
              <InputNumber style={{ width: '100%' }} min={1} max={9999} />
            </Form.Item>
            <Form.Item style={{ flex: 1 }} name="lebar" label="Lebar angka" rules={[{ required: true, message: 'Isi' }]}>
              <InputNumber style={{ width: '100%' }} min={1} max={4} />
            </Form.Item>
          </Space>
          <Typography.Text type="secondary">{pratinjauMassal(nilaiMassal?.dari, nilaiMassal?.sampai, nilaiMassal?.lebar) || 'Isi rentang untuk melihat pratinjau.'}</Typography.Text>
          <Typography.Paragraph type="secondary" style={{ marginTop: 8, fontSize: 12 }}>
            Semua atau tidak sama sekali: bila satu nomor sudah ada, tidak ada TPH yang dibuat. Maksimal 500 sekali buat.
          </Typography.Paragraph>
        </Form>
      </Modal>
    </>
  )
}
