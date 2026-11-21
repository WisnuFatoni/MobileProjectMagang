import { RotateRightOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons'
import { Button, Slider, Space } from 'antd'
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react'
import { awal, dariRasio, daerahSumber, jepit, keRasio, zoomDi, type Posisi } from './pemotong'

/** Sisi kotak potong di layar (piksel). */
const S = 280
/** Sisi foto hasil (piksel); server mengecilkan lagi ke 384. */
const KELUAR = 768

export interface PemotongHandle {
  /** Foto hasil potongan (JPEG persegi) sesuai yang tampak di dalam lingkaran. */
  hasil: () => Promise<Blob>
}

/**
 * Pemotong foto profil: geser gambar dengan tetikus atau jari, zoom dengan penggeser atau roda tetikus, putar 90 derajat. Yang
 * berada di dalam lingkaran menjadi foto profil. Gambar selalu menutupi lingkaran penuh.
 */
export const PemotongFoto = forwardRef<PemotongHandle, { berkas: File }>(function PemotongFoto({ berkas }, ref) {
  const kanvas = useRef<HTMLCanvasElement>(null)
  const [sumber, setSumber] = useState<HTMLCanvasElement | null>(null)
  const [pos, setPos] = useState<Posisi | null>(null)
  const seret = useRef<{ x: number; y: number } | null>(null)

  // Gambar dimuat sekali ke kanvas sumber (orientasi EXIF diterapkan oleh createImageBitmap); putar membuat kanvas baru.
  useEffect(() => {
    let batal = false
    void (async () => {
      const bmp = await createImageBitmap(berkas)
      if (batal) {
        bmp.close()
        return
      }
      const k = document.createElement('canvas')
      k.width = bmp.width
      k.height = bmp.height
      k.getContext('2d')?.drawImage(bmp, 0, 0)
      bmp.close()
      setSumber(k)
      setPos(awal(k.width, k.height, S))
    })()
    return () => {
      batal = true
    }
  }, [berkas])

  const gambar = useCallback(() => {
    const el = kanvas.current
    const c = el?.getContext('2d')
    if (!el || !c || !sumber || !pos) return
    const dpr = window.devicePixelRatio || 1
    el.width = S * dpr
    el.height = S * dpr
    c.setTransform(dpr, 0, 0, dpr, 0, 0)
    c.fillStyle = '#000'
    c.fillRect(0, 0, S, S)
    c.imageSmoothingQuality = 'high'
    c.drawImage(sumber, pos.tx, pos.ty, sumber.width * pos.s, sumber.height * pos.s)
    // Luar lingkaran digelapkan, garis bantu sepertiga di dalamnya.
    c.save()
    c.fillStyle = 'rgba(0,0,0,0.65)'
    c.beginPath()
    c.rect(0, 0, S, S)
    c.arc(S / 2, S / 2, S / 2 - 1, 0, Math.PI * 2, true)
    c.fill('evenodd')
    c.restore()
    c.save()
    c.beginPath()
    c.arc(S / 2, S / 2, S / 2 - 1, 0, Math.PI * 2)
    c.clip()
    c.strokeStyle = 'rgba(255,255,255,0.28)'
    c.lineWidth = 1
    for (const f of [1 / 3, 2 / 3]) {
      c.beginPath()
      c.moveTo(S * f, 0)
      c.lineTo(S * f, S)
      c.moveTo(0, S * f)
      c.lineTo(S, S * f)
      c.stroke()
    }
    c.restore()
    c.strokeStyle = '#fff'
    c.lineWidth = 2
    c.beginPath()
    c.arc(S / 2, S / 2, S / 2 - 1, 0, Math.PI * 2)
    c.stroke()
  }, [sumber, pos])

  useEffect(gambar, [gambar])

  useImperativeHandle(
    ref,
    () => ({
      hasil: () =>
        new Promise<Blob>((ok, gagal) => {
          if (!sumber || !pos) return gagal(new Error('Gambar belum siap'))
          const d = daerahSumber(pos, S)
          const k = document.createElement('canvas')
          k.width = KELUAR
          k.height = KELUAR
          const c = k.getContext('2d')
          if (!c) return gagal(new Error('Peramban tidak bisa memproses gambar'))
          c.imageSmoothingQuality = 'high'
          c.drawImage(sumber, d.x, d.y, d.sisi, d.sisi, 0, 0, KELUAR, KELUAR)
          k.toBlob((b) => (b ? ok(b) : gagal(new Error('Gagal memproses gambar'))), 'image/jpeg', 0.92)
        }),
    }),
    [sumber, pos],
  )

  function titik(e: React.PointerEvent | React.WheelEvent) {
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top }
  }

  function putar() {
    if (!sumber) return
    const k = document.createElement('canvas')
    k.width = sumber.height
    k.height = sumber.width
    const c = k.getContext('2d')
    if (!c) return
    c.translate(k.width, 0)
    c.rotate(Math.PI / 2)
    c.drawImage(sumber, 0, 0)
    setSumber(k)
    setPos(awal(k.width, k.height, S))
  }

  const rasio = sumber && pos ? keRasio(pos.s, sumber.width, sumber.height, S) : 0
  const ubahZoom = (r: number) => {
    if (!sumber || !pos) return
    setPos(zoomDi(pos, dariRasio(r, sumber.width, sumber.height, S), S / 2, S / 2, sumber.width, sumber.height, S))
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
      <canvas
        ref={kanvas}
        role="img"
        aria-label="Area pemotong foto profil: geser gambar untuk mengatur posisi"
        style={{ width: S, height: S, borderRadius: 8, touchAction: 'none', cursor: seret.current ? 'grabbing' : 'grab', background: '#000' }}
        onPointerDown={(e) => {
          e.currentTarget.setPointerCapture(e.pointerId)
          seret.current = titik(e)
        }}
        onPointerMove={(e) => {
          if (!seret.current || !sumber || !pos) return
          const t = titik(e)
          setPos(jepit({ ...pos, tx: pos.tx + t.x - seret.current.x, ty: pos.ty + t.y - seret.current.y }, sumber.width, sumber.height, S))
          seret.current = t
        }}
        onPointerUp={() => (seret.current = null)}
        onPointerCancel={() => (seret.current = null)}
        onWheel={(e) => {
          if (!sumber || !pos) return
          const t = titik(e)
          setPos(zoomDi(pos, pos.s * (e.deltaY < 0 ? 1.08 : 1 / 1.08), t.x, t.y, sumber.width, sumber.height, S))
        }}
      />
      <Space style={{ width: S }} align="center">
        <ZoomOutOutlined aria-hidden />
        <Slider
          style={{ width: 170, margin: '0 6px' }}
          min={0}
          max={1}
          step={0.01}
          value={rasio}
          tooltip={{ open: false }}
          onChange={ubahZoom}
          disabled={!sumber}
          aria-label="Zoom foto"
        />
        <ZoomInOutlined aria-hidden />
        <Button size="small" icon={<RotateRightOutlined />} onClick={putar} disabled={!sumber} aria-label="Putar 90 derajat" />
      </Space>
    </div>
  )
})
