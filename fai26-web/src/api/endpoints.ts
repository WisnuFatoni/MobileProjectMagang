import { api } from './http'
import type {
  AuditItem,
  Blok,
  BlokInput,
  Divisi,
  DivisiInput,
  FilterAudit,
  FilterPanen,
  HakAkses,
  Halaman,
  IdDibuat,
  Ok,
  OkQr,
  PanenDetail,
  PanenRingkas,
  Pemanen,
  PemanenInput,
  Pengguna,
  PenggunaInput,
  Perangkat,
  Ringkasan,
  ProduktivitasHarian,
  Role,
  Tph,
  TphInput,
  TphMassalInput,
} from './types'

export const divisiApi = {
  daftar: () => api.get<Divisi[]>('/admin/divisi'),
  buat: (d: DivisiInput) => api.post<IdDibuat>('/admin/divisi', d),
  ubah: (id: number, d: DivisiInput) => api.put<Ok>(`/admin/divisi/${id}`, d),
  hapus: (id: number) => api.hapus<Ok>(`/admin/divisi/${id}`),
}

export const blokApi = {
  daftar: () => api.get<Blok[]>('/admin/blok'),
  buat: (d: BlokInput) => api.post<IdDibuat>('/admin/blok', d),
  ubah: (id: number, d: BlokInput) => api.put<OkQr>(`/admin/blok/${id}`, d),
  hapus: (id: number) => api.hapus<Ok>(`/admin/blok/${id}`),
}

export const tphApi = {
  daftar: (blokId?: number) => api.get<Tph[]>('/admin/tph', { blok_id: blokId }),
  buat: (d: TphInput) => api.post<IdDibuat>('/admin/tph', d),
  massal: (d: TphMassalInput) => api.post<{ dibuat: number; id: number[] }>('/admin/tph/massal', d),
  ubah: (id: number, d: TphInput) => api.put<OkQr>(`/admin/tph/${id}`, d),
  hapus: (id: number) => api.hapus<Ok>(`/admin/tph/${id}`),
}

export const pemanenApi = {
  daftar: () => api.get<Pemanen[]>('/admin/pemanen'),
  buat: (d: PemanenInput) => api.post<IdDibuat>('/admin/pemanen', d),
  ubah: (id: number, d: PemanenInput) => api.put<Ok>(`/admin/pemanen/${id}`, d),
  hapus: (id: number) => api.hapus<Ok>(`/admin/pemanen/${id}`),
}

export const penggunaApi = {
  role: () => api.get<Role[]>('/admin/peran'),
  daftar: () => api.get<Pengguna[]>('/admin/pengguna'),
  buat: (d: PenggunaInput) => api.post<IdDibuat>('/admin/pengguna', d),
  ubah: (id: number, d: PenggunaInput) => api.put<Ok>(`/admin/pengguna/${id}`, d),
  resetPassword: (id: number, password: string) => api.post<Ok>(`/admin/pengguna/${id}/reset-password`, { password }),
}

export const perangkatApi = {
  daftar: () => api.get<Perangkat[]>('/admin/perangkat'),
  atur: (id: string, aktif: boolean) => api.put<Ok>(`/admin/perangkat/${id}`, { aktif }),
}

export const laporanApi = {
  ringkasan: (tanggal?: string) => api.get<Ringkasan>('/admin/ringkasan', { tanggal }),
  produktivitas: (tanggal?: string) => api.get<ProduktivitasHarian>('/admin/produktivitas', { tanggal }),
  panen: (f: FilterPanen) => api.get<Halaman<PanenRingkas>>('/admin/panen', { ...f }),
  detailPanen: (id: string) => api.get<PanenDetail>(`/admin/panen/${id}`),
  foto: (id: string) => api.blob(`/admin/foto/${id}`),
  audit: (f: FilterAudit) => api.get<Halaman<AuditItem>>('/admin/audit', { ...f }),
}

export const akunApi = {
  /** Mengganti password sendiri. Sesi di perangkat lain dicabut server; perangkat ini tetap masuk. */
  gantiPassword: (passwordLama: string, passwordBaru: string) =>
    api.post<Ok>('/auth/password', { password_lama: passwordLama, password_baru: passwordBaru }),
}

export const hakAksesApi = {
  daftar: () => api.get<HakAkses>('/admin/hak-akses'),
  /** Mengganti SELURUH hak satu role dengan daftar ini. */
  ubah: (roleId: number, permission: string[]) => api.put<Ok>(`/admin/hak-akses/${roleId}`, { permission }),
}
