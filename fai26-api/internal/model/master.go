package model

// Baris master adalah peta kolom ke nilai, persis seperti kolom tabel di HP (lihat docs/API.md, GET /api/master).
// Waktu berformat ISO 8601 UTC tanpa pecahan detik, boolean sebagai true/false.
type BarisMaster map[string]any

// MasterUser adalah akun di unit pengguna. Hash password dan data kunci akun tidak pernah dikirim ke HP.
type MasterUser struct {
	UserID         int64    `json:"user_id"`
	UnitID         int64    `json:"unit_id"`
	Username       string   `json:"username"`
	Nama           string   `json:"nama"`
	Aktif          bool     `json:"aktif"`
	ErpUserOid     *int64   `json:"erp_user_oid"`
	MandorHrmbCode *int64   `json:"mandor_hrmb_code"`
	AfdelingKode   *string  `json:"afdeling_kode"`
	Role           []string `json:"role"`
	DihapusPada    *string  `json:"dihapus_pada"`
}

// RoleIzin adalah hak satu role: kode role dan kode permission yang dipegangnya.
type RoleIzin struct {
	Role       string   `json:"role"`
	Permission []string `json:"permission"`
}

// Master adalah paket master satu unit untuk HP. Bila Berubah false, hanya Versi yang terisi.
type Master struct {
	Versi   string        `json:"versi"`
	Berubah bool          `json:"berubah"`
	Unit    BarisMaster   `json:"unit,omitempty"`
	Divisi  []BarisMaster `json:"divisi,omitempty"`
	Blok    []BarisMaster `json:"blok,omitempty"`
	Tph     []BarisMaster `json:"tph,omitempty"`
	Pemanen []BarisMaster `json:"pemanen,omitempty"`
	User    []MasterUser  `json:"user,omitempty"`
	// KodeTemuan adalah master kode temuan IPD (A, G, S, M1 ... R). Daftar mandor dan asisten untuk IPD diambil dari User.
	KodeTemuan []BarisMaster `json:"kode_temuan,omitempty"`
	// RolePermission adalah hak SEMUA role (bukan per unit). Bila kunci ini tidak ada, HP tidak mengubah hak role-nya.
	RolePermission []RoleIzin `json:"role_permission,omitempty"`
}

// LingkupMaster membatasi master yang diunduh sebuah akun. Akun mandor hanya menerima pemanen miliknya dan blok/TPH
// di afdelingnya; akun tanpa mandor_hrmb_code/afdeling_kode menerima seluruh unit seperti sebelumnya.
type LingkupMaster struct {
	MandorHrmb *int64
	Afdeling   string
}

// IsiMaster adalah hasil bacaan semua tabel master satu unit, sebelum dibentuk menjadi Master.
type IsiMaster struct {
	Unit           BarisMaster
	Divisi         []BarisMaster
	Blok           []BarisMaster
	Tph            []BarisMaster
	Pemanen        []BarisMaster
	User           []MasterUser
	KodeTemuan     []BarisMaster
	RolePermission []RoleIzin
}
