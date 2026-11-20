import { ArrowLeftOutlined, PrinterOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Radio, Space, Spin, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { tphApi } from '@/api/endpoints'
import { ErrorState } from '@/components/ErrorState'
import { LembarQr } from './LembarQr'
import { TATA_LETAK, type TataLetak } from './tataLetak'
import './cetak.css'

/**
 * Halaman cetak QR TPH: ?blok=ID (semua TPH satu blok) atau ?ids=1,2,3 (TPH terpilih).
 * Spesifikasi: level koreksi H, margin 4 modul, hitam di atas putih, ukuran minimal 12 cm, teks manusia di bawah.
 */
export function CetakQrPage() {
  const [params] = useSearchParams()
  const blok = Number(params.get('blok')) || undefined
  const ids = useMemo(() => new Set((params.get('ids') ?? '').split(',').map(Number).filter(Boolean)), [params])
  const [tataLetak, setTataLetak] = useState<TataLetak>('besar')

  const q = useQuery({ queryKey: ['tph', blok ?? 'semua'], queryFn: () => tphApi.daftar(blok) })
  const tph = useMemo(() => (q.data ?? []).filter((t) => t.aktif && (ids.size === 0 || ids.has(t.tph_id))), [q.data, ids])

  return (
    <div className="cetak">
      <div className="cetak__kontrol">
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Link to={blok ? `/tph?blok=${blok}` : '/tph'}>
              <Button icon={<ArrowLeftOutlined />}>Kembali</Button>
            </Link>
            <Button type="primary" icon={<PrinterOutlined />} onClick={() => window.print()} disabled={tph.length === 0}>
              Cetak {tph.length} QR
            </Button>
          </Space>
          <Radio.Group value={tataLetak} onChange={(e) => setTataLetak(e.target.value as TataLetak)} optionType="button" buttonStyle="solid">
            {(Object.keys(TATA_LETAK) as TataLetak[]).map((k) => (
              <Radio.Button key={k} value={k}>{TATA_LETAK[k].judul}</Radio.Button>
            ))}
          </Radio.Group>
          <Typography.Text type="secondary">{TATA_LETAK[tataLetak].ket}</Typography.Text>
          {tataLetak === 'kecil' && <Alert type="warning" showIcon message="Ukuran ini di bawah minimal 12 cm. Jangan dipasang di lapangan." />}
          <Alert
            type="info"
            showIcon
            message="Tips cetak"
            description="Pakai kertas putih atau stiker laminasi anti-UV (bukan bahan sangat mengkilap), skala 100%, tanpa margin otomatis browser (pilih 'Margins: None' atau 'Default' dengan skala 100%)."
          />
        </Space>
      </div>

      {q.isLoading && <Spin style={{ display: 'block', margin: '48px auto' }} />}
      {q.isError && <ErrorState error={q.error} onCoba={() => void q.refetch()} />}
      {q.isSuccess && tph.length === 0 && <Alert type="warning" showIcon message="Tidak ada TPH aktif untuk dicetak." />}
      {tph.length > 0 && <LembarQr tph={tph} tataLetak={tataLetak} />}
    </div>
  )
}
