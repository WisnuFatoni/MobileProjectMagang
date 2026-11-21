import { DownloadOutlined } from '@ant-design/icons'
import { App, Button, Dropdown } from 'antd'
import type { MenuProps } from 'antd'
import { useState } from 'react'
import { cetakLaporan } from '@/lib/cetak'
import { eksporExcel, eksporPdf, type LaporanEkspor } from '@/lib/ekspor'
import { pesanGalat } from '@/lib/galat'

/** Satu pilihan di menu Ekspor. */
export interface PilihanEkspor {
  key: string
  label: string
  format: 'xlsx' | 'pdf' | 'cetak'
  /** Judul kelompok di menu (pilihan dengan kelompok sama dikumpulkan). */
  kelompok?: string
  /** Menyusun isi dokumen (boleh mengambil data dulu dari server). `lapor` memperbarui pesan kemajuan. */
  ambil: (lapor: (teks: string) => void) => Promise<LaporanEkspor>
}

interface Props {
  pilihan: PilihanEkspor[]
  disabled?: boolean
}

const KUNCI_PESAN = 'ekspor'

/** Tombol "Ekspor" dengan pilihan Excel dan PDF (ringkas atau rinci). Pustaka ekspor dimuat saat dipakai. */
export function TombolEkspor({ pilihan, disabled }: Props) {
  const { message } = App.useApp()
  const [sibuk, setSibuk] = useState(false)

  async function jalankan(p: PilihanEkspor) {
    setSibuk(true)
    const lapor = (teks: string) => void message.open({ key: KUNCI_PESAN, type: 'loading', content: teks, duration: 0 })
    try {
      lapor('Menyiapkan data…')
      const dok = await p.ambil(lapor)
      lapor('Menyusun berkas…')
      if (p.format === 'xlsx') await eksporExcel(dok)
      else if (p.format === 'pdf') await eksporPdf(dok)
      else await cetakLaporan(dok)
      void message.open({ key: KUNCI_PESAN, type: 'success', content: p.format === 'cetak' ? 'Dialog cetak dibuka' : 'Berkas siap diunduh', duration: 3 })
    } catch (e) {
      void message.open({ key: KUNCI_PESAN, type: 'error', content: `Ekspor gagal: ${pesanGalat(e)}`, duration: 6 })
    } finally {
      setSibuk(false)
    }
  }

  const kelompok = new Map<string, PilihanEkspor[]>()
  for (const p of pilihan) kelompok.set(p.kelompok ?? '', [...(kelompok.get(p.kelompok ?? '') ?? []), p])
  const items: MenuProps['items'] = [...kelompok.entries()].map(([nama, isi]) => ({
    type: 'group' as const,
    key: `g-${nama}`,
    label: nama || undefined,
    children: isi.map((p) => ({ key: p.key, label: p.label, onClick: () => void jalankan(p) })),
  }))

  return (
    <Dropdown disabled={disabled || sibuk} menu={{ items }}>
      <Button icon={<DownloadOutlined />} loading={sibuk} disabled={disabled}>
        Ekspor
      </Button>
    </Dropdown>
  )
}
