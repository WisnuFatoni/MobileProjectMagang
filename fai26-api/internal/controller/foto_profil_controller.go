package controller

import (
	"io"
	"net/http"
	"strconv"

	"fai26-api/internal/auth"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type FotoProfilController struct{ svc *service.FotoProfilService }

func NewFotoProfilController(svc *service.FotoProfilService) *FotoProfilController {
	return &FotoProfilController{svc: svc}
}

// Simpan: PUT /api/auth/foto. Isi permintaan adalah berkas gambar mentah (JPEG atau PNG).
func (c *FotoProfilController) Simpan(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, service.MaksFotoProfilByte+1024)
	versi, err := c.svc.Simpan(r.Context(), auth.KlaimDari(r.Context()), r.Body)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]int64{"foto_versi": versi})
}

// Baca: GET /api/auth/foto. ETag memakai versi foto; "Cache-Control: no-cache" membuat klien selalu bertanya dulu (304 bila sama).
func (c *FotoProfilController) Baca(w http.ResponseWriter, r *http.Request) {
	f, versi, err := c.svc.Baca(r.Context(), auth.KlaimDari(r.Context()).UserID)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	defer f.Close()
	etag := `"` + strconv.FormatInt(versi, 10) + `"`
	w.Header().Set("ETag", etag)
	w.Header().Set("Cache-Control", "private, no-cache")
	if r.Header.Get("If-None-Match") == etag {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	_, _ = io.Copy(w, f)
}

// Hapus: DELETE /api/auth/foto
func (c *FotoProfilController) Hapus(w http.ResponseWriter, r *http.Request) {
	selesai(w, r, c.svc.Hapus(r.Context(), auth.KlaimDari(r.Context())))
}
