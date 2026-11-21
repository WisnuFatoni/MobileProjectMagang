import { DownloadOutlined, PrinterOutlined } from '@ant-design/icons'
import { Button, Space } from 'antd'
import { useState } from 'react'
import { DialogEkspor, type AksiEkspor } from './DialogEkspor'

type PropsDialog = Omit<Parameters<typeof DialogEkspor>[0], 'aksi' | 'onTutup'>

/** Dua tombol berdampingan, "Ekspor" dan "Cetak"; keduanya membuka dialog pilihan periode lebih dulu. */
export function TombolEksporCetak(props: PropsDialog) {
  const [aksi, setAksi] = useState<AksiEkspor | null>(null)
  return (
    <>
      <Space>
        <Button icon={<DownloadOutlined />} onClick={() => setAksi('ekspor')}>
          Ekspor
        </Button>
        <Button icon={<PrinterOutlined />} onClick={() => setAksi('cetak')}>
          Cetak
        </Button>
      </Space>
      <DialogEkspor {...props} aksi={aksi} onTutup={() => setAksi(null)} />
    </>
  )
}
