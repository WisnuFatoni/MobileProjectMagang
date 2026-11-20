import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Space, Switch, Table, Tag } from 'antd'
import { useMemo, useState } from 'react'
import { pemanenApi } from '@/api/endpoints'
import type { IdDibuat, Ok, Pemanen, PemanenInput } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { IZIN } from '@/lib/izin'
import { useAksi } from '@/lib/useAksi'

type Edit = Pemanen | 'baru' | null

export function PemanenPage() {
  const { bisa } = useAuth()
  const kelola = bisa(IZIN.masterKelola)
  const q = useQuery({ queryKey: ['pemanen'], queryFn: pemanenApi.daftar })
  const [cari, setCari] = useState('')
  const [edit, setEdit] = useState<Edit>(null)
  const [form] = Form.useForm<PemanenInput>()

  const data = useMemo(() => {
    const k = cari.trim().toLowerCase()
    return (q.data ?? []).filter((p) => !k || p.nama.toLowerCase().includes(k) || String(p.erp_hrmb_code).includes(k) || (p.kode ?? '').toLowerCase().includes(k))
  }, [q.data, cari])

  const simpan = useAksi<IdDibuat | Ok, PemanenInput>({
    fn: (v) => (edit && edit !== 'baru' ? pemanenApi.ubah(edit.pemanen_id, v) : pemanenApi.buat(v)),
    segarkan: [['pemanen']],
    sukses: 'Pemanen tersimpan',
    onSukses: () => setEdit(null),
  })
  const hapus = useAksi({ fn: (id: number) => pemanenApi.hapus(id), segarkan: [['pemanen']], sukses: 'Pemanen dihapus' })

  function buka(d: Edit) {
    setEdit(d)
    form.resetFields()
    if (d && d !== 'baru') form.setFieldsValue({ erp_hrmb_code: d.erp_hrmb_code, mandor_hrmb_code: d.mandor_hrmb_code, kode: d.kode, nama: d.nama, aktif: d.aktif })
    else form.setFieldsValue({ aktif: true })
  }

  return (
    <>
      <PageHeader
        judul="Pemanen"
        keterangan="Daftar pemanen yang bisa dipilih krani di HP. Satu TPH hanya untuk satu pemanen. Kode ERP (HRMBCODE) dan kode mandor dipakai saat impor ke HVD1."
        aksi={
          <>
            <Input allowClear prefix={<SearchOutlined />} placeholder="Cari nama atau kode" value={cari} onChange={(e) => setCari(e.target.value)} style={{ width: 240 }} aria-label="Cari pemanen" />
            {kelola && <Button type="primary" icon={<PlusOutlined />} onClick={() => buka('baru')}>Tambah pemanen</Button>}
          </>
        }
      />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<Pemanen>
          rowKey="pemanen_id"
          loading={q.isLoading}
          dataSource={data}
          pagination={{ pageSize: 25, hideOnSinglePage: true, showTotal: (t) => `${t} pemanen` }}
          scroll={{ x: 640 }}
          columns={[
            { title: 'Nama', dataIndex: 'nama', sorter: (a, b) => a.nama.localeCompare(b.nama), render: (v: string) => <strong>{v}</strong> },
            { title: 'Kode', dataIndex: 'kode', render: (v: string | null) => v ?? '-' },
            { title: 'Kode ERP (HRMBCODE)', dataIndex: 'erp_hrmb_code', className: 'tabel-sel-kode' },
            {
              title: 'Mandor (HRMB)',
              dataIndex: 'mandor_hrmb_code',
              className: 'tabel-sel-kode',
              render: (v: number | null) => v ?? <Tag color="orange">Belum diisi</Tag>,
            },
            { title: 'Status', dataIndex: 'aktif', render: (v: boolean) => (v ? <Tag color="green">Aktif</Tag> : <Tag>Nonaktif</Tag>) },
            ...(kelola
              ? [{
                  title: 'Aksi',
                  key: 'aksi',
                  width: 130,
                  render: (_: unknown, d: Pemanen) => (
                    <Space>
                      <Button size="small" icon={<EditOutlined />} onClick={() => buka(d)} aria-label={`Ubah ${d.nama}`} />
                      <Popconfirm title={`Hapus ${d.nama}?`} okText="Hapus" okButtonProps={{ danger: true }} cancelText="Batal" onConfirm={() => hapus.mutate(d.pemanen_id)}>
                        <Button size="small" danger icon={<DeleteOutlined />} aria-label={`Hapus ${d.nama}`} />
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
        title={edit === 'baru' ? 'Tambah pemanen' : 'Ubah pemanen'}
        okText="Simpan"
        cancelText="Batal"
        confirmLoading={simpan.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEdit(null)}
        destroyOnClose
        forceRender
      >
        <Form<PemanenInput> form={form} layout="vertical" onFinish={(v) => simpan.mutate(v)} requiredMark="optional">
          <Form.Item name="nama" label="Nama" rules={[{ required: true, whitespace: true, message: 'Isi nama' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="erp_hrmb_code" label="Kode ERP (HRMBCODE)" rules={[{ required: true, message: 'Isi kode ERP' }]}>
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item
            name="mandor_hrmb_code"
            label="Kode mandor (HRMBCODE)"
            extra="Mandor kelompok pemanen ini. Dipakai sebagai mandor pada header HVD1; bila kosong, HVDM tidak bisa membuat header."
          >
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="kode" label="Kode internal (opsional)" rules={[{ max: 20, message: 'Maksimal 20 karakter' }]}>
            <Input placeholder="contoh: P-001" />
          </Form.Item>
          <Form.Item name="aktif" label="Aktif" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
