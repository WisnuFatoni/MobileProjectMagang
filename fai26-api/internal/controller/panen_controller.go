package controller

import (
	"net/http"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type PanenController struct{ svc *service.PanenService }

func NewPanenController(svc *service.PanenService) *PanenController {
	return &PanenController{svc: svc}
}

// Terima: POST /api/panen
func (c *PanenController) Terima(w http.ResponseWriter, r *http.Request) {
	var in model.PanenInput
	if err := decodeJSON(w, r, 1<<20, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	status, err := c.svc.Terima(r.Context(), auth.KlaimDari(r.Context()), in, ipDari(r))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	kode := http.StatusCreated
	if status == service.StatusSudahAda {
		kode = http.StatusOK
	}
	response.JSON(w, kode, map[string]string{"status": status, "id": in.PanenID})
}

// DaftarStatus: GET /api/panen?sejak=
func (c *PanenController) DaftarStatus(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.DaftarStatus(r.Context(), auth.KlaimDari(r.Context()), r.URL.Query().Get("sejak"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Ringkasan: GET /api/panen/ringkasan?tanggal=
func (c *PanenController) Ringkasan(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.Ringkasan(r.Context(), auth.KlaimDari(r.Context()), r.URL.Query().Get("tanggal"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Menunggu: GET /api/panen/menunggu
func (c *PanenController) Menunggu(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.Menunggu(r.Context(), auth.KlaimDari(r.Context()))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Setujui: POST /api/panen/{id}/setujui
func (c *PanenController) Setujui(w http.ResponseWriter, r *http.Request) {
	if err := c.svc.Setujui(r.Context(), auth.KlaimDari(r.Context()), r.PathValue("id"), ipDari(r)); err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "DISETUJUI"})
}

// Tolak: POST /api/panen/{id}/tolak
func (c *PanenController) Tolak(w http.ResponseWriter, r *http.Request) {
	var in model.TolakInput
	if err := decodeJSON(w, r, 1<<14, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	if err := c.svc.Tolak(r.Context(), auth.KlaimDari(r.Context()), r.PathValue("id"), in.Alasan, ipDari(r)); err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "DITOLAK"})
}
