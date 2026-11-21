import { describe, expect, it } from 'vitest'
import { ZOOM_MAKS, awal, dariRasio, daerahSumber, jepit, jepitSkala, keRasio, skalaMin, zoomDi } from './pemotong'

const S = 280

describe('pemotong foto profil', () => {
  it('awal: gambar menutupi kotak dan berada di tengah', () => {
    const p = awal(1600, 1000, S) // skala 0,28; lebar 448; kelebihan 168 dibagi dua
    expect(p.s).toBeCloseTo(0.28, 5)
    expect(p.tx).toBeCloseTo(-84, 5)
    expect(p.ty).toBeCloseTo(0, 5)
  })

  it('geser dijepit supaya tidak ada celah', () => {
    const p = awal(1600, 1000, S)
    expect(jepit({ ...p, tx: 50 }, 1600, 1000, S).tx).toBe(0)
    expect(jepit({ ...p, tx: -999 }, 1600, 1000, S).tx).toBeCloseTo(S - 1600 * p.s, 5)
    expect(jepit({ ...p, ty: 30 }, 1600, 1000, S).ty).toBe(0) // tinggi pas: tidak ada ruang geser vertikal
  })

  it('skala dibatasi antara minimum dan ZOOM_MAKS kali minimum', () => {
    const min = skalaMin(1600, 1000, S)
    expect(jepitSkala(0.001, 1600, 1000, S)).toBeCloseTo(min, 6)
    expect(jepitSkala(99, 1600, 1000, S)).toBeCloseTo(min * ZOOM_MAKS, 6)
  })

  it('zoom menjaga titik di bawah penunjuk tetap di tempat dan kotak tetap tertutup', () => {
    const p = awal(2000, 2000, S)
    const px = 200
    const py = 90
    const gx = (px - p.tx) / p.s
    const gy = (py - p.ty) / p.s
    const z = zoomDi(p, p.s * 2, px, py, 2000, 2000, S)
    expect(z.s).toBeCloseTo(p.s * 2, 6)
    expect((px - z.tx) / z.s).toBeCloseTo(gx, 3)
    expect((py - z.ty) / z.s).toBeCloseTo(gy, 3)
    expect(z.tx).toBeLessThanOrEqual(1e-9)
    expect(z.tx + 2000 * z.s).toBeGreaterThanOrEqual(S - 1e-9)
  })

  it('rasio penggeser bolak-balik dan daerah sumber sesuai yang terlihat', () => {
    expect(keRasio(skalaMin(1600, 1000, S), 1600, 1000, S)).toBeCloseTo(0, 6)
    expect(dariRasio(1, 1600, 1000, S)).toBeCloseTo(skalaMin(1600, 1000, S) * ZOOM_MAKS, 6)
    expect(keRasio(dariRasio(0.4, 1600, 1000, S), 1600, 1000, S)).toBeCloseTo(0.4, 6)
    const d = daerahSumber(awal(1600, 1000, S), S) // skala 0,28, tx = -84 -> x = 300, sisi = 1000
    expect(d.x).toBeCloseTo(300, 4)
    expect(d.y).toBeCloseTo(0, 4)
    expect(d.sisi).toBeCloseTo(1000, 4)
  })
})
