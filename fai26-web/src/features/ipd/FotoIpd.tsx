import { useQuery } from '@tanstack/react-query'
import { Image, Skeleton, Space, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { ipdApi } from '@/api/endpoints'
import type { IpdFotoRef } from '@/api/types'

/** Foto bukti IPD diambil dengan fetch (token di header) lalu ditampilkan lewat object URL, seperti foto panen. */
function FotoSatu({ foto }: { foto: IpdFotoRef }) {
  const q = useQuery({ queryKey: ['foto-ipd', foto.id], queryFn: () => ipdApi.foto(foto.id), staleTime: Infinity, gcTime: 5 * 60_000 })
  const [url, setUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!q.data) return
    const u = URL.createObjectURL(q.data)
    setUrl(u)
    return () => URL.revokeObjectURL(u)
  }, [q.data])

  if (q.isError) return <Typography.Text type="danger">Foto tidak bisa dimuat</Typography.Text>
  if (!url) return <Skeleton.Image active style={{ width: 160, height: 120 }} />
  return (
    <figure style={{ margin: 0, width: 160 }}>
      <Image src={url} alt={foto.keterangan ?? 'Foto bukti inspeksi'} width={160} height={120} style={{ objectFit: 'cover', borderRadius: 8 }} />
      <figcaption style={{ fontSize: 12, opacity: 0.75, marginTop: 4 }}>{foto.keterangan || 'Tanpa keterangan'}</figcaption>
    </figure>
  )
}

export function FotoIpd({ foto }: { foto: IpdFotoRef[] }) {
  if (foto.length === 0) return <Typography.Text type="secondary">Tidak ada foto.</Typography.Text>
  return (
    <Image.PreviewGroup>
      <Space wrap size={12} align="start">
        {foto.map((f) => (
          <FotoSatu key={f.id} foto={f} />
        ))}
      </Space>
    </Image.PreviewGroup>
  )
}
