import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Space, Switch, Table, Tag } from 'antd'
import { useState } from 'react'
import { divisiApi } from '@/api/endpoints'
import type { Divisi, DivisiInput, IdDibuat, Ok } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { IZIN } from '@/lib/izin'
import { useAksi } from '@/lib/useAksi'

type Edit = Divisi | 'baru' | null

export function DivisiPage() {
  const { bisa } = useAuth()
  const kelola = bisa(IZIN.masterKelola)
  const q = useQuery({ queryKey: ['divisi'], queryFn: divisiApi.daftar })
  const [edit, setEdit] = useState<Edit>(null)
  const [form] = Form.useForm<DivisiInput>()

  const simpan = useAksi<IdDibuat | Ok, DivisiInput>({
    fn: (v) => (edit && edit !== 'baru' ? divisiApi.ubah(edit.divisi_id, v) : divisiApi.buat(v)),
    segarkan: [['divisi'], ['blok']],
    sukses: 'Divisi tersimpan',
    onSukses: () => setEdit(null),
  })
  const hapus = useAksi({ fn: (id: number) => divisiApi.hapus(id), segarkan: [['divisi']], sukses: 'Divisi dihapus' })

  function buka(d: Edit) {
    setEdit(d)
    form.resetFields()
    if (d && d !== 'baru') form.setFieldsValue({ kode: d.kode, nama: d.nama, erp_oid: d.erp_oid, aktif: d.aktif })
    else form.setFieldsValue({ aktif: true })
  }

  return (
    <>
      <PageHeader
        judul="Divisi"
        keterangan="Pengelompokan blok di dalam satu unit."
        aksi={kelola && <Button type="primary" icon={<PlusOutlined />} onClick={() => buka('baru')}>Tambah divisi</Button>}
      />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<Divisi>
          rowKey="divisi_id"
          loading={q.isLoading}
          dataSource={q.data}
          pagination={false}
          scroll={{ x: 640 }}
          columns={[
            { title: 'Kode', dataIndex: 'kode', render: (v: string) => <strong>{v}</strong> },
            { title: 'Nama', dataIndex: 'nama' },
            { title: 'OID ERP', dataIndex: 'erp_oid', render: (v: number | null) => v ?? '-' },
            { title: 'Jumlah blok', dataIndex: 'jumlah_blok', align: 'right' },
            { title: 'Status', dataIndex: 'aktif', render: (v: boolean) => (v ? <Tag color="green">Aktif</Tag> : <Tag>Nonaktif</Tag>) },
            ...(kelola
              ? [{
                  title: 'Aksi',
                  key: 'aksi',
                  width: 130,
                  render: (_: unknown, d: Divisi) => (
                    <Space>
                      <Button size="small" icon={<EditOutlined />} onClick={() => buka(d)} aria-label={`Ubah divisi ${d.kode}`} />
                      <Popconfirm
                        title={`Hapus divisi ${d.kode}?`}
                        description="Tidak bisa bila masih ada blok di dalamnya."
                        okText="Hapus"
                        okButtonProps={{ danger: true }}
                        cancelText="Batal"
                        onConfirm={() => hapus.mutate(d.divisi_id)}
                      >
                        <Button size="small" danger icon={<DeleteOutlined />} aria-label={`Hapus divisi ${d.kode}`} />
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
        title={edit === 'baru' ? 'Tambah divisi' : 'Ubah divisi'}
        okText="Simpan"
        cancelText="Batal"
        confirmLoading={simpan.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEdit(null)}
        destroyOnClose
        forceRender
      >
        <Form<DivisiInput> form={form} layout="vertical" onFinish={(v) => simpan.mutate(v)} requiredMark="optional">
          <Form.Item name="kode" label="Kode" rules={[{ required: true, whitespace: true, message: 'Isi kode' }, { max: 20, message: 'Maksimal 20 karakter' }, { pattern: /^[^|]*$/, message: 'Tidak boleh berisi |' }]}>
            <Input placeholder="contoh: DIV2" />
          </Form.Item>
          <Form.Item name="nama" label="Nama" rules={[{ required: true, whitespace: true, message: 'Isi nama' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="erp_oid" label="OID di ERP (opsional)">
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="aktif" label="Aktif" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
