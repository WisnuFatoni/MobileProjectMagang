import { CameraOutlined, DeleteOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { App, Button, Modal, Space, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { fotoProfilApi } from '@/api/endpoints'
import { pesanGalat } from '@/lib/galat'
import { useAuth } from './AuthProvider'
import { AvatarAkun } from './FotoProfil'
import { PemotongFoto, type PemotongHandle } from './PemotongFoto'
import { useFotoProfil } from './useFotoProfil'

const TIPE_BOLEH = ['image/jpeg', 'image/png', 'image/webp']
const MAKS_ASLI = 25 * 1024 * 1024

/** Dialog ubah foto profil: pilih gambar, lihat pratinjau bulat (yang tampil = potongan tengah), lalu simpan. */
export function FotoProfilModal({ buka, onTutup }: { buka: boolean; onTutup: () => void }) {
  const { message } = App.useApp()
  const { profil, muatUlangProfil } = useAuth()
  const qc = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [berkas, setBerkas] = useState<File | null>(null)
  const pemotong = useRef<PemotongHandle>(null)
  const [sibuk, setSibuk] = useState<'simpan' | 'hapus' | null>(null)
  const fotoSekarang = useFotoProfil(profil?.foto_versi)

  useEffect(() => {
    if (!buka) setBerkas(null)
  }, [buka])

  function pilih(f: File | undefined) {
    if (!f) return
    if (!TIPE_BOLEH.includes(f.type)) {
      void message.error('Pilih gambar JPEG, PNG, atau WebP')
      return
    }
    if (f.size > MAKS_ASLI) {
      void message.error('Gambar terlalu besar (maksimal 25 MB)')
      return
    }
    setBerkas(f)
  }

  async function simpan() {
    if (!berkas) return
    setSibuk('simpan')
    try {
      // Yang diunggah adalah bagian di dalam lingkaran (768 px), bukan gambar utuh.
      await fotoProfilApi.simpan(await (pemotong.current as PemotongHandle).hasil())
      await muatUlangProfil()
      void qc.invalidateQueries({ queryKey: ['foto-profil'] })
      void message.success('Foto profil disimpan')
      onTutup()
    } catch (e) {
      void message.error(`Gagal menyimpan foto: ${pesanGalat(e)}`)
    } finally {
      setSibuk(null)
    }
  }

  async function hapus() {
    setSibuk('hapus')
    try {
      await fotoProfilApi.hapus()
      await muatUlangProfil()
      void qc.invalidateQueries({ queryKey: ['foto-profil'] })
      void message.success('Foto profil dihapus')
      onTutup()
    } catch (e) {
      void message.error(`Gagal menghapus foto: ${pesanGalat(e)}`)
    } finally {
      setSibuk(null)
    }
  }

  const adaFoto = profil?.foto_versi !== null && profil?.foto_versi !== undefined

  return (
    <Modal
      open={buka}
      onCancel={sibuk ? undefined : onTutup}
      title="Foto profil"
      width={380}
      destroyOnClose
      footer={
        <Space wrap>
          {adaFoto && !berkas && (
            <Button danger icon={<DeleteOutlined />} loading={sibuk === 'hapus'} disabled={sibuk !== null} onClick={() => void hapus()}>
              Hapus foto
            </Button>
          )}
          <Button onClick={onTutup} disabled={sibuk !== null}>
            Batal
          </Button>
          <Button type="primary" disabled={!berkas} loading={sibuk === 'simpan'} onClick={() => void simpan()}>
            Simpan
          </Button>
        </Space>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, padding: '8px 0' }}>
        {berkas ? (
          <PemotongFoto ref={pemotong} berkas={berkas} />
        ) : fotoSekarang ? (
          <img src={fotoSekarang} alt="Foto profil saat ini" style={{ width: 168, height: 168, borderRadius: '50%', objectFit: 'cover' }} />
        ) : (
          <AvatarAkun nama={profil?.nama} versi={null} size={168} />
        )}
        <Typography.Text type="secondary" style={{ textAlign: 'center' }}>
          {berkas ? 'Geser gambar dan atur zoom sampai pas di dalam lingkaran.' : 'Pilih gambar JPEG, PNG, atau WebP, lalu atur posisinya.'}
        </Typography.Text>
        <input
          ref={input}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(e) => {
            pilih(e.target.files?.[0])
            e.target.value = ''
          }}
        />
        <Button icon={<CameraOutlined />} onClick={() => input.current?.click()} disabled={sibuk !== null}>
          {berkas ? 'Pilih gambar lain' : 'Pilih gambar'}
        </Button>
      </div>
    </Modal>
  )
}
