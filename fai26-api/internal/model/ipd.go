package model

import (
	"errors"
	"fmt"
	"strings"
	"time"
)

// Batas isi satu catatan IPD (melindungi server dari kiriman raksasa).
const (
	MaksPokokIpd   = 3000 // pokok per catatan (satu pemanen, beberapa baris)
	MaksNomorIpd   = 9999 // nomor baris dan nomor pokok
	MaksJumlahIpd  = 9999 // jumlah per temuan
	JendelaIpdHari = 7    // peringatan inspeksi ulang: pemanen + blok yang sama dalam 7 hari terakhir
)

// IpdTemuanInput adalah satu temuan pada satu pokok. Jumlah 0 (tidak dikirim) dianggap 1.
type IpdTemuanInput struct {
	Kode    string `json:"kode"`
	Jumlah  int    `json:"jumlah"`
	Catatan string `json:"catatan"`
}

// IpdPokokInput adalah satu pokok yang diperiksa. Temuan kosong berarti pokok bersih (tetap dihitung sebagai diperiksa).
type IpdPokokInput struct {
	NoBaris int              `json:"no_baris"`
	NoPokok int              `json:"no_pokok"`
	Temuan  []IpdTemuanInput `json:"temuan"`
}

// IpdInput adalah satu catatan inspeksi panen detail dari HP: satu pemanen (satu ancak). Pengguna, unit, dan penyetuju
// ditentukan server (dari token dan divisi), bukan dari isi ini.
type IpdInput struct {
	IpdID               string `json:"ipd_id"`
	UnitKode            string `json:"unit_kode"`
	TanggalInspeksi     string `json:"tanggal_inspeksi"`
	DivisiKode          string `json:"divisi_kode"`
	Asm3Oid             int64  `json:"asm3_oid"`
	BlokKode            string `json:"blok_kode"`
	PemanenHrmbCode     int    `json:"pemanen_hrmb_code"`
	AncakNo             string `json:"ancak_no"`
	MandorBlokUsername  string `json:"mandor_blok_username"`
	MandorAncakUsername string `json:"mandor_ancak_username"`
	Catatan             string `json:"catatan"`
	DibuatPada          string `json:"dibuat_pada"`
	// KirimUlang true: HP mengubah catatan yang sudah terkirim (masih MENUNGGU asisten) atau memperbaiki yang DITOLAK.
	// Tanpa ini catatan yang sudah ada dijawab SUDAH_ADA tanpa diubah (aman untuk percobaan ulang jaringan).
	KirimUlang bool            `json:"kirim_ulang"`
	Pokok      []IpdPokokInput `json:"pokok"`
}

// Validasi memeriksa isi catatan (bentuk, bukan master). Mengisi jumlah temuan yang kosong dengan 1.
func (p *IpdInput) Validasi() error {
	if !ReUUID.MatchString(p.IpdID) {
		return errors.New("ipd_id harus UUID")
	}
	if p.UnitKode == "" {
		return errors.New("unit_kode wajib diisi")
	}
	if _, err := time.Parse("2006-01-02", p.TanggalInspeksi); err != nil {
		return errors.New("tanggal_inspeksi harus YYYY-MM-DD")
	}
	if strings.TrimSpace(p.DivisiKode) == "" {
		return errors.New("divisi_kode wajib diisi")
	}
	if p.Asm3Oid <= 0 {
		return errors.New("asm3_oid (OID blok di ERP) wajib diisi")
	}
	if p.PemanenHrmbCode <= 0 {
		return errors.New("pemanen_hrmb_code wajib diisi (satu catatan = satu pemanen)")
	}
	if strings.TrimSpace(p.MandorBlokUsername) == "" || strings.TrimSpace(p.MandorAncakUsername) == "" {
		return errors.New("mandor_blok_username dan mandor_ancak_username wajib diisi")
	}
	if len([]rune(p.AncakNo)) > 30 {
		return errors.New("ancak_no maksimal 30 karakter")
	}
	if len([]rune(p.Catatan)) > 500 {
		return errors.New("catatan maksimal 500 karakter")
	}
	if p.DibuatPada != "" {
		if _, err := ParseWaktu(p.DibuatPada); err != nil {
			return errors.New("dibuat_pada: " + err.Error())
		}
	}
	if len(p.Pokok) == 0 {
		return errors.New("pokok tidak boleh kosong (catat minimal satu pokok yang diperiksa)")
	}
	if len(p.Pokok) > MaksPokokIpd {
		return fmt.Errorf("pokok maksimal %d per catatan", MaksPokokIpd)
	}
	sudah := map[[2]int]bool{}
	for i := range p.Pokok {
		pk := &p.Pokok[i]
		if pk.NoBaris < 1 || pk.NoBaris > MaksNomorIpd || pk.NoPokok < 1 || pk.NoPokok > MaksNomorIpd {
			return fmt.Errorf("no_baris dan no_pokok harus 1 sampai %d", MaksNomorIpd)
		}
		kunci := [2]int{pk.NoBaris, pk.NoPokok}
		if sudah[kunci] {
			return fmt.Errorf("pokok ganda: baris %d pokok %d", pk.NoBaris, pk.NoPokok)
		}
		sudah[kunci] = true
		kode := map[string]bool{}
		for j := range pk.Temuan {
			t := &pk.Temuan[j]
			t.Kode = strings.ToUpper(strings.TrimSpace(t.Kode))
			if t.Kode == "" {
				return errors.New("kode temuan wajib diisi")
			}
			if kode[t.Kode] {
				return fmt.Errorf("kode temuan ganda pada baris %d pokok %d: %s", pk.NoBaris, pk.NoPokok, t.Kode)
			}
			kode[t.Kode] = true
			if t.Jumlah == 0 {
				t.Jumlah = 1
			}
			if t.Jumlah < 1 || t.Jumlah > MaksJumlahIpd {
				return fmt.Errorf("jumlah temuan harus 1 sampai %d", MaksJumlahIpd)
			}
			if len([]rune(t.Catatan)) > 200 {
				return errors.New("catatan temuan maksimal 200 karakter")
			}
		}
	}
	return nil
}

// IpdAda menjelaskan catatan IPD yang sudah tersimpan dengan ipd_id tertentu.
type IpdAda struct {
	Status    string
	PemilikID int64
}

// IpdMaster adalah hasil pemeriksaan master untuk satu catatan IPD.
type IpdMaster struct {
	DivisiID        int64
	DivisiAda       bool
	AsistenID       *int64 // asisten divisi saat ini; nil bila belum diatur
	BlokAda         bool
	BlokDalamDivisi bool
	PemanenAda      bool
}

// IpdOrang adalah akun yang dipilih sebagai mandor.
type IpdOrang struct {
	UserID int64
	Hrmb   *int64
}

// IpdSimpan adalah catatan yang sudah divalidasi dan dilengkapi identitas dari server, siap ditulis.
type IpdSimpan struct {
	Input       IpdInput
	UnitID      int64
	DeviceID    string
	UserID      int64 // inspektur (pengirim, dari token)
	DivisiID    int64
	AsistenID   *int64
	MandorBlok  IpdOrang
	MandorAncak IpdOrang
	KodeID      map[string]int64 // kode temuan -> kode_id
	// Menunggu true: asisten yang akan menyetujui (status MENUNGGU). False: pengirim memegang ipd.setujui, langsung DISETUJUI.
	Menunggu   bool
	Ganti      bool // memperbarui catatan yang sudah ada (MENUNGGU atau DITOLAK)
	DibuatPada time.Time
	IP         string
}

// IpdStatus adalah status catatan IPD untuk HP pengirim. Status memakai istilah HP.
type IpdStatus struct {
	ID           string    `json:"id"`
	Status       string    `json:"status"`
	StatusServer string    `json:"status_server"`
	Pesan        *string   `json:"pesan,omitempty"`
	Diubah       time.Time `json:"diubah"`
}

// IpdStatusUntukHP menerjemahkan fai_ipd.status ke status catatan IPD di HP.
func IpdStatusUntukHP(s string) string {
	switch s {
	case "DISETUJUI":
		return "DISETUJUI"
	case "DITOLAK":
		return "DITOLAK"
	default:
		return "MENUNGGU_SETUJU"
	}
}

// IpdKodeAgg adalah hitungan satu kode temuan: Pokok = banyak pokok yang memuat kode itu, Jumlah = jumlah temuan.
type IpdKodeAgg struct {
	Kode        string  `json:"kode"`
	Nama        string  `json:"nama"`
	Kelompok    string  `json:"kelompok"`
	Pokok       int64   `json:"pokok"`
	Jumlah      int64   `json:"jumlah"`
	PersenPokok float64 `json:"persen_pokok"`
}

// IpdRingkasan adalah hasil hitung satu inspeksi (atau gabungan beberapa). Persen = pokok bertemuan dibagi pokok diperiksa.
type IpdRingkasan struct {
	PokokDiperiksa       int64        `json:"pokok_diperiksa"`
	PokokBertemuan       int64        `json:"pokok_bertemuan"`
	PersenPokokBertemuan float64      `json:"persen_pokok_bertemuan"`
	TotalTemuan          int64        `json:"total_temuan"`
	PerKode              []IpdKodeAgg `json:"per_kode"`
}

// IpdFotoRef menunjuk satu foto bukti; berkasnya diunduh lewat GET /api/ipd/foto/{id}.
type IpdFotoRef struct {
	ID         string  `json:"id"`
	Keterangan *string `json:"keterangan"`
	Urut       int     `json:"urut"`
}

// IpdBaris adalah satu inspeksi pada daftar (menunggu persetujuan atau daftar web).
type IpdBaris struct {
	ID              string       `json:"id"`
	TanggalInspeksi string       `json:"tanggal_inspeksi"`
	DivisiKode      string       `json:"divisi_kode"`
	BlokKode        *string      `json:"blok_kode"`
	PemanenHrmbCode int64        `json:"pemanen_hrmb_code"`
	PemanenNama     *string      `json:"pemanen_nama"`
	AncakNo         *string      `json:"ancak_no"`
	Inspektur       string       `json:"inspektur"`
	InspekturNama   string       `json:"inspektur_nama"`
	MandorBlokNama  *string      `json:"mandor_blok_nama"`
	MandorAncakNama *string      `json:"mandor_ancak_nama"`
	AsistenNama     *string      `json:"asisten_nama"`
	Status          string       `json:"status"`
	PesanTolak      *string      `json:"pesan_tolak"`
	Catatan         *string      `json:"catatan"`
	DibuatPada      time.Time    `json:"dibuat_pada"`
	DisetujuiPada   *time.Time   `json:"disetujui_pada"`
	Ringkasan       IpdRingkasan `json:"ringkasan"`
	Foto            []IpdFotoRef `json:"foto"`
}

// IpdTemuanOut dan IpdPokokOut membentuk rincian satu inspeksi.
type IpdTemuanOut struct {
	Kode    string  `json:"kode"`
	Jumlah  int64   `json:"jumlah"`
	Catatan *string `json:"catatan"`
}

type IpdPokokOut struct {
	NoBaris int            `json:"no_baris"`
	NoPokok int            `json:"no_pokok"`
	Temuan  []IpdTemuanOut `json:"temuan"`
}

// IpdDetail adalah satu inspeksi lengkap dengan semua pokok yang diperiksa.
type IpdDetail struct {
	IpdBaris
	Pokok []IpdPokokOut `json:"pokok"`
}

// FilterIpd adalah penyaring daftar inspeksi di web.
type FilterIpd struct {
	Dari, Sampai string
	Status       string
	DivisiKode   string
	BlokKode     string
	PemanenHrmb  int64
	Halaman      int
	PerHal       int
}

// HalamanIpd adalah satu halaman daftar inspeksi.
type HalamanIpd struct {
	Item    []IpdBaris `json:"item"`
	Total   int64      `json:"total"`
	Halaman int        `json:"halaman"`
	PerHal  int        `json:"per_halaman"`
}

// IpdTerakhir adalah inspeksi sebelumnya untuk pemanen + blok yang sama (peringatan inspeksi ulang).
type IpdTerakhir struct {
	ID              string `json:"id"`
	TanggalInspeksi string `json:"tanggal_inspeksi"`
	Inspektur       string `json:"inspektur"`
	InspekturNama   string `json:"inspektur_nama"`
	Status          string `json:"status"`
}

// IpdInspeksiTerakhir adalah jawaban GET /api/ipd/inspeksi-terakhir.
type IpdInspeksiTerakhir struct {
	JendelaHari int           `json:"jendela_hari"`
	Ada         bool          `json:"ada"`
	Inspeksi    []IpdTerakhir `json:"inspeksi"`
}

// ---- Laporan loses ------------------------------------------------------------------------------

// FilterLosesIpd membatasi laporan loses. Hanya inspeksi berstatus DISETUJUI yang dihitung.
type FilterLosesIpd struct {
	Dari, Sampai string
	DivisiKode   string
	BlokKode     string
	PemanenHrmb  int64
}

// IpdHitung adalah hitungan satu kode atau kelompok: Pokok = banyak pokok yang memuatnya, Jumlah = jumlah temuan.
type IpdHitung struct {
	Pokok  int64
	Jumlah int64
}

// IpdMentah adalah hitungan dasar satu inspeksi dari database: pokok diperiksa, pokok bertemuan, dan temuan per kode dan per kelompok.
type IpdMentah struct {
	PokokDiperiksa int64
	PokokBertemuan int64
	PerKode        map[string]IpdHitung // kode temuan
	PerKelompok    map[string]IpdHitung // BUAH, BRONDOLAN, PELEPAH
}

// IpdDasar adalah satu inspeksi DISETUJUI beserta hitungan dasarnya (bahan laporan).
type IpdDasar struct {
	ID          string
	Tanggal     string
	DivisiKode  string
	BlokKode    string
	PemanenHrmb int64
	PemanenNama string
	Mentah      IpdMentah
}

// IpdFoto adalah satu baris fai_ipd_foto.
type IpdFoto struct {
	ID         string
	IpdID      string
	Keterangan string
	Urut       int
	NamaFile   string
	SHA256     string
	UserID     int64
}

// IpdKodeMaster adalah satu kode temuan di master.
type IpdKodeMaster struct {
	Kode     string
	Nama     string
	Kelompok string
	Urut     int
}

// IpdKelompokAgg adalah hitungan satu kelompok temuan (BUAH, BRONDOLAN, PELEPAH).
type IpdKelompokAgg struct {
	Kelompok    string  `json:"kelompok"`
	Pokok       int64   `json:"pokok"`
	Jumlah      int64   `json:"jumlah"`
	PersenPokok float64 `json:"persen_pokok"`
}

// IpdGrupLoses adalah loses satu kelompok data (satu pemanen, satu blok, satu divisi, atau satu tanggal).
type IpdGrupLoses struct {
	Kunci                string           `json:"kunci"`
	Nama                 string           `json:"nama"`
	Inspeksi             int64            `json:"inspeksi"`
	PokokDiperiksa       int64            `json:"pokok_diperiksa"`
	PokokBertemuan       int64            `json:"pokok_bertemuan"`
	PersenPokokBertemuan float64          `json:"persen_pokok_bertemuan"`
	TotalTemuan          int64            `json:"total_temuan"`
	TemuanPerKode        map[string]int64 `json:"temuan_per_kode"`
}

// IpdLaporan adalah jawaban GET /api/admin/ipd/laporan.
type IpdLaporan struct {
	Dari        string           `json:"dari"`
	Sampai      string           `json:"sampai"`
	Inspeksi    int64            `json:"inspeksi"`
	Pemanen     int64            `json:"pemanen"`
	Ringkasan   IpdRingkasan     `json:"ringkasan"`
	PerKelompok []IpdKelompokAgg `json:"per_kelompok"`
	PerPemanen  []IpdGrupLoses   `json:"per_pemanen"`
	PerBlok     []IpdGrupLoses   `json:"per_blok"`
	PerDivisi   []IpdGrupLoses   `json:"per_divisi"`
	PerTanggal  []IpdGrupLoses   `json:"per_tanggal"`
}
