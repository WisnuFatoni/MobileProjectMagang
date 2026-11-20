package model

// HakAksesRole adalah satu role beserta kode permission yang dipegangnya.
type HakAksesRole struct {
	RoleID     int64    `json:"role_id"`
	Kode       string   `json:"kode"`
	Nama       string   `json:"nama"`
	Bawaan     bool     `json:"bawaan"`
	Permission []string `json:"permission"`
}

// IzinKatalog adalah satu permission yang tersedia di sistem.
type IzinKatalog struct {
	Kode  string `json:"kode"`
	Modul string `json:"modul"`
	Nama  string `json:"nama"`
}

// HakAkses adalah seluruh role beserta katalog permission, untuk layar pengelolaan hak akses.
type HakAkses struct {
	Role       []HakAksesRole `json:"role"`
	Permission []IzinKatalog  `json:"permission"`
}

// HakAksesInput adalah isi PUT /api/admin/hak-akses/{id}: daftar LENGKAP permission yang dipegang role itu.
type HakAksesInput struct {
	Permission []string `json:"permission"`
}
