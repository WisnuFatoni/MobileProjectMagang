// Package repository berisi semua akses database (SQL). Lapisan lain tidak boleh menulis SQL.
package repository

import (
	"database/sql"
	"errors"
)

var (
	ErrNotFound          = errors.New("data tidak ditemukan")
	ErrPerangkatNonaktif = errors.New("perangkat dinonaktifkan")
	ErrDuplikat          = errors.New("data dengan kunci yang sama sudah ada")
	ErrMasihDipakai      = errors.New("data masih dipakai data lain")
	ErrReferensi         = errors.New("data yang dirujuk tidak ada")
	ErrStatusBerubah     = errors.New("status catatan sudah berubah")
)

func nullStr(s string) any {
	if s == "" {
		return nil
	}
	return s
}

func ptrAny[T any](p *T) any {
	if p == nil {
		return nil
	}
	return *p
}

func int64Ptr(n sql.NullInt64) *int64 {
	if !n.Valid {
		return nil
	}
	v := n.Int64
	return &v
}

func teksAtauKosong(p *string) string {
	if p == nil {
		return ""
	}
	return *p
}
