package model

import "time"

// AuditInput adalah satu baris audit_log dari HP.
type AuditInput struct {
	AuditID    string  `json:"audit_id"`
	Tabel      string  `json:"tabel"`
	BarisID    string  `json:"baris_id"`
	Aksi       string  `json:"aksi"`
	NilaiLama  *string `json:"nilai_lama"`
	NilaiBaru  *string `json:"nilai_baru"`
	Username   string  `json:"username"`
	Waktu      string  `json:"waktu"`
	Keterangan string  `json:"keterangan"`
}

// AuditBaris adalah baris audit yang ditulis server sendiri (login, terima catatan, dan sebagainya).
type AuditBaris struct {
	Tabel      string
	BarisID    string
	Aksi       string
	UserID     *int64
	Username   string
	DeviceID   string
	IP         string
	Keterangan string
}

// AuditSimpan adalah baris audit dari HP yang sudah divalidasi, dengan waktu yang sudah di-parse (UTC).
type AuditSimpan struct {
	Input AuditInput
	Waktu time.Time
}
