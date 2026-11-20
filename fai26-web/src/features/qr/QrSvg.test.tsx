import { render } from '@testing-library/react'
import jsQR from 'jsqr'
import { describe, expect, it } from 'vitest'
import { MARGIN_MODUL, modulQr, pathQr } from './modulQr'
import { QrSvg } from './QrSvg'

const ISI = 'FAI26|TPH|1|KP_STL|A12|01'

describe('QR TPH', () => {
  it('memakai level koreksi H dengan pola pencari di tiga sudut', () => {
    const { ukuran, gelap } = modulQr(ISI)
    expect((ukuran - 17) % 4).toBe(0) // ukuran QR selalu 4*versi + 17
    expect(ukuran).toBeGreaterThanOrEqual(29) // 26 byte pada level H butuh versi 4 ke atas
    for (let i = 0; i < 7; i++) {
      expect(gelap[0][i]).toBe(true) // sisi atas pola pencari kiri atas
      expect(gelap[i][0]).toBe(true)
      expect(gelap[0][ukuran - 1 - i]).toBe(true) // kanan atas
      expect(gelap[ukuran - 1][i]).toBe(true) // kiri bawah
    }
  })

  it('isi yang sama menghasilkan QR yang sama, isi berbeda berbeda', () => {
    expect(pathQr(modulQr(ISI).gelap)).toBe(pathQr(modulQr(ISI).gelap))
    expect(pathQr(modulQr(ISI).gelap)).not.toBe(pathQr(modulQr('FAI26|TPH|1|KP_STL|A12|02').gelap))
  })

  it('SVG punya zona tenang 4 modul, latar putih, dan label aksesibilitas', () => {
    const { container, getByRole } = render(<QrSvg teks={ISI} label="QR Blok A12 TPH 01" />)
    const svg = getByRole('img', { name: 'QR Blok A12 TPH 01' })
    const { ukuran } = modulQr(ISI)
    expect(MARGIN_MODUL).toBeGreaterThanOrEqual(4)
    expect(svg.getAttribute('viewBox')).toBe(`-4 -4 ${ukuran + 8} ${ukuran + 8}`)
    expect(container.querySelector('rect')?.getAttribute('fill')).toBe('#fff')
    expect(container.querySelector('path')?.getAttribute('fill')).toBe('#000')
  })

  it('dapat dibaca kembali oleh pembaca QR (hitam di atas putih, dengan margin)', () => {
    for (const isi of [ISI, 'FAI26|TPH|1|KP_STL|B07|123']) {
      const { ukuran, gelap } = modulQr(isi)
      const skala = 8
      const sisi = (ukuran + MARGIN_MODUL * 2) * skala
      const piksel = new Uint8ClampedArray(sisi * sisi * 4).fill(255)
      gelap.forEach((baris, r) =>
        baris.forEach((g, c) => {
          if (!g) return
          for (let y = 0; y < skala; y++)
            for (let x = 0; x < skala; x++) {
              const i = (((r + MARGIN_MODUL) * skala + y) * sisi + (c + MARGIN_MODUL) * skala + x) * 4
              piksel[i] = piksel[i + 1] = piksel[i + 2] = 0
            }
        }),
      )
      const hasil = jsQR(piksel, sisi, sisi)
      expect(hasil?.data).toBe(isi)
    }
  })
})
