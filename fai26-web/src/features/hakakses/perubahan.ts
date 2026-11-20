import type { HakAksesRole } from '@/api/types'

/** Hak yang sedang diedit: role_id menjadi himpunan kode permission. */
export type DraftHak = Record<number, Set<string>>

export interface PerubahanRole {
  roleId: number
  kode: string
  tambah: string[]
  cabut: string[]
}

/** Salinan hak tiap role dari server sebagai draft yang bisa diubah. */
export function dariServer(role: HakAksesRole[]): DraftHak {
  const d: DraftHak = {}
  for (const r of role) d[r.role_id] = new Set(r.permission)
  return d
}

/** Mengubah satu kotak centang tanpa memutasi draft lama. */
export function ubahKotak(draft: DraftHak, roleId: number, kode: string, nilai: boolean): DraftHak {
  const s = new Set(draft[roleId] ?? [])
  if (nilai) s.add(kode)
  else s.delete(kode)
  return { ...draft, [roleId]: s }
}

/** Role yang haknya berbeda dari server, beserta rincian yang ditambah dan dicabut (urut abjad supaya stabil). */
export function hitungPerubahan(asli: HakAksesRole[], draft: DraftHak): PerubahanRole[] {
  const out: PerubahanRole[] = []
  for (const r of asli) {
    const sekarang = draft[r.role_id] ?? new Set<string>()
    const awal = new Set(r.permission)
    const tambah = [...sekarang].filter((k) => !awal.has(k)).sort()
    const cabut = [...awal].filter((k) => !sekarang.has(k)).sort()
    if (tambah.length > 0 || cabut.length > 0) out.push({ roleId: r.role_id, kode: r.kode, tambah, cabut })
  }
  return out
}

/**
 * Kotak yang dikunci: ADMIN harus selalu memegang role.kelola (server menolak mencabutnya; dikunci di sini supaya
 * pengguna tidak perlu menemukan penolakannya setelah menekan Simpan).
 */
export function kotakDikunci(roleKode: string, izinKode: string): boolean {
  return roleKode === 'ADMIN' && izinKode === 'role.kelola'
}
