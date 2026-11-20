import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, App, Button, Card, Checkbox, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Tooltip } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { hakAksesApi } from '@/api/endpoints'
import type { HakAksesRole, IdDibuat, IzinKatalog, Ok } from '@/api/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'
import { useAuth } from '@/features/auth/AuthProvider'
import { useAksi } from '@/lib/useAksi'
import { dariServer, hitungPerubahan, kotakDikunci, ubahKotak, type DraftHak, type PerubahanRole } from './perubahan'

const NAMA_MODUL: Record<string, string> = {
  Panen: 'Panen',
  Master: 'Data master',
  Laporan: 'Laporan',
  ERP: 'ERP',
  Akses: 'Akses pengguna',
  Audit: 'Audit',
  Model: 'Model AI',
}

function kelompokModul(katalog: IzinKatalog[]): { modul: string; izin: IzinKatalog[] }[] {
  const peta = new Map<string, IzinKatalog[]>()
  for (const k of katalog) peta.set(k.modul, [...(peta.get(k.modul) ?? []), k])
  return [...peta.entries()].map(([modul, izin]) => ({ modul, izin }))
}

interface FormRole {
  kode: string
  nama: string
  deskripsi?: string
  salin_dari?: number
}

/** Kode role: huruf besar, angka, garis bawah (sama dengan aturan server). */
const POLA_KODE = /^[A-Z][A-Z0-9_]{1,29}$/

/** Matriks hak akses: baris permission, kolom role. Perubahan disimpan per role (mengganti seluruh hak role itu). */
export function HakAksesPage() {
  const { modal } = App.useApp()
  const { muatUlangProfil } = useAuth()
  const q = useQuery({ queryKey: ['hak-akses'], queryFn: hakAksesApi.daftar })
  const [draft, setDraft] = useState<DraftHak>({})
  // Pengelolaan role: null = tertutup, 'baru' = buat role, objek = ubah nama dan keterangan role itu.
  const [formRole, setFormRole] = useState<HakAksesRole | 'baru' | null>(null)
  const [form] = Form.useForm<FormRole>()

  useEffect(() => {
    if (q.data) setDraft(dariServer(q.data.role))
  }, [q.data])

  const perubahan = useMemo(() => (q.data ? hitungPerubahan(q.data.role, draft) : []), [q.data, draft])

  const simpan = useAksi({
    fn: async (daftar: PerubahanRole[]) => {
      // Satu per satu: bila ada yang ditolak, yang sebelumnya sudah tersimpan dan pesannya jelas.
      for (const p of daftar) await hakAksesApi.ubah(p.roleId, [...(draft[p.roleId] ?? [])].sort())
      return { status: 'OK' }
    },
    segarkan: [['hak-akses']],
    sukses: 'Hak akses disimpan. HP mengikuti saat sinkron berikutnya.',
    onSukses: () => void muatUlangProfil(), // hak akun yang sedang dipakai ikut berubah
  })

  const simpanRole = useAksi<IdDibuat | Ok, FormRole>({
    fn: (v) => {
      if (formRole && formRole !== 'baru') {
        // Hak tidak disentuh: kirim hak role itu apa adanya dari server, hanya nama dan keterangan yang berubah.
        return hakAksesApi.ubah(formRole.role_id, formRole.permission, { nama: v.nama, deskripsi: v.deskripsi ?? '' })
      }
      const salinan = q.data?.role.find((r) => r.role_id === v.salin_dari)?.permission ?? []
      return hakAksesApi.buat({ kode: v.kode.trim().toUpperCase(), nama: v.nama.trim(), deskripsi: v.deskripsi?.trim(), permission: salinan })
    },
    segarkan: [['hak-akses'], ['peran']],
    sukses: (_h, v) => (formRole === 'baru' ? `Role ${v.kode.trim().toUpperCase()} dibuat. Centang hak yang diberikan lalu simpan.` : 'Role diperbarui'),
    onSukses: () => setFormRole(null),
  })
  const hapusRole = useAksi({ fn: (id: number) => hakAksesApi.hapus(id), segarkan: [['hak-akses'], ['peran']], sukses: 'Role dihapus' })

  function bukaForm(r: HakAksesRole | 'baru') {
    setFormRole(r)
    form.resetFields()
    if (r !== 'baru') form.setFieldsValue({ kode: r.kode, nama: r.nama, deskripsi: r.deskripsi ?? '' })
  }

  const tanyaSimpan = () =>
    modal.confirm({
      title: `Simpan perubahan hak ${perubahan.length} role?`,
      width: 560,
      okText: 'Simpan',
      cancelText: 'Batal',
      content: (
        <div>
          <p>Berlaku langsung di server, dan di HP setelah sinkron berikutnya.</p>
          {perubahan.map((p) => (
            <div key={p.roleId} style={{ marginBottom: 8 }}>
              <strong>{p.kode}</strong>
              <div>
                {p.tambah.map((k) => (
                  <Tag key={k} color="green">+ {k}</Tag>
                ))}
                {p.cabut.map((k) => (
                  <Tag key={k} color="red">- {k}</Tag>
                ))}
              </div>
            </div>
          ))}
        </div>
      ),
      onOk: () => simpan.mutateAsync(perubahan),
    })

  const role: HakAksesRole[] = q.data?.role ?? []
  const berubah = new Set(perubahan.map((p) => p.roleId))

  return (
    <>
      <PageHeader
        judul="Hak Akses"
        keterangan="Atur apa yang boleh dilakukan tiap role. Centang lalu simpan; hak ikut berlaku untuk semua akun dengan role itu."
        aksi={
          <Space wrap>
            <Button icon={<PlusOutlined />} onClick={() => bukaForm('baru')}>
              Buat role baru
            </Button>
            <Button disabled={perubahan.length === 0 || simpan.isPending} onClick={() => q.data && setDraft(dariServer(q.data.role))}>
              Batalkan
            </Button>
            <Button type="primary" disabled={perubahan.length === 0} loading={simpan.isPending} onClick={tanyaSimpan}>
              {perubahan.length > 0 ? `Simpan perubahan (${perubahan.length} role)` : 'Simpan perubahan'}
            </Button>
          </Space>
        }
      />
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Hak role ADMIN untuk mengelola hak akses tidak bisa dicabut, supaya tidak ada yang terkunci dari halaman ini."
      />
      <Card title="Daftar role" style={{ marginBottom: 16 }} styles={{ body: { padding: 0 } }}>
        <Table<HakAksesRole>
          rowKey="role_id"
          size="small"
          loading={q.isLoading}
          pagination={false}
          dataSource={role}
          scroll={{ x: 720 }}
          columns={[
            { title: 'Kode', dataIndex: 'kode', width: 150, render: (v: string) => <strong>{v}</strong> },
            { title: 'Nama', dataIndex: 'nama', width: 160 },
            { title: 'Keterangan', dataIndex: 'deskripsi', render: (v: string | null) => v ?? '-' },
            { title: 'Akun aktif', dataIndex: 'pengguna', align: 'right', width: 100 },
            {
              title: 'Jenis',
              dataIndex: 'bawaan',
              width: 110,
              render: (v: boolean) => (v ? <Tag>Bawaan</Tag> : <Tag color="green">Buatan admin</Tag>),
            },
            {
              title: '',
              key: 'aksi',
              width: 120,
              render: (_: unknown, r) => (
                <Space>
                  <Tooltip title="Ubah nama dan keterangan">
                    <Button size="small" icon={<EditOutlined />} aria-label={`Ubah role ${r.kode}`} onClick={() => bukaForm(r)} />
                  </Tooltip>
                  {r.bawaan ? (
                    <Tooltip title="Role bawaan tidak bisa dihapus">
                      <Button size="small" icon={<DeleteOutlined />} disabled aria-label={`Hapus role ${r.kode}`} />
                    </Tooltip>
                  ) : r.pengguna > 0 ? (
                    <Tooltip title="Masih dipegang akun aktif: pindahkan akun itu ke role lain dulu">
                      <Button size="small" icon={<DeleteOutlined />} disabled aria-label={`Hapus role ${r.kode}`} />
                    </Tooltip>
                  ) : (
                    <Popconfirm
                      title={`Hapus role ${r.kode}?`}
                      description="Role dan hak-nya dihapus. Kodenya tidak bisa dipakai lagi."
                      okText="Hapus"
                      cancelText="Batal"
                      okButtonProps={{ danger: true }}
                      onConfirm={() => hapusRole.mutate(r.role_id)}
                    >
                      <Button size="small" danger icon={<DeleteOutlined />} aria-label={`Hapus role ${r.kode}`} />
                    </Popconfirm>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        open={formRole !== null}
        title={formRole === 'baru' ? 'Buat role baru' : `Ubah role ${formRole?.kode ?? ''}`}
        okText="Simpan"
        cancelText="Batal"
        confirmLoading={simpanRole.isPending}
        onCancel={() => setFormRole(null)}
        onOk={() => void form.validateFields().then((v) => simpanRole.mutate(v))}
        destroyOnClose
        forceRender
      >
        <Form<FormRole> form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="kode"
            label="Kode role"
            extra="Huruf besar, angka, atau garis bawah (mis. KEPALA_KEBUN). Tidak bisa diubah setelah dibuat."
            rules={[
              { required: true, message: 'Kode wajib diisi' },
              { validator: (_r, v: string | undefined) => (!v || POLA_KODE.test(v.trim().toUpperCase()) ? Promise.resolve() : Promise.reject(new Error('2-30 karakter: huruf besar, angka, garis bawah; diawali huruf'))) },
            ]}
          >
            <Input disabled={formRole !== 'baru'} maxLength={30} style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Form.Item name="nama" label="Nama" rules={[{ required: true, whitespace: true, message: 'Nama wajib diisi' }, { min: 2, max: 60 }]}>
            <Input maxLength={60} />
          </Form.Item>
          <Form.Item name="deskripsi" label="Keterangan (opsional)" rules={[{ max: 200 }]}>
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
          {formRole === 'baru' && (
            <Form.Item name="salin_dari" label="Salin hak dari role (opsional)" extra="Memudahkan: hak awal sama dengan role itu, lalu bisa diubah.">
              <Select allowClear placeholder="Mulai tanpa hak" options={role.map((r) => ({ value: r.role_id, label: `${r.nama} (${r.kode})` }))} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Space direction="vertical" size="middle" style={{ display: 'flex' }}>
        {kelompokModul(q.data?.permission ?? []).map((g) => (
          <Card key={g.modul} title={NAMA_MODUL[g.modul] ?? g.modul} loading={q.isLoading} styles={{ body: { padding: 0 } }}>
            <Table<IzinKatalog>
              rowKey="kode"
              size="small"
              pagination={false}
              dataSource={g.izin}
              scroll={{ x: 640 }}
              columns={[
                {
                  title: 'Hak',
                  key: 'izin',
                  render: (_: unknown, i) => (
                    <>
                      {i.nama}
                      <div className="tabel-sel-kode" style={{ opacity: 0.65 }}>{i.kode}</div>
                    </>
                  ),
                },
                ...role.map((r) => ({
                  title: (
                    <span style={berubah.has(r.role_id) ? { color: '#d97706' } : undefined}>
                      {r.nama}
                      {berubah.has(r.role_id) ? ' •' : ''}
                    </span>
                  ),
                  key: r.kode,
                  width: 110,
                  align: 'center' as const,
                  render: (_: unknown, i: IzinKatalog) => {
                    const kunci = kotakDikunci(r.kode, i.kode)
                    const kotak = (
                      <Checkbox
                        checked={draft[r.role_id]?.has(i.kode) ?? false}
                        disabled={kunci || simpan.isPending}
                        aria-label={`${r.nama}: ${i.nama}`}
                        onChange={(e) => setDraft((d) => ubahKotak(d, r.role_id, i.kode, e.target.checked))}
                      />
                    )
                    return kunci ? <Tooltip title="Tidak bisa dicabut dari ADMIN">{kotak}</Tooltip> : kotak
                  },
                })),
              ]}
            />
          </Card>
        ))}
      </Space>
    </>
  )
}
