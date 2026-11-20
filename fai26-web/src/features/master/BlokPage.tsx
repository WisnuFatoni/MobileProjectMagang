import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag } from 'antd'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { blokApi, divisiApi } from '@/api/endpoints'
import type { Blok, BlokInput, IdDibuat, OkQr } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { angka, desimal } from '@/lib/format'
import { IZIN } from '@/lib/izin'
import { useAksi } from '@/lib/useAksi'

type Edit = Blok | 'baru' | null

export function BlokPage() {
  const { bisa } = useAuth()
  const kelola = bisa(IZIN.masterKelola)
  const q = useQuery({ queryKey: ['blok'], queryFn: blokApi.daftar })
  const divisi = useQuery({ queryKey: ['divisi'], queryFn: divisiApi.daftar })
  const [cari, setCari] = useState('')
  const [edit, setEdit] = useState<Edit>(null)
  const [form] = Form.useForm<BlokInput>()
  const kodeBaru = Form.useWatch('kode', form)

  const data = useMemo(() => {
    const k = cari.trim().toLowerCase()
    return (q.data ?? []).filter((b) => !k || b.kode.toLowerCase().includes(k) || (b.nama ?? '').toLowerCase().includes(k))
  }, [q.data, cari])

  const simpan = useAksi<IdDibuat | OkQr, BlokInput>({
    fn: (v) => (edit && edit !== 'baru' ? blokApi.ubah(edit.blok_id, v) : blokApi.buat(v)),
    segarkan: [['blok'], ['divisi'], ['tph']],
    sukses: (h) => (typeof h === 'object' && 'qr_berubah' in h && Number(h.qr_berubah) > 0 ? `Blok tersimpan. ${h.qr_berubah} QR TPH berubah: cetak ulang.` : 'Blok tersimpan'),
    onSukses: () => setEdit(null),
  })
  const hapus = useAksi({ fn: (id: number) => blokApi.hapus(id), segarkan: [['blok'], ['divisi']], sukses: 'Blok dihapus' })

  function buka(d: Edit) {
    setEdit(d)
    form.resetFields()
    if (d && d !== 'baru') {
      form.setFieldsValue({ kode: d.kode, erp_oid: d.erp_oid, divisi_id: d.divisi_id, nama: d.nama, luas_ha: d.luas_ha, tahun_tanam: d.tahun_tanam, sph: d.sph, aktif: d.aktif })
    } else {
      form.setFieldsValue({ aktif: true })
    }
  }

  const kodeBerubah = edit && edit !== 'baru' && kodeBaru !== undefined && kodeBaru !== edit.kode && edit.jumlah_tph > 0

  return (
    <>
      <PageHeader
        judul="Blok"
        keterangan="Blok kebun beserta OID ERP-nya (ASM3). Kode blok ikut membentuk isi QR TPH."
        aksi={
          <>
            <Input allowClear prefix={<SearchOutlined />} placeholder="Cari kode atau nama" value={cari} onChange={(e) => setCari(e.target.value)} style={{ width: 220 }} aria-label="Cari blok" />
            {kelola && <Button type="primary" icon={<PlusOutlined />} onClick={() => buka('baru')}>Tambah blok</Button>}
          </>
        }
      />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<Blok>
          rowKey="blok_id"
          loading={q.isLoading}
          dataSource={data}
          pagination={{ pageSize: 25, hideOnSinglePage: true, showTotal: (t) => `${t} blok` }}
          scroll={{ x: 900 }}
          columns={[
            { title: 'Kode', dataIndex: 'kode', sorter: (a, b) => a.kode.localeCompare(b.kode), render: (v: string) => <strong>{v}</strong> },
            { title: 'Divisi', dataIndex: 'divisi_kode', render: (v: string | null) => v ?? '-' },
            { title: 'Nama', dataIndex: 'nama', render: (v: string | null) => v ?? '-' },
            { title: 'OID ERP', dataIndex: 'erp_oid', className: 'tabel-sel-kode' },
            { title: 'Luas (ha)', dataIndex: 'luas_ha', align: 'right', render: (v: number | null) => desimal(v) },
            { title: 'Tahun tanam', dataIndex: 'tahun_tanam', align: 'right', render: (v: number | null) => v ?? '-' },
            { title: 'SPH', dataIndex: 'sph', align: 'right', render: (v: number | null) => desimal(v, 0) },
            {
              title: 'TPH',
              dataIndex: 'jumlah_tph',
              align: 'right',
              render: (v: number, b) => <Link to={`/tph?blok=${b.blok_id}`}>{angka(v)}</Link>,
            },
            { title: 'Status', dataIndex: 'aktif', render: (v: boolean) => (v ? <Tag color="green">Aktif</Tag> : <Tag>Nonaktif</Tag>) },
            ...(kelola
              ? [{
                  title: 'Aksi',
                  key: 'aksi',
                  width: 120,
                  fixed: 'right' as const,
                  render: (_: unknown, d: Blok) => (
                    <Space>
                      <Button size="small" icon={<EditOutlined />} onClick={() => buka(d)} aria-label={`Ubah blok ${d.kode}`} />
                      <Popconfirm
                        title={`Hapus blok ${d.kode}?`}
                        description="Tidak bisa bila masih ada TPH di dalamnya."
                        okText="Hapus"
                        okButtonProps={{ danger: true }}
                        cancelText="Batal"
                        onConfirm={() => hapus.mutate(d.blok_id)}
                      >
                        <Button size="small" danger icon={<DeleteOutlined />} aria-label={`Hapus blok ${d.kode}`} />
                      </Popconfirm>
                    </Space>
                  ),
                }]
              : []),
          ]}
        />
      </Card>

      <Modal
        open={edit !== null}
        title={edit === 'baru' ? 'Tambah blok' : 'Ubah blok'}
        okText="Simpan"
        cancelText="Batal"
        confirmLoading={simpan.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEdit(null)}
        destroyOnClose
        forceRender
        width={560}
      >
        {kodeBerubah && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message={`Mengubah kode membuat ${edit.jumlah_tph} QR TPH berubah`}
            description="QR yang sudah dicetak dan dipasang tidak berlaku lagi. Cetak ulang setelah menyimpan."
          />
        )}
        <Form<BlokInput> form={form} layout="vertical" onFinish={(v) => simpan.mutate(v)} requiredMark="optional">
          <Space size={16} style={{ display: 'flex' }} align="start">
            <Form.Item style={{ flex: 1 }} name="kode" label="Kode blok" rules={[{ required: true, whitespace: true, message: 'Isi kode' }, { max: 20, message: 'Maksimal 20 karakter' }, { pattern: /^[^|]*$/, message: 'Tidak boleh berisi |' }]}>
              <Input placeholder="contoh: A12" />
            </Form.Item>
            <Form.Item style={{ flex: 1 }} name="erp_oid" label="OID di ERP (ASM3)" rules={[{ required: true, message: 'Isi OID ERP' }]}>
              <InputNumber style={{ width: '100%' }} min={1} />
            </Form.Item>
          </Space>
          <Form.Item name="divisi_id" label="Divisi">
            <Select allowClear placeholder="Pilih divisi" options={(divisi.data ?? []).map((d) => ({ value: d.divisi_id, label: `${d.kode} · ${d.nama}` }))} />
          </Form.Item>
          <Form.Item name="nama" label="Nama (opsional)">
            <Input />
          </Form.Item>
          <Space size={16} style={{ display: 'flex' }} align="start">
            <Form.Item style={{ flex: 1 }} name="luas_ha" label="Luas (ha)">
              <InputNumber style={{ width: '100%' }} min={0} step={0.1} />
            </Form.Item>
            <Form.Item style={{ flex: 1 }} name="tahun_tanam" label="Tahun tanam">
              <InputNumber style={{ width: '100%' }} min={1900} max={2100} />
            </Form.Item>
            <Form.Item style={{ flex: 1 }} name="sph" label="SPH (pokok/ha)">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
          </Space>
          <Form.Item name="aktif" label="Aktif" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
