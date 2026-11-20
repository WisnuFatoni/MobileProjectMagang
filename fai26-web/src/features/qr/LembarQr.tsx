import type { Tph } from '@/api/types'
import { QrSvg } from './QrSvg'
import { TATA_LETAK, bagiHalaman, type TataLetak } from './tataLetak'

export function LembarQr({ tph, tataLetak }: { tph: Tph[]; tataLetak: TataLetak }) {
  const t = TATA_LETAK[tataLetak]
  const halaman = bagiHalaman(tph, t.perHalaman)
  return (
    <div className={`cetak__lembar cetak__lembar--${tataLetak}`} style={{ ['--qr-cm' as string]: `${t.cm}cm` }}>
      {halaman.map((h, i) => (
        <section key={i} className="cetak__halaman" aria-label={`Halaman ${i + 1}`}>
          {h.map((x) => (
            <figure key={x.tph_id} className="cetak__kartu">
              <QrSvg teks={x.qr_kode} label={`QR Blok ${x.blok_kode} TPH ${x.no_tph}`} className="cetak__qr" />
              <figcaption>
                <strong>Blok {x.blok_kode} / TPH {x.no_tph}</strong>
                <small>{x.qr_kode}</small>
              </figcaption>
            </figure>
          ))}
        </section>
      ))}
    </div>
  )
}
