import { DownloadOutlined, FileExcelOutlined, FilePdfOutlined } from '@ant-design/icons'
import { App, Button, Dropdown } from 'antd'
import { useState } from 'react'
import { eksporExcel, eksporPdf, type DokumenEkspor } from '@/lib/ekspor'
import { pesanGalat } from '@/lib/galat'

interface Props {
  /** Menyusun isi dokumen saat tombol dipakai (boleh mengambil data dulu dari server). */
  ambil: () => Promise<DokumenEkspor>
  disabled?: boolean
}

/** Tombol "Ekspor" dengan pilihan Excel dan PDF. Pustaka ekspor dimuat saat dipakai. */
export function TombolEkspor({ ambil, disabled }: Props) {
  const { message } = App.useApp()
  const [sibuk, setSibuk] = useState(false)

  async function jalankan(jenis: 'xlsx' | 'pdf') {
    setSibuk(true)
    try {
      const dok = await ambil()
      if (jenis === 'xlsx') await eksporExcel(dok)
      else await eksporPdf(dok)
    } catch (e) {
      void message.error(`Ekspor gagal: ${pesanGalat(e)}`)
    } finally {
      setSibuk(false)
    }
  }

  return (
    <Dropdown
      disabled={disabled || sibuk}
      menu={{
        items: [
          { key: 'xlsx', icon: <FileExcelOutlined />, label: 'Unduh Excel (.xlsx)', onClick: () => void jalankan('xlsx') },
          { key: 'pdf', icon: <FilePdfOutlined />, label: 'Unduh PDF', onClick: () => void jalankan('pdf') },
        ],
      }}
    >
      <Button icon={<DownloadOutlined />} loading={sibuk} disabled={disabled}>
        Ekspor
      </Button>
    </Dropdown>
  )
}
