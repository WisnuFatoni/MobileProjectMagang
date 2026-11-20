import { EditOutlined, KeyOutlined, LockOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Tooltip } from 'antd'
import { useState } from 'react'
import { divisiApi, penggunaApi } from '@/api/endpoints'
import type { IdDibuat, Ok, Pengguna, PenggunaInput } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { waktu } from '@/lib/format'
import { useAksi } from '@/lib/useAksi'

type Edit = Pengguna | 'baru' | null

const ATURAN_PASSWORD = [{ required: true, message: 'Isi password' }, { min: 8, message: 'Minimal 8 karakter' }, { max: 72, message: 'Maksimal 72 karakter' }]

export function PenggunaPage() {
  const { profil } = useAuth()
  const q = useQuery({ queryKey: ['pengguna'], queryFn: penggunaApi.daftar })
  const divisi = useQuery({ queryKey: ['divisi'], queryFn: divisiApi.daftar, staleTime: 5 * 60_000 })
  const role = useQuery({ queryKey: ['peran'], queryFn: penggunaApi.role, staleTime: 10 * 60_000 })
  const [edit, setEdit] = useState<Edit>(null)
  const [reset, setReset] = useState<Pengguna | null>(null)
  const [form] = Form.useForm<PenggunaInput>()
  const [formReset] = Form.useForm<{ password: string }>()

  const simpan = useAksi<IdDibuat | Ok, PenggunaInput>({
    fn: (v) => (edit && edit !== 'baru' ? penggunaApi.ubah(edit.user_id, v) : penggunaApi.buat(v)),
    segarkan: [['pengguna'], ['ringkasan']],
    sukses: (_h, v) => (v.username ? 'Pengguna dibuat. Ia wajib mengganti password saat login pertama.' : 'Pengguna tersimpan'),
    onSukses: () => setEdit(null),
  })
  const resetPw = useAksi({
    fn: (v: { id: number; password: string }) => penggunaApi.resetPassword(v.id, v.password),
    segarkan: [['pengguna']],
    sukses: 'Password direset. Semua sesi pengguna dicabut.',
    onSukses: () => setReset(null),
  })

  function buka(d: Edit) {
    setEdit(d)
    form.resetFields()
    if (d && d !== 'baru') form.setFieldsValue({ nama: d.nama, role: d.role, aktif: d.aktif, mandor_hrmb_code: d.mandor_hrmb_code, afdeling_kode: d.afdeling_kode })
    else form.setFieldsValue({ aktif: true, role: [] })
  }

  return (
    <>
      <PageHeader
        judul="Pengguna"
        keterangan="Akun krani, mandor, asisten, admin, dan auditor. Hak akses mengikuti role; perubahan role langsung berlaku di server."
        aksi={<Button type="primary" icon={<PlusOutlined />} onClick={() => buka('baru')}>Tambah pengguna</Button>}
      />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Card styles={{ body: { padding: 0 } }}>
        <Table<Pengguna>
          rowKey="user_id"
          loading={q.isLoading}
          dataSource={q.data}
          pagination={{ pageSize: 25, hideOnSinglePage: true, showTotal: (t) => `${t} pengguna` }}
          scroll={{ x: 900 }}
          columns={[
            { title: 'Nama', key: 'nama', render: (_: unknown, p) => (<><strong>{p.nama}</strong><div style={{ opacity: 0.65 }}>@{p.username}</div></>) },
            { title: 'Role', dataIndex: 'role', render: (v: string[]) => v.map((r) => <Tag key={r} color="green">{r}</Tag>) },
            {
              title: 'Mandor',
              key: 'mandor',
              render: (_: unknown, p: Pengguna) => (p.mandor_hrmb_code ? <>{p.mandor_hrmb_code}{p.afdeling_kode ? <div style={{ opacity: 0.65 }}>{p.afdeling_kode}</div> : null}</> : '-'),
            },
            { title: 'Login terakhir', dataIndex: 'login_terakhir', render: (v: string | null) => waktu(v) },
            {
              title: 'Status',
              key: 'status',
              render: (_: unknown, p) => (
                <Space size={4} wrap>
                  {p.aktif ? <Tag color="green">Aktif</Tag> : <Tag>Nonaktif</Tag>}
                  {p.terkunci && <Tag color="red" icon={<LockOutlined />}>Terkunci</Tag>}
                  {p.wajib_ganti_password && <Tag color="gold">Wajib ganti password</Tag>}
                </Space>
              ),
            },
            {
              title: 'Aksi',
              key: 'aksi',
              width: 110,
              fixed: 'right' as const,
              render: (_: unknown, p) => (
                <Space>
                  <Tooltip title="Ubah"><Button size="small" icon={<EditOutlined />} onClick={() => buka(p)} aria-label={`Ubah ${p.username}`} /></Tooltip>
                  <Tooltip title="Reset password"><Button size="small" icon={<KeyOutlined />} onClick={() => { formReset.resetFields(); setReset(p) }} aria-label={`Reset password ${p.username}`} /></Tooltip>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        open={edit !== null}
        title={edit === 'baru' ? 'Tambah pengguna' : 'Ubah pengguna'}
        okText="Simpan"
        cancelText="Batal"
        confirmLoading={simpan.isPending}
        onOk={() => form.submit()}
        onCancel={() => setEdit(null)}
        destroyOnClose
        forceRender
      >
        <Form<PenggunaInput> form={form} layout="vertical" onFinish={(v) => simpan.mutate(v)} requiredMark="optional" autoComplete="off">
          {edit === 'baru' && (
            <>
              <Form.Item name="username" label="Username" rules={[{ required: true, message: 'Isi username' }, { pattern: /^[a-z0-9._-]{3,50}$/, message: '3 sampai 50 karakter: huruf kecil, angka, titik, garis bawah, strip' }]}>
                <Input autoComplete="off" />
              </Form.Item>
              <Form.Item name="password" label="Password awal" rules={ATURAN_PASSWORD} extra="Pengguna wajib menggantinya saat login pertama.">
                <Input.Password autoComplete="new-password" />
              </Form.Item>
            </>
          )}
          <Form.Item name="nama" label="Nama lengkap" rules={[{ required: true, whitespace: true, message: 'Isi nama' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="role" label="Role" rules={[{ required: true, type: 'array', min: 1, message: 'Pilih minimal satu role' }]}>
            <Select mode="multiple" placeholder="Pilih role" loading={role.isLoading} options={(role.data ?? []).map((r) => ({ value: r.kode, label: `${r.kode} · ${r.deskripsi || r.nama}` }))} />
          </Form.Item>
          <Form.Item
            name="mandor_hrmb_code"
            label="Kode mandor (HRMBCODE)"
            extra="Isi untuk akun mandor. HP-nya hanya menerima pemanen milik mandor ini, dan catatannya dicatat atas nama mandor ini."
          >
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="afdeling_kode" label="Afdeling mandor" extra="Membatasi blok dan TPH di HP mandor ke afdeling ini.">
            <Select allowClear placeholder="Pilih afdeling" loading={divisi.isLoading} options={(divisi.data ?? []).map((d) => ({ value: d.kode, label: `${d.kode} · ${d.nama}` }))} />
          </Form.Item>
          {edit !== 'baru' && (
            <Form.Item name="aktif" label="Aktif" valuePropName="checked" extra={edit?.user_id === profil?.user_id ? 'Anda tidak bisa menonaktifkan akun sendiri.' : undefined}>
              <Switch disabled={edit?.user_id === profil?.user_id} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        open={reset !== null}
        title={`Reset password @${reset?.username ?? ''}`}
        okText="Reset"
        cancelText="Batal"
        confirmLoading={resetPw.isPending}
        onOk={() => formReset.submit()}
        onCancel={() => setReset(null)}
        destroyOnClose
        forceRender
      >
        <Form form={formReset} layout="vertical" onFinish={(v) => reset && resetPw.mutate({ id: reset.user_id, password: v.password })} autoComplete="off">
          <Form.Item name="password" label="Password baru" rules={ATURAN_PASSWORD} extra="Semua sesi pengguna dicabut dan ia wajib menggantinya saat login berikutnya.">
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
