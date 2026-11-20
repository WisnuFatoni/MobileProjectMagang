// Bentuk data dari fai26-api. Sumber kebenaran: ../../fai26-api/docs/API.md (dan openapi.yaml).

export interface Profil {
  user_id: number
  unit_id: number
  unit_kode: string
  username: string
  nama: string
  role: string[]
  permission: string[]
  wajib_ganti_password: boolean
}

export interface LoginResponse {
  access_token: string
  access_expires: string
  refresh_token: string
  profil: Profil
}

export interface RefreshResponse {
  access_token: string
  access_expires: string
  refresh_token: string
}

// ---- Master ----------------------------------------------------------------

export interface Divisi {
  divisi_id: number
  kode: string
  nama: string
  erp_oid: number | null
  aktif: boolean
  /** Asisten divisi (akun berperan ASISTEN); dipakai IPD untuk mengisi dan menentukan penyetuju. */
  asisten_user_id: number | null
  asisten_nama: string | null
  jumlah_blok: number
}

export interface Blok {
  blok_id: number
  divisi_id: number | null
  divisi_kode: string | null
  erp_oid: number
  kode: string
  nama: string | null
  luas_ha: number | null
  tahun_tanam: number | null
  sph: number | null
  aktif: boolean
  jumlah_tph: number
}

export interface Tph {
  tph_id: number
  blok_id: number
  blok_kode: string
  no_tph: string
  qr_kode: string
  lat: number | null
  lon: number | null
  aktif: boolean
}

export interface Pemanen {
  pemanen_id: number
  erp_hrmb_code: number
  mandor_hrmb_code: number | null
  kode: string | null
  nama: string
  aktif: boolean
}

export interface DivisiInput {
  kode: string
  nama: string
  erp_oid?: number | null
  aktif?: boolean
  asisten_user_id?: number | null
}

export interface BlokInput {
  kode: string
  erp_oid: number
  divisi_id?: number | null
  nama?: string | null
  luas_ha?: number | null
  tahun_tanam?: number | null
  sph?: number | null
  aktif?: boolean
}

export interface TphInput {
  blok_id: number
  no_tph: string
  lat?: number | null
  lon?: number | null
  aktif?: boolean
}

export interface TphMassalInput {
  blok_id: number
  dari: number
  sampai: number
  lebar: number
}

export interface PemanenInput {
  erp_hrmb_code: number
  mandor_hrmb_code?: number | null
  nama: string
  kode?: string | null
  aktif?: boolean
}

export interface Ok {
  status: string
}

export interface IdDibuat {
  id: number
}

export interface OkQr {
  status: string
  qr_berubah: number | boolean
}

// ---- Pengguna dan perangkat -------------------------------------------------

export interface Role {
  role_id: number
  kode: string
  nama: string
  deskripsi: string
}

export interface Pengguna {
  user_id: number
  username: string
  nama: string
  aktif: boolean
  role: string[]
  login_terakhir: string | null
  terkunci: boolean
  wajib_ganti_password: boolean
  mandor_hrmb_code: number | null
  afdeling_kode: string | null
}

export interface PenggunaInput {
  username?: string
  nama: string
  password?: string
  role: string[]
  aktif?: boolean
  mandor_hrmb_code?: number | null
  afdeling_kode?: string | null
}

export interface Perangkat {
  device_id: string
  nama: string | null
  os_versi: string | null
  app_versi: string | null
  aktif: boolean
  terakhir_sinkron: string | null
}

// ---- Laporan -----------------------------------------------------------------

export interface TrenHarian {
  tanggal: string
  catatan: number
  janjang: number
}

export interface Ringkasan {
  catatan_hari_ini: number
  janjang_hari_ini: number
  catatan_per_status: Record<string, number>
  perlu_perhatian: number
  perangkat_aktif: number
  pengguna_aktif: number
  tph_tanpa_koordinat: number
  tph_aktif: number
  tren_7_hari: TrenHarian[]
}

/** Satu pemanen pada ranking produktivitas harian (hanya janjang dan mutu; premi dihitung di ERP). */
export interface PemanenProduktif {
  peringkat: number
  hrmb_code: number
  nama: string
  catatan: number
  janjang: number
  janjang_matang: number
  persen_matang: number
  brondolan_kg: number
  menunggu_persetujuan: number
}

export interface ProduktivitasHarian {
  tanggal: string
  jumlah_pemanen: number
  total_janjang: number
  rata_janjang: number
  pemanen: PemanenProduktif[]
}

export type StatusErp = 'BARU' | 'DIBACA' | 'DIIMPOR' | 'DITOLAK'
export type StatusLokasi = 'DI_TPH' | 'DI_LUAR' | 'AKURASI_RENDAH' | 'TANPA_GPS' | 'TPH_TANPA_KOORDINAT'

export interface PanenRingkas {
  id: string
  tanggal_panen: string
  blok_kode: string | null
  tph_kode: string | null
  total_janjang: number
  brondolan_kg: number
  status_erp: StatusErp
  status_lokasi: StatusLokasi
  jarak_ke_tph_m: number | null
  gps_mock: boolean
  pencatat: string | null
  penyetuju: string | null
  diterima_server: string
}

export interface Grading {
  kelas_kode: string
  erp_kolom: string | null
  jml_ai: number
  jml_final: number
  diubah_manual: boolean
}

export interface PanenDetail extends PanenRingkas {
  rotasi: number | null
  kondisi_cuaca: number | null
  asm3_oid: number
  gps_lat: number | null
  gps_lon: number | null
  gps_akurasi_m: number | null
  model_versi: string | null
  catatan: string | null
  dibuat_pada: string
  disetujui_pada: string | null
  device_id: string
  grading: Grading[]
  pemanen: { hrmb_code: number | null; nama: string | null; jjg_alokasi: number | null }[]
  foto: { id: string; jenis: string }[]
  impor_log: { waktu_impor: string; hasil: string; pesan_error: string | null }[]
}

export interface FilterPanen {
  dari?: string
  sampai?: string
  status_erp?: string
  cari?: string
  halaman?: number
  per_halaman?: number
}

export interface Halaman<T> {
  item: T[]
  total: number
  halaman: number
  per_halaman: number
}

export interface AuditItem {
  audit_id: string
  tabel: string
  baris_id: string
  aksi: string
  nilai_lama: string | null
  nilai_baru: string | null
  user_id: number | null
  username: string | null
  device_id: string | null
  waktu: string
  waktu_server: string | null
  ip: string | null
  keterangan: string | null
}

export interface FilterAudit {
  tabel?: string
  aksi?: string
  dari?: string
  sampai?: string
  cari?: string
  halaman?: number
  per_halaman?: number
}

export interface HakAksesRole {
  role_id: number
  kode: string
  nama: string
  bawaan: boolean
  permission: string[]
}

export interface IzinKatalog {
  kode: string
  modul: string
  nama: string
}

export interface HakAkses {
  role: HakAksesRole[]
  permission: IzinKatalog[]
}

// ---- IPD (Inspeksi Panen Detail) ----------------------------------------------------------------

export type StatusIpd = 'MENUNGGU' | 'DISETUJUI' | 'DITOLAK'

export interface IpdKodeAgg {
  kode: string
  nama: string
  kelompok: 'BUAH' | 'BRONDOLAN' | 'PELEPAH'
  /** LOSES = memengaruhi hasil panen (buah, brondolan); TEMUAN = temuan lain (pelepah), tidak dihitung loses. */
  jenis: 'LOSES' | 'TEMUAN'
  pokok: number
  jumlah: number
  persen_pokok: number
}

export interface IpdRingkasan {
  pokok_diperiksa: number
  /** Pokok dengan temuan apa pun (loses atau temuan lain). */
  pokok_bertemuan: number
  persen_pokok_bertemuan: number
  total_temuan: number
  pokok_loses: number
  persen_pokok_loses: number
  total_loses: number
  pokok_temuan_lain: number
  persen_pokok_temuan_lain: number
  total_temuan_lain: number
  per_kode: IpdKodeAgg[]
}

export interface IpdFotoRef {
  id: string
  keterangan: string | null
  urut: number
}

export interface IpdBaris {
  id: string
  tanggal_inspeksi: string
  divisi_kode: string
  blok_kode: string | null
  pemanen_hrmb_code: number
  pemanen_nama: string | null
  ancak_no: string | null
  inspektur: string
  inspektur_nama: string
  mandor_blok_nama: string | null
  mandor_ancak_nama: string | null
  asisten_nama: string | null
  status: StatusIpd
  pesan_tolak: string | null
  catatan: string | null
  dibuat_pada: string
  disetujui_pada: string | null
  ringkasan: IpdRingkasan
  foto: IpdFotoRef[]
}

export interface IpdPokokOut {
  no_baris: number
  no_pokok: number
  temuan: { kode: string; jumlah: number; catatan: string | null }[]
}

export interface IpdDetail extends IpdBaris {
  pokok: IpdPokokOut[]
}

export interface FilterIpd {
  dari?: string
  sampai?: string
  status?: string
  divisi_kode?: string
  halaman?: number
  per_halaman?: number
}

export interface IpdKelompokAgg {
  kelompok: 'BUAH' | 'BRONDOLAN' | 'PELEPAH'
  pokok: number
  jumlah: number
  persen_pokok: number
}

export interface IpdGrupLoses {
  kunci: string
  nama: string
  inspeksi: number
  pokok_diperiksa: number
  pokok_bertemuan: number
  persen_pokok_bertemuan: number
  total_temuan: number
  pokok_loses: number
  persen_pokok_loses: number
  total_loses: number
  pokok_temuan_lain: number
  persen_pokok_temuan_lain: number
  total_temuan_lain: number
  temuan_per_kode: Record<string, number>
}

export interface IpdLaporan {
  dari: string
  sampai: string
  inspeksi: number
  pemanen: number
  ringkasan: IpdRingkasan
  per_kelompok: IpdKelompokAgg[]
  per_pemanen: IpdGrupLoses[]
  per_blok: IpdGrupLoses[]
  per_divisi: IpdGrupLoses[]
  per_tanggal: IpdGrupLoses[]
}

export interface FilterLosesIpd {
  dari?: string
  sampai?: string
  divisi_kode?: string
}
