package model

import (
	"errors"
	"fmt"
	"regexp"
	"time"
)

var ReUUID = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

var statusLokasiSah = map[string]bool{
	"DI_TPH": true, "DI_LUAR": true, "AKURASI_RENDAH": true, "TANPA_GPS": true, "TPH_TANPA_KOORDINAT": true,
}

var kelasSah = map[string]bool{
	"unripe": true, "underripe": true, "ripe": true, "overripe": true, "empty": true, "abnormal": true,
}

type GradingInput struct {
	KelasKode    string `json:"kelas_kode"`
	ErpKolom     string `json:"erp_kolom"`
	JmlAI        int    `json:"jml_ai"`
	JmlFinal     int    `json:"jml_final"`
	DiubahManual bool   `json:"diubah_manual"`
}

type PemanenInput struct {
	HrmbCode   int `json:"hrmb_code"`
	JjgAlokasi int `json:"jjg_alokasi"`
}

// PanenInput adalah satu catatan panen dari HP. Identitas pengguna diambil dari token, bukan dari isi ini.
type PanenInput struct {
	PanenID           string   `json:"panen_id"`
	UnitKode          string   `json:"unit_kode"`
	TanggalPanen      string   `json:"tanggal_panen"`
	Asm3Oid           int64    `json:"asm3_oid"`
	BlokKode          string   `json:"blok_kode"`
	TphKode           string   `json:"tph_kode"`
	Rotasi            *int     `json:"rotasi"`
	KondisiCuaca      *int     `json:"kondisi_cuaca"`
	BrondolanKg       float64  `json:"brondolan_kg"`
	GpsLat            *float64 `json:"gps_lat"`
	GpsLon            *float64 `json:"gps_lon"`
	GpsAkurasiM       *float64 `json:"gps_akurasi_m"`
	GpsWaktu          *string  `json:"gps_waktu"`
	GpsMock           bool     `json:"gps_mock"`
	JarakKeTphM       *float64 `json:"jarak_ke_tph_m"`
	StatusLokasi      string   `json:"status_lokasi"`
	ModelVersi        string   `json:"model_versi"`
	Catatan           string   `json:"catatan"`
	DibuatPada        string   `json:"dibuat_pada"`
	PencatatUsername  string   `json:"pencatat_username"`
	DisetujuiUsername string   `json:"disetujui_username"`
	DisetujuiPada     string   `json:"disetujui_pada"`
	// KirimUlang true berarti HP sengaja mengirim ulang catatan yang DITOLAK ERP setelah diperbaiki (panen_id sama).
	// Tanpa ini, catatan yang sudah ada selalu dijawab SUDAH_ADA tanpa diubah (aman untuk percobaan ulang jaringan).
	KirimUlang bool           `json:"kirim_ulang"`
	Grading    []GradingInput `json:"grading"`
	Pemanen    []PemanenInput `json:"pemanen"`
}

// ParseWaktu membaca waktu ISO 8601 dan mengembalikannya dalam UTC.
func ParseWaktu(s string) (time.Time, error) {
	for _, f := range []string{time.RFC3339Nano, time.RFC3339, "2006-01-02T15:04:05"} {
		if t, err := time.Parse(f, s); err == nil {
			return t.UTC(), nil
		}
	}
	return time.Time{}, fmt.Errorf("format waktu tidak dikenal: %q", s)
}

// Validasi memeriksa isi catatan. Galatnya berisi pesan yang aman ditampilkan ke pengguna.
func (p *PanenInput) Validasi() error {
	if !ReUUID.MatchString(p.PanenID) {
		return errors.New("panen_id harus UUID")
	}
	if p.UnitKode == "" {
		return errors.New("unit_kode wajib diisi")
	}
	if _, err := time.Parse("2006-01-02", p.TanggalPanen); err != nil {
		return errors.New("tanggal_panen harus YYYY-MM-DD")
	}
	if p.Asm3Oid <= 0 {
		return errors.New("asm3_oid (OID blok di ERP) wajib diisi")
	}
	if p.BrondolanKg < 0 {
		return errors.New("brondolan_kg tidak boleh negatif")
	}
	if !statusLokasiSah[p.StatusLokasi] {
		return errors.New("status_lokasi tidak dikenal")
	}
	if len(p.Grading) == 0 {
		return errors.New("grading tidak boleh kosong")
	}
	total := 0
	seen := map[string]bool{}
	for _, g := range p.Grading {
		if !kelasSah[g.KelasKode] {
			return errors.New("kelas_kode tidak dikenal: " + g.KelasKode)
		}
		if seen[g.KelasKode] {
			return errors.New("kelas_kode ganda: " + g.KelasKode)
		}
		seen[g.KelasKode] = true
		if g.JmlAI < 0 || g.JmlFinal < 0 {
			return errors.New("jumlah janjang tidak boleh negatif")
		}
		total += g.JmlFinal
	}
	if total == 0 {
		return errors.New("total janjang nol")
	}
	// Keputusan Ptoni (18 Nov): satu TPH hanya untuk SATU pemanen (buah tidak bercampur antar pemanen), jadi tiap catatan
	// membawa tepat satu pemanen yang dikreditkan seluruh janjangnya. HVD1 mencatat satu baris per pemanen.
	if len(p.Pemanen) != 1 {
		return errors.New("pemanen wajib diisi tepat satu (satu TPH hanya untuk satu pemanen)")
	}
	if pm := p.Pemanen[0]; pm.HrmbCode <= 0 {
		return errors.New("hrmb_code pemanen wajib diisi")
	} else if pm.JjgAlokasi != total {
		return errors.New("jjg_alokasi pemanen harus sama dengan total janjang (satu TPH hanya untuk satu pemanen)")
	}
	if p.TphKode == "" {
		return errors.New("tph_kode wajib diisi")
	}
	// Persetujuan: mandor yang menginput dianggap menyetujui sendiri (server mengisinya dari akun pengirim). Bila HP tetap
	// mengirim penyetuju (alur krani lama), keduanya harus lengkap dan waktunya sah.
	if (p.DisetujuiUsername == "") != (p.DisetujuiPada == "") {
		return errors.New("disetujui_username dan disetujui_pada harus diisi bersama")
	}
	if p.DisetujuiPada != "" {
		if _, err := ParseWaktu(p.DisetujuiPada); err != nil {
			return errors.New("disetujui_pada: " + err.Error())
		}
	}
	if p.DibuatPada != "" {
		if _, err := ParseWaktu(p.DibuatPada); err != nil {
			return errors.New("dibuat_pada: " + err.Error())
		}
	}
	if p.GpsWaktu != nil && *p.GpsWaktu != "" {
		if _, err := ParseWaktu(*p.GpsWaktu); err != nil {
			return errors.New("gps_waktu: " + err.Error())
		}
	}
	return nil
}

// PanenSimpan adalah catatan yang sudah divalidasi dan dilengkapi identitas dari server, siap ditulis ke database.
type PanenSimpan struct {
	Input       PanenInput
	UnitID      int64
	DeviceID    string
	UserID      int64 // pengirim (dari token)
	PencatatID  int64 // pencatat catatan: pengirim sendiri, atau orang lain bila pengirim memegang panen.koreksi
	PenyetujuID int64
	// Menunggu true: tidak ada penyetuju (mandor mengirim, krani yang akan menyetujui); status MENUNGGU, PenyetujuID dan DisetujuiPada kosong.
	Menunggu bool
	// Ganti true: catatan DITOLAK yang sudah ada diperbarui dan dikembalikan ke BARU (bukan baris baru).
	Ganti bool
	// MandorHrmbCode dan AfdelingKode berasal dari akun pencatat (bukan dari isi kiriman). Kosong berarti diturunkan dari master.
	MandorHrmbCode *int64
	AfdelingKode   string
	DibuatPada     time.Time
	DisetujuiPada  time.Time
	GpsWaktu       *time.Time
	IP             string
}

// StatusPanen adalah status catatan untuk HP. Status adalah status HP, StatusErp status di staging server.
type StatusPanen struct {
	ID        string    `json:"id"`
	Status    string    `json:"status"`
	StatusErp string    `json:"status_erp"`
	Pesan     *string   `json:"pesan,omitempty"`
	Diubah    time.Time `json:"diubah"`
}

// StatusUntukHP menerjemahkan status_erp di server ke status catatan di HP.
func StatusUntukHP(erp string) string {
	switch erp {
	case "DIIMPOR":
		return "DITERIMA"
	case "DITOLAK":
		return "DITOLAK"
	case "MENUNGGU":
		return "MENUNGGU_SETUJU"
	default: // BARU, DIBACA
		return "TERKIRIM"
	}
}

// Foto adalah satu baris fai_foto.
type Foto struct {
	ID       string
	PanenID  string
	Jenis    string
	NamaFile string
	SHA256   string
	UserID   int64
}

// KelasJumlah adalah jumlah janjang satu kelas pada ringkasan panen.
type KelasJumlah struct {
	KelasKode string `json:"kelas_kode"`
	Janjang   int64  `json:"janjang"`
}

// BlokRekap adalah rekap satu blok pada satu hari.
type BlokRekap struct {
	BlokKode string `json:"blok_kode"`
	Tph      int64  `json:"tph"`
	Catatan  int64  `json:"catatan"`
	Janjang  int64  `json:"janjang"`
}

// RingkasanPanen adalah data dashboard HP: produksi satu hari, rincian kelas dan blok, tren 7 hari, dan status
// catatan. Lingkup SENDIRI berarti hanya catatan pengguna; UNIT berarti semua catatan di unitnya (panen.koreksi).
type RingkasanPanen struct {
	Tanggal          string           `json:"tanggal"`
	Lingkup          string           `json:"lingkup"`
	CatatanHariIni   int64            `json:"catatan_hari_ini"`
	JanjangHariIni   int64            `json:"janjang_hari_ini"`
	PerKelas         []KelasJumlah    `json:"per_kelas"`
	PerBlok          []BlokRekap      `json:"per_blok"`
	Tren7Hari        []TrenHarian     `json:"tren_7_hari"`
	CatatanPerStatus map[string]int64 `json:"catatan_per_status"`
}

// MasterPanen adalah hasil pemeriksaan master untuk satu catatan: apakah blok, TPH, dan pemanen ada, dan mandor pemanen itu.
type MasterPanen struct {
	BlokAda       bool
	TphAda        bool
	PemanenAda    bool
	PemanenMandor *int64
}

// CatatanAda menjelaskan catatan yang sudah tersimpan dengan panen_id tertentu.
type CatatanAda struct {
	StatusErp string
	PemilikID int64
}

// PanenMenunggu adalah satu catatan yang menunggu persetujuan krani (GET /api/panen/menunggu).
type PanenMenunggu struct {
	ID           string        `json:"id"`
	TanggalPanen string        `json:"tanggal_panen"`
	BlokKode     *string       `json:"blok_kode"`
	TphKode      *string       `json:"tph_kode"`
	AfdelingKode *string       `json:"afdeling_kode"`
	Pencatat     string        `json:"pencatat"`
	PencatatNama string        `json:"pencatat_nama"`
	PemanenNama  *string       `json:"pemanen_nama"`
	PemanenHrmb  *int64        `json:"pemanen_hrmb_code"`
	TotalJanjang int64         `json:"total_janjang"`
	BrondolanKg  float64       `json:"brondolan_kg"`
	Catatan      *string       `json:"catatan"`
	StatusLokasi string        `json:"status_lokasi"`
	JarakKeTphM  *float64      `json:"jarak_ke_tph_m"`
	GpsMock      bool          `json:"gps_mock"`
	DibuatPada   time.Time     `json:"dibuat_pada"`
	Grading      []KelasJumlah `json:"grading"`
	// FotoID: foto bukti untuk diperiksa krani (hasil deteksi bila ada, kalau tidak foto asli). Nil bila foto belum terkirim.
	FotoID *string `json:"foto_id"`
}

// TolakInput adalah isi POST /api/panen/{id}/tolak.
type TolakInput struct {
	Alasan string `json:"alasan"`
}
