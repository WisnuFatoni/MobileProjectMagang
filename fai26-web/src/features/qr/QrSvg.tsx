import { useMemo } from 'react'
import { MARGIN_MODUL, modulQr, pathQr } from './modulQr'

/**
 * QR sebagai SVG murni (tajam di cetakan berapa pun ukurannya, tanpa innerHTML). Hitam di atas putih dengan
 * zona tenang 4 modul. Ukuran lewat CSS (mis. width: 15cm).
 */
export function QrSvg({ teks, label, className, style }: { teks: string; label?: string; className?: string; style?: React.CSSProperties }) {
  const { ukuran, d } = useMemo(() => {
    const m = modulQr(teks)
    return { ukuran: m.ukuran, d: pathQr(m.gelap) }
  }, [teks])
  const sisi = ukuran + MARGIN_MODUL * 2
  return (
    <svg
      viewBox={`${-MARGIN_MODUL} ${-MARGIN_MODUL} ${sisi} ${sisi}`}
      role="img"
      aria-label={label ?? `QR ${teks}`}
      shapeRendering="crispEdges"
      className={className}
      style={style}
    >
      <rect x={-MARGIN_MODUL} y={-MARGIN_MODUL} width={sisi} height={sisi} fill="#fff" />
      <path d={d} fill="#000" />
    </svg>
  )
}
