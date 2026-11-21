import { Avatar } from 'antd'
import { useFotoProfil } from './useFotoProfil'

/** Avatar akun: foto profil bila ada, kalau tidak huruf pertama nama. */
export function AvatarAkun({ nama, versi, size = 30 }: { nama: string | undefined; versi: number | null | undefined; size?: number }) {
  const url = useFotoProfil(versi)
  return (
    <Avatar size={size} src={url ?? undefined} alt={nama ? `Foto ${nama}` : 'Foto profil'} style={{ background: '#15803d' }}>
      {nama?.slice(0, 1).toUpperCase()}
    </Avatar>
  )
}
