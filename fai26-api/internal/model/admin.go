package model

import "time"

// ---- Master (dikelola lewat web admin) ----------------------------------------------------------
// PUT menggantikan semua kolom yang bisa diubah: kolom yang tidak dikirim menjadi kosong.

type DivisiInput struct {
	Kode   string `json:"kode"`
	Nama   string `json:"nama"`
	ErpOid *int64 `json:"erp_oid"`
	Aktif  *bool  `json:"aktif"`
	// AsistenUserID: asisten divisi (akun aktif berperan ASISTEN di unit ini); null berarti belum diatur. Dipakai IPD untuk
	// mengisi asisten otomatis. Saat diganti, inspeksi IPD divisi ini yang masih MENUNGGU ikut pindah ke asisten baru.
	AsistenUserID *int64 `json:"asisten_user_id"`
}

type BlokInput struct {
	DivisiID   *int64   `json:"divisi_id"`
	ErpOid     int64    `json:"erp_oid"`
	Kode       string   `json:"kode"`
	Nama       *string  `json:"nama"`
	LuasHa     *float64 `json:"luas_ha"`
	TahunTanam *int64   `json:"tahun_tanam"`
	Sph        *float64 `json:"sph"`
	Aktif      *bool    `json:"aktif"`
}

type TphInput struct {
	BlokID int64    `json:"blok_id"`
	NoTph  string   `json:"no_tph"`
	Lat    *float64 `json:"lat"`
	Lon    *float64 `json:"lon"`
	Aktif  *bool    `json:"aktif"`
}

// TphMassalInput membuat TPH bernomor berurutan dalam satu blok (contoh dari 1 sampai 20 lebar 2 menjadi 01..20).
type TphMassalInput struct {
	BlokID int64 `json:"blok_id"`
	Dari   int   `json:"dari"`
	Sampai int   `json:"sampai"`
	Lebar  int   `json:"lebar"`
}

type PemanenAdminInput struct {
	ErpHrmbCode int64 `json:"erp_hrmb_code"`
	// MandorHrmbCode adalah kode HRMB mandor kelompok pemanen ini (untuk header HVDM). Kosong berarti belum diisi.
	MandorHrmbCode *int64  `json:"mandor_hrmb_code"`
	Kode           *string `json:"kode"`
	Nama           string  `json:"nama"`
	Aktif          *bool   `json:"aktif"`
}

// ---- Pengguna dan perangkat ---------------------------------------------------------------------

type PenggunaInput struct {
	Username string   `json:"username"`
	Nama     string   `json:"nama"`
	Password string   `json:"password"`
	Aktif    *bool    `json:"aktif"`
	Role     []string `json:"role"`
	// Untuk akun mandor: kode HRMB mandor di ERP dan kode afdeling (divisi.kode). Boleh kosong untuk akun lain.
	MandorHrmbCode *int64  `json:"mandor_hrmb_code"`
	AfdelingKode   *string `json:"afdeling_kode"`
}

type ResetPasswordInput struct {
	Password string `json:"password"`
}

type PenggunaAdmin struct {
	UserID         int64      `json:"user_id"`
	Username       string     `json:"username"`
	Nama           string     `json:"nama"`
	Aktif          bool       `json:"aktif"`
	Role           []string   `json:"role"`
	LoginTerakhir  *time.Time `json:"login_terakhir"`
	Terkunci       bool       `json:"terkunci"`
	WajibGanti     bool       `json:"wajib_ganti_password"`
	MandorHrmbCode *int64     `json:"mandor_hrmb_code"`
	AfdelingKode   *string    `json:"afdeling_kode"`
}

type PerangkatAdmin struct {
	DeviceID        string     `json:"device_id"`
	Nama            *string    `json:"nama"`
	OsVersi         *string    `json:"os_versi"`
	AppVersi        *string    `json:"app_versi"`
	Aktif           bool       `json:"aktif"`
	TerakhirSinkron *time.Time `json:"terakhir_sinkron"`
}

type PerangkatUbahInput struct {
	Aktif bool `json:"aktif"`
}

type RoleAdmin struct {
	RoleID    int64  `json:"role_id"`
	Kode      string `json:"kode"`
	Nama      string `json:"nama"`
	Deskripsi string `json:"deskripsi"`
}

// ---- Catatan panen dan audit (hanya baca) -------------------------------------------------------

type FilterPanen struct {
	Dari     string // YYYY-MM-DD
	Sampai   string // YYYY-MM-DD
	StatusEr string
	Cari     string // kode blok atau TPH
	Halaman  int
	PerHal   int
}

type PanenRingkas struct {
	ID             string    `json:"id"`
	TanggalPanen   string    `json:"tanggal_panen"`
	BlokKode       *string   `json:"blok_kode"`
	TphKode        *string   `json:"tph_kode"`
	TotalJanjang   int64     `json:"total_janjang"`
	BrondolanKg    float64   `json:"brondolan_kg"`
	StatusErp      string    `json:"status_erp"`
	StatusLokasi   string    `json:"status_lokasi"`
	JarakKeTphM    *float64  `json:"jarak_ke_tph_m"`
	GpsMock        bool      `json:"gps_mock"`
	Pencatat       *string   `json:"pencatat"`
	Penyetuju      *string   `json:"penyetuju"`
	DiterimaServer time.Time `json:"diterima_server"`
}

type HalamanPanen struct {
	Item    []PanenRingkas `json:"item"`
	Total   int64          `json:"total"`
	Halaman int            `json:"halaman"`
	PerHal  int            `json:"per_halaman"`
}

type GradingAdmin struct {
	KelasKode string  `json:"kelas_kode"`
	ErpKolom  *string `json:"erp_kolom"`
	JmlAI     int64   `json:"jml_ai"`
	JmlFinal  int64   `json:"jml_final"`
	Manual    bool    `json:"diubah_manual"`
}

type PemanenPanenAdmin struct {
	HrmbCode   *int64  `json:"hrmb_code"`
	Nama       *string `json:"nama"`
	JjgAlokasi *int64  `json:"jjg_alokasi"`
}

type FotoAdmin struct {
	ID    string `json:"id"`
	Jenis string `json:"jenis"`
}

type ImporLogAdmin struct {
	Waktu time.Time `json:"waktu_impor"`
	Hasil string    `json:"hasil"`
	Pesan *string   `json:"pesan_error"`
}

type PanenDetail struct {
	PanenRingkas
	Rotasi        *int64              `json:"rotasi"`
	KondisiCuaca  *int64              `json:"kondisi_cuaca"`
	Asm3Oid       int64               `json:"asm3_oid"`
	GpsLat        *float64            `json:"gps_lat"`
	GpsLon        *float64            `json:"gps_lon"`
	GpsAkurasiM   *float64            `json:"gps_akurasi_m"`
	ModelVersi    *string             `json:"model_versi"`
	Catatan       *string             `json:"catatan"`
	DibuatPada    time.Time           `json:"dibuat_pada"`
	DisetujuiPada *time.Time          `json:"disetujui_pada"`
	DeviceID      string              `json:"device_id"`
	Grading       []GradingAdmin      `json:"grading"`
	Pemanen       []PemanenPanenAdmin `json:"pemanen"`
	Foto          []FotoAdmin         `json:"foto"`
	ImporLog      []ImporLogAdmin     `json:"impor_log"`
}

type FilterAudit struct {
	Tabel   string
	Aksi    string
	Dari    string
	Sampai  string
	Cari    string // username atau baris_id
	Halaman int
	PerHal  int
}

type AuditAdmin struct {
	AuditID     string     `json:"audit_id"`
	Tabel       string     `json:"tabel"`
	BarisID     string     `json:"baris_id"`
	Aksi        string     `json:"aksi"`
	NilaiLama   *string    `json:"nilai_lama"`
	NilaiBaru   *string    `json:"nilai_baru"`
	UserID      *int64     `json:"user_id"`
	Username    *string    `json:"username"`
	DeviceID    *string    `json:"device_id"`
	Waktu       time.Time  `json:"waktu"`
	WaktuServer *time.Time `json:"waktu_server"`
	Ip          *string    `json:"ip"`
	Keterangan  *string    `json:"keterangan"`
}

type HalamanAudit struct {
	Item    []AuditAdmin `json:"item"`
	Total   int64        `json:"total"`
	Halaman int          `json:"halaman"`
	PerHal  int          `json:"per_halaman"`
}

type RingkasanAdmin struct {
	CatatanHariIni    int64            `json:"catatan_hari_ini"`
	JanjangHariIni    int64            `json:"janjang_hari_ini"`
	CatatanPerStatus  map[string]int64 `json:"catatan_per_status"`
	PerluPerhatian    int64            `json:"perlu_perhatian"`
	PerangkatAktif    int64            `json:"perangkat_aktif"`
	PenggunaAktif     int64            `json:"pengguna_aktif"`
	TphTanpaKoordinat int64            `json:"tph_tanpa_koordinat"`
	TphAktif          int64            `json:"tph_aktif"`
	Tren7Hari         []TrenHarian     `json:"tren_7_hari"`
}

type TrenHarian struct {
	Tanggal string `json:"tanggal"`
	Catatan int64  `json:"catatan"`
	Janjang int64  `json:"janjang"`
}

// ProduktivitasPemanen adalah ringkasan satu pemanen pada satu hari: dasar monitoring produktivitas.
// Premi (rupiah) sengaja tidak dihitung di sini: tarif dan basis premi dimiliki ERP.
type ProduktivitasPemanen struct {
	Peringkat     int     `json:"peringkat"`
	HrmbCode      int64   `json:"hrmb_code"`
	Nama          string  `json:"nama"`
	Catatan       int64   `json:"catatan"`
	Janjang       int64   `json:"janjang"`
	JanjangMatang int64   `json:"janjang_matang"`
	PersenMatang  float64 `json:"persen_matang"`
	BrondolanKg   float64 `json:"brondolan_kg"`
	Menunggu      int64   `json:"menunggu_persetujuan"`
}

// ProduktivitasHarian adalah ranking produktivitas pemanen satu hari di satu unit.
type ProduktivitasHarian struct {
	Tanggal       string                 `json:"tanggal"`
	JumlahPemanen int64                  `json:"jumlah_pemanen"`
	TotalJanjang  int64                  `json:"total_janjang"`
	RataJanjang   float64                `json:"rata_janjang"`
	Pemanen       []ProduktivitasPemanen `json:"pemanen"`
}
