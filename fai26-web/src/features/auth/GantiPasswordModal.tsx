import { LockOutlined } from '@ant-design/icons'
import { Alert, Form, Input, Modal } from 'antd'
import { akunApi } from '@/api/endpoints'
import { useAksi } from '@/lib/useAksi'
import { useAuth } from './AuthProvider'

interface Nilai {
  lama: string
  baru: string
  ulangi: string
}

interface Props {
  buka: boolean
  /** true bila admin mengatur ulang password: pengguna tidak boleh menutup dialog sebelum menggantinya. */
  wajib?: boolean
  onTutup: () => void
}

/** Dialog ganti password sendiri. Setelah berhasil, sesi di perangkat lain dicabut server. */
export function GantiPasswordModal({ buka, wajib = false, onTutup }: Props) {
  const [form] = Form.useForm<Nilai>()
  const { muatUlangProfil } = useAuth()
  const ganti = useAksi({
    fn: (v: Nilai) => akunApi.gantiPassword(v.lama, v.baru),
    segarkan: [],
    sukses: 'Password diganti. Perangkat lain perlu login ulang.',
    onSukses: () => {
      form.resetFields()
      void muatUlangProfil()
      onTutup()
    },
  })

  return (
    <Modal
      open={buka}
      title="Ganti password"
      okText="Simpan password"
      cancelText="Batal"
      closable={!wajib}
      maskClosable={!wajib}
      keyboard={!wajib}
      cancelButtonProps={{ style: wajib ? { display: 'none' } : undefined }}
      confirmLoading={ganti.isPending}
      onOk={() => form.submit()}
      onCancel={() => {
        form.resetFields()
        onTutup()
      }}
      destroyOnClose
      forceRender
    >
      {wajib && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Password Anda diatur ulang oleh admin"
          description="Buat password baru yang hanya Anda yang tahu sebelum melanjutkan."
        />
      )}
      <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => ganti.mutate(v)} autoComplete="off">
        <Form.Item name="lama" label="Password sekarang" rules={[{ required: true, message: 'Isi password sekarang' }]}>
          <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="baru"
          label="Password baru"
          extra="8 sampai 72 karakter, berbeda dari password sekarang."
          rules={[
            { required: true, message: 'Isi password baru' },
            { min: 8, message: 'Minimal 8 karakter' },
            { max: 72, message: 'Maksimal 72 karakter' },
            ({ getFieldValue }) => ({
              validator: (_, nilai: string) =>
                !nilai || nilai !== getFieldValue('lama') ? Promise.resolve() : Promise.reject(new Error('Harus berbeda dari password sekarang')),
            }),
          ]}
        >
          <Input.Password prefix={<LockOutlined />} autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="ulangi"
          label="Ulangi password baru"
          dependencies={['baru']}
          rules={[
            { required: true, message: 'Ulangi password baru' },
            ({ getFieldValue }) => ({
              validator: (_, nilai: string) =>
                !nilai || nilai === getFieldValue('baru') ? Promise.resolve() : Promise.reject(new Error('Tidak sama dengan password baru')),
            }),
          ]}
        >
          <Input.Password prefix={<LockOutlined />} autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
