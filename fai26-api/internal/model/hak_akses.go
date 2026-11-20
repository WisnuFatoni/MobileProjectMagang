package model

// HakAksesRole adalah satu role beserta kode permission yang dipegangnya.
type HakAksesRole struct {
	RoleID     int64    `json:"role_id"`
	Kode       string   `json:"kode"`
	Nama       string   `json:"nama"`
	Deskripsi  *string  `json:"deskripsi"`
	Bawaan     bool     `json:"bawaan"`
	Pengguna   int64    `json:"pengguna"` // jumlah akun aktif yang memegang role ini
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
// Nama dan Deskripsi opsional: bila diisi, ikut mengubah identitas role (kode tidak bisa diubah).
type HakAksesInput struct {
	Permission []string `json:"permission"`
	Nama       *string  `json:"nama"`
	Deskripsi  *string  `json:"deskripsi"`
}

// RoleInput adalah isi POST /api/admin/hak-akses: role baru beserta hak awalnya.
type RoleInput struct {
	Kode       string   `json:"kode"`
	Nama       string   `json:"nama"`
	Deskripsi  string   `json:"deskripsi"`
	Permission []string `json:"permission"`
}
