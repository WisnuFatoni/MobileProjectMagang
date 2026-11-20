import { Typography } from 'antd'
import type { ReactNode } from 'react'

/** Judul halaman dengan keterangan singkat dan tempat tombol aksi di kanan. */
export function PageHeader({ judul, keterangan, aksi }: { judul: string; keterangan?: ReactNode; aksi?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {judul}
        </Typography.Title>
        {keterangan && (
          <Typography.Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
            {keterangan}
          </Typography.Paragraph>
        )}
      </div>
      {aksi && <div className="page-header__aksi">{aksi}</div>}
    </div>
  )
}
