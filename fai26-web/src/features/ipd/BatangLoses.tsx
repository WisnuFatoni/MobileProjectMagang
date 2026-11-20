import { Typography } from 'antd'
import type { IpdKodeAgg } from '@/api/types'
import { angka, desimal } from '@/lib/format'
import { warnaLoses } from './ipdTampil'

/**
 * Batang mendatar loses per kode temuan: panjang = persen pokok yang memuat kode itu dibagi pokok diperiksa. Tiap batang berlabel
 * angka (kode, persen, pokok, jumlah) sehingga tidak bergantung pada warna saja. Kode tanpa temuan tetap tampil dengan 0%.
 */
export function BatangLoses({ data }: { data: IpdKodeAgg[] }) {
  if (data.length === 0) return <Typography.Text type="secondary">Belum ada data.</Typography.Text>
  const maks = Math.max(...data.map((k) => k.persen_pokok), 1)
  return (
    <ul role="list" aria-label="Loses per kode temuan" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
      {data.map((k) => (
        <li key={k.kode} style={{ display: 'grid', gridTemplateColumns: '150px 1fr 180px', gap: 12, alignItems: 'center', padding: '6px 0' }}>
          <span>
            <strong>{k.kode}</strong> <span style={{ opacity: 0.75 }}>{k.nama}</span>
          </span>
          <span aria-hidden="true" style={{ background: 'rgba(0,0,0,0.06)', borderRadius: 6, height: 14, display: 'block' }}>
            <span style={{ display: 'block', height: 14, borderRadius: 6, width: `${(k.persen_pokok / maks) * 100}%`, background: warnaLoses(k.persen_pokok), minWidth: k.persen_pokok > 0 ? 4 : 0 }} />
          </span>
          <span style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
            <strong>{desimal(k.persen_pokok)}%</strong> <span style={{ opacity: 0.7 }}>• {angka(k.pokok)} pokok • jumlah {angka(k.jumlah)}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}
