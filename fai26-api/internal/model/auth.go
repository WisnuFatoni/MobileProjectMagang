// Package model berisi bentuk data yang dipakai antar-lapisan: permintaan (DTO), jawaban, dan entitas.
package model

import "time"

// LoginRequest adalah isi POST /api/auth/login.
type LoginRequest struct {
	Username   string `json:"username"`
	Password   string `json:"password"`
	DeviceID   string `json:"device_id"`
	DeviceNama string `json:"device_nama"`
	OSVersi    string `json:"os_versi"`
	AppVersi   string `json:"app_versi"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type Profil struct {
	UserID     int64    `json:"user_id"`
	UnitID     int64    `json:"unit_id"`
	UnitKode   string   `json:"unit_kode"`
	Username   string   `json:"username"`
	Nama       string   `json:"nama"`
	Role       []string `json:"role"`
	Permission []string `json:"permission"`
	WajibGanti bool     `json:"wajib_ganti_password"`
	// MandorHrmbCode dan AfdelingKode terisi untuk akun mandor (diisi admin): dasar atribusi catatan dan pembatas master HP.
	MandorHrmbCode *int64  `json:"mandor_hrmb_code"`
	AfdelingKode   *string `json:"afdeling_kode"`
}

type LoginResponse struct {
	AccessToken   string    `json:"access_token"`
	AccessExpires time.Time `json:"access_expires"`
	RefreshToken  string    `json:"refresh_token"`
	Profil        Profil    `json:"profil"`
}

type RefreshResponse struct {
	AccessToken   string    `json:"access_token"`
	AccessExpires time.Time `json:"access_expires"`
	RefreshToken  string    `json:"refresh_token"`
}

// User adalah baris app_user beserta unitnya, dipakai untuk memeriksa login.
type User struct {
	ID             int64
	UnitID         int64
	UnitKode       string
	Username       string
	Nama           string
	PasswordHash   string
	Aktif          bool
	GagalLogin     int
	Terkunci       bool // terkunci_sampai masih di masa depan (menurut jam database)
	WajibGanti     bool
	MandorHrmbCode *int64
	AfdelingKode   *string
}

// Perangkat adalah HP yang login.
type Perangkat struct {
	ID       string
	Nama     string
	OSVersi  string
	AppVersi string
}

// Sesi adalah baris auth_sesi yang masih berlaku.
type Sesi struct {
	ID       string
	UserID   int64
	UnitID   int64
	DeviceID string
}

// GantiPasswordRequest adalah isi POST /api/auth/password.
type GantiPasswordRequest struct {
	PasswordLama string `json:"password_lama"`
	PasswordBaru string `json:"password_baru"`
}
