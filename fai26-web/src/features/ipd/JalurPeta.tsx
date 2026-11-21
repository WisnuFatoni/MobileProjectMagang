import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { ExpandOutlined } from '@ant-design/icons'
import { Button, Modal, Space, Tag, Typography } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { IpdJejak, IpdPokokOut } from '@/api/types'
import { waktu } from '@/lib/format'
import { NAMA_JENIS, WARNA_JENIS, haluskan, keTitikPeta, pasangkanPokok, teksDurasi, teksJarak, type JenisPokok, type PokokDiPeta, type TitikPeta } from './jalur'

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string)

interface Props {
  jejak: IpdJejak
  pokok: IpdPokokOut[]
  /** Kode temuan menjadi jenisnya (LOSES atau TEMUAN), dari ringkasan inspeksi. */
  jenisKode: Map<string, string>
  /** Kode menjadi nama temuan, untuk keterangan di penanda. */
  namaKode: Map<string, string>
}

interface KanvasProps {
  titik: TitikPeta[]
  dipasang: PokokDiPeta[]
  namaKode: Map<string, string>
  tinggi: number | string
  label: string
  /** Dipanggil saat latar peta diklik (bukan penanda, bukan geser). Dipakai peta kecil untuk membuka tampilan besar. */
  saatKlikLatar?: () => void
}

/** Satu peta Leaflet: ubin OpenStreetMap, garis jalur, titik Mulai/Selesai, dan penanda tiap pokok. Dipakai di laci dan di jendela besar. */
function KanvasPeta({ titik, dipasang, namaKode, tinggi, label, saatKlikLatar }: KanvasProps) {
  const wadah = useRef<HTMLDivElement>(null)
  // Pemanggil bisa membuat fungsi baru tiap render; simpan di ref supaya peta tidak dibangun ulang karenanya.
  const klikRef = useRef(saatKlikLatar)
  klikRef.current = saatKlikLatar

  useEffect(() => {
    const el = wadah.current
    if (!el || titik.length === 0) return
    const peta = L.map(el, { zoomControl: true, attributionControl: true })
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(peta)

    const garis = haluskan(titik)
    const polyline = L.polyline(garis, { color: '#1d4ed8', weight: 4, opacity: 0.85, bubblingMouseEvents: false }).addTo(peta)
    L.circleMarker(garis[0], { radius: 8, color: '#fff', weight: 2, fillColor: '#16a34a', fillOpacity: 1, bubblingMouseEvents: false }).bindTooltip('Mulai').addTo(peta)
    if (garis.length > 1) {
      L.circleMarker(garis[garis.length - 1], { radius: 8, color: '#fff', weight: 2, fillColor: '#111827', fillOpacity: 1, bubblingMouseEvents: false }).bindTooltip('Selesai').addTo(peta)
    }
    const kelompok = L.featureGroup([polyline])
    for (const p of dipasang) {
      const temuan = p.pokok.temuan.length === 0 ? 'Bersih' : p.pokok.temuan.map((t) => `${esc(t.kode)} ${esc(namaKode.get(t.kode) ?? '')} x${t.jumlah}`).join('<br>')
      const m = L.circleMarker([p.titik.lat, p.titik.lon], { radius: 6, color: '#fff', weight: 1.5, fillColor: WARNA_JENIS[p.jenis], fillOpacity: 0.95, bubblingMouseEvents: false })
        .bindPopup(`<strong>Baris ${p.pokok.no_baris}, pokok ${p.pokok.no_pokok}</strong><br>${esc(NAMA_JENIS[p.jenis])}<br>${temuan}<br><small>${esc(waktu(p.pokok.dicatat_pada))}</small>`)
        .addTo(peta)
      kelompok.addLayer(m)
    }
    peta.fitBounds(kelompok.getBounds(), { padding: [24, 24], maxZoom: 19 })
    peta.on('click', () => klikRef.current?.())
    // Laci atau jendela bisa selesai membuka setelah peta dibuat: ukuran dihitung ulang supaya tidak terpotong.
    const t1 = setTimeout(() => peta.invalidateSize(), 250)
    const t2 = setTimeout(() => {
      peta.invalidateSize()
      peta.fitBounds(kelompok.getBounds(), { padding: [24, 24], maxZoom: 19 })
    }, 500)
    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
      peta.remove()
    }
  }, [titik, dipasang, namaKode])

  return (
    <div
      ref={wadah}
      role="img"
      aria-label={label}
      style={{
        height: tinggi,
        width: '100%',
        borderRadius: 8,
        overflow: 'hidden',
        border: '1px solid rgba(128,128,128,0.35)',
        cursor: saatKlikLatar ? 'zoom-in' : undefined,
      }}
    />
  )
}

/**
 * Peta jalur inspeksi (OpenStreetMap lewat Leaflet): garis yang dilalui inspektur, titik awal dan akhir, dan penanda tiap pokok yang
 * dicatat (merah = loses, kuning = temuan lain, hijau = bersih) di posisi inspektur saat mencatatnya. Dimuat hanya saat dibutuhkan.
 * Klik latar peta atau tombol "Perbesar" membuka tampilan besar.
 */
export default function JalurPeta({ jejak, pokok, jenisKode, namaKode }: Props) {
  const titik = useMemo(() => keTitikPeta(jejak.titik), [jejak])
  const dipasang = useMemo(() => pasangkanPokok(pokok, titik, jenisKode), [pokok, titik, jenisKode])
  const [besar, setBesar] = useState(false)
  const hitung = useMemo(() => {
    const h: Record<JenisPokok, number> = { LOSES: 0, LAIN: 0, BERSIH: 0 }
    for (const p of dipasang) h[p.jenis]++
    return h
  }, [dipasang])

  const ringkas = `${teksJarak(jejak.jarak_m)} ditempuh dalam ${teksDurasi(jejak.durasi_menit)}`
  const legenda = (
    <Space wrap size={[12, 4]}>
      <Tag color="red">Loses: {hitung.LOSES}</Tag>
      <Tag color="orange">Temuan lain: {hitung.LAIN}</Tag>
      <Tag color="green">Bersih: {hitung.BERSIH}</Tag>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Penanda = posisi saat pokok dicatat. Akurasi GPS di bawah tajuk sawit sekitar 5 sampai 15 meter.
      </Typography.Text>
    </Space>
  )

  return (
    <Space direction="vertical" size={8} style={{ width: '100%' }}>
      <Space wrap size={[8, 4]} style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Text>
          <strong>{teksJarak(jejak.jarak_m)}</strong> ditempuh • <strong>{teksDurasi(jejak.durasi_menit)}</strong> • {jejak.titik.length} titik GPS
        </Typography.Text>
        <Button size="small" icon={<ExpandOutlined />} onClick={() => setBesar(true)}>
          Perbesar peta
        </Button>
      </Space>
      <KanvasPeta titik={titik} dipasang={dipasang} namaKode={namaKode} tinggi={340} label={`Peta jalur inspeksi: ${ringkas}. Klik untuk memperbesar.`} saatKlikLatar={() => setBesar(true)} />
      {legenda}
      <Modal
        open={besar}
        onCancel={() => setBesar(false)}
        footer={null}
        centered
        destroyOnClose
        width="min(1400px, 96vw)"
        title={`Jalur inspeksi: ${ringkas}`}
      >
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <KanvasPeta titik={titik} dipasang={dipasang} namaKode={namaKode} tinggi="calc(100vh - 240px)" label={`Peta jalur inspeksi diperbesar: ${ringkas}`} />
          {legenda}
        </Space>
      </Modal>
    </Space>
  )
}
