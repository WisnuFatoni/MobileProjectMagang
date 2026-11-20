// Package controller menangani permintaan HTTP: membaca input, memanggil service, menulis jawaban.
// Tidak ada aturan bisnis dan tidak ada SQL di sini.
package controller

import (
	"encoding/json"
	"net"
	"net/http"

	"fai26-api/internal/apperror"
)

// decodeJSON membaca badan JSON dengan batas ukuran. Galatnya sudah berupa apperror.
func decodeJSON(w http.ResponseWriter, r *http.Request, maks int64, tujuan any) error {
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, maks)).Decode(tujuan); err != nil {
		return apperror.BadRequest("Isi permintaan tidak valid")
	}
	return nil
}

func ipDari(r *http.Request) string {
	h, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return h
}
