// Package response menulis jawaban JSON yang seragam.
package response

import (
	"encoding/json"
	"log"
	"net/http"

	"fai26-api/internal/apperror"
)

func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

// Error menulis {"error": "..."} sesuai kode status galat. Sebab teknis galat 5xx dicatat di log, tidak dikirim ke klien.
func Error(w http.ResponseWriter, r *http.Request, err error) {
	ae := apperror.Dari(err)
	// Batas waktu permintaan habis (database lambat): jawab 503 walau pustaka driver membungkus galatnya sebagai teks.
	if ae.Status == http.StatusInternalServerError && r.Context().Err() != nil {
		ae = &apperror.Error{Status: http.StatusServiceUnavailable, Message: "Server sedang sibuk. Coba lagi sebentar", Err: ae}
	}
	if ae.Status >= 500 {
		log.Printf("ERROR %s %s: %v", r.Method, r.URL.Path, ae)
	}
	JSON(w, ae.Status, map[string]string{"error": ae.Message})
}
