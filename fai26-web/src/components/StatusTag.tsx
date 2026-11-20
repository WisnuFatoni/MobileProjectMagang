import { Tag } from 'antd'
import type { StatusErp, StatusLokasi } from '@/api/types'
import { STATUS_ERP, STATUS_LOKASI } from '@/lib/format'

export function StatusErpTag({ status }: { status: StatusErp }) {
  const s = STATUS_ERP[status] ?? { teks: status, warna: 'default' }
  return <Tag color={s.warna}>{s.teks}</Tag>
}

export function StatusLokasiTag({ status, mock }: { status: StatusLokasi; mock?: boolean }) {
  const s = STATUS_LOKASI[status] ?? { teks: status, warna: 'default' }
  return (
    <>
      <Tag color={s.warna}>{s.teks}</Tag>
      {mock && <Tag color="magenta">Lokasi palsu</Tag>}
    </>
  )
}
