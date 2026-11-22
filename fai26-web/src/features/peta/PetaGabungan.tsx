import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import { haluskan, type TitikPeta } from '@/features/ipd/jalur'

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string)

/** Satu jalur untuk digambar: titik, warna petugas, dan isi balon keterangan (teks biasa per baris; di sini di-escape). */
export interface JalurGabungan {
  id: string
  warna: string
  titik: TitikPeta[]
  keterangan: string[]
}

interface Props {
  jalur: JalurGabungan[]
  tinggi: number | string
  label: string
}

/**
 * Peta Leaflet berisi banyak jalur sekaligus: tiap inspeksi satu garis berwarna petugasnya, dengan titik mulai. Dimuat hanya saat
 * dibutuhkan (lazy). Tampilan menyesuaikan ke semua jalur yang tampil.
 */
export default function PetaGabungan({ jalur, tinggi, label }: Props) {
  const wadah = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const el = wadah.current
    if (!el) return
    const peta = L.map(el, { zoomControl: true, attributionControl: true })
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(peta)

    const kelompok = L.featureGroup()
    for (const j of jalur) {
      if (j.titik.length === 0) continue
      const garis = haluskan(j.titik)
      const isi = j.keterangan.map(esc).join('<br>')
      L.polyline(garis, { color: j.warna, weight: 4, opacity: 0.85, bubblingMouseEvents: false })
        .bindPopup(isi)
        .addTo(kelompok)
      L.circleMarker(garis[0], { radius: 6, color: '#fff', weight: 2, fillColor: j.warna, fillOpacity: 1, bubblingMouseEvents: false })
        .bindTooltip(esc(j.keterangan[0] ?? 'Mulai'))
        .addTo(kelompok)
    }
    kelompok.addTo(peta)
    if (kelompok.getLayers().length > 0) peta.fitBounds(kelompok.getBounds(), { padding: [24, 24], maxZoom: 18 })
    else peta.setView([-2.9, 104.7], 9)
    const t = setTimeout(() => peta.invalidateSize(), 250)
    return () => {
      clearTimeout(t)
      peta.remove()
    }
  }, [jalur])

  return <div ref={wadah} role="img" aria-label={label} style={{ height: tinggi, width: '100%', borderRadius: 8, overflow: 'hidden', border: '1px solid rgba(128,128,128,0.35)' }} />
}
