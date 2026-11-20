import { useQuery } from '@tanstack/react-query'
import { Image, Skeleton, Space, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { laporanApi } from '@/api/endpoints'

/**
 * Foto diambil dengan fetch (token di header) lalu ditampilkan lewat object URL, karena tag gambar biasa
 * tidak bisa mengirim Authorization. URL dilepas saat komponen hilang supaya memori tidak bocor.
 */
function FotoSatu({ id, jenis }: { id: string; jenis: string }) {
  const q = useQuery({ queryKey: ['foto', id], queryFn: () => laporanApi.foto(id), staleTime: Infinity, gcTime: 5 * 60_000 })
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
    <figure style={{ margin: 0 }}>
      <Image src={url} alt={`Foto ${jenis.toLowerCase()} catatan panen`} width={160} height={120} style={{ objectFit: 'cover', borderRadius: 8 }} />
      <figcaption style={{ fontSize: 12, opacity: 0.7, marginTop: 4 }}>{jenis === 'DETEKSI' ? 'Dengan kotak deteksi' : 'Asli'}</figcaption>
    </figure>
  )
}

export function FotoGaleri({ foto }: { foto: { id: string; jenis: string }[] }) {
  if (foto.length === 0) return <Typography.Text type="secondary">Tidak ada foto.</Typography.Text>
  return (
    <Image.PreviewGroup>
      <Space wrap size={12}>
        {foto.map((f) => (
          <FotoSatu key={f.id} id={f.id} jenis={f.jenis} />
        ))}
      </Space>
    </Image.PreviewGroup>
  )
}
