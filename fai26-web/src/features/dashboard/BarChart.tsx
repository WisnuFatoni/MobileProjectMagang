import { Typography } from 'antd'
import type { TrenHarian } from '@/api/types'
import { angka } from '@/lib/format'

/**
 * Diagram batang sederhana (SVG) untuk tren harian. Tanpa pustaka grafik supaya ringan dan mudah diuji.
 * Setiap batang punya label teks sehingga tidak bergantung pada warna saja.
 */
export function BarChart({ data }: { data: TrenHarian[] }) {
  if (data.length === 0) {
    return <Typography.Text type="secondary">Belum ada catatan dalam 7 hari terakhir.</Typography.Text>
  }
  const maks = Math.max(...data.map((d) => d.janjang), 1)
  const lebar = 560
  const tinggi = 220
  const dasar = tinggi - 36
  const area = dasar - 24
  const slot = lebar / data.length
  const batang = Math.min(48, slot * 0.6)

  return (
    <svg viewBox={`0 0 ${lebar} ${tinggi}`} role="img" aria-label="Janjang per hari, 7 hari terakhir" style={{ width: '100%', height: 'auto' }}>
      <line x1="0" y1={dasar} x2={lebar} y2={dasar} stroke="currentColor" strokeOpacity="0.2" />
      {data.map((d, i) => {
        const h = Math.max(2, (d.janjang / maks) * area)
        const x = i * slot + (slot - batang) / 2
        return (
          <g key={d.tanggal}>
            <rect x={x} y={dasar - h} width={batang} height={h} rx="6" fill="#15803d" />
            <text x={x + batang / 2} y={dasar - h - 6} textAnchor="middle" fontSize="12" fill="currentColor" fontWeight="600">
              {angka(d.janjang)}
            </text>
            <text x={x + batang / 2} y={dasar + 16} textAnchor="middle" fontSize="11" fill="currentColor" fillOpacity="0.65">
              {d.tanggal.slice(8, 10)}/{d.tanggal.slice(5, 7)}
            </text>
            <text x={x + batang / 2} y={dasar + 30} textAnchor="middle" fontSize="10" fill="currentColor" fillOpacity="0.5">
              {d.catatan} cat.
            </text>
          </g>
        )
      })}
    </svg>
  )
}
