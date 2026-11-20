package controller

import (
	"net/http"

	"fai26-api/internal/model"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type HakAksesController struct{ svc *service.HakAksesService }

func NewHakAksesController(svc *service.HakAksesService) *HakAksesController {
	return &HakAksesController{svc: svc}
}

// Daftar: GET /api/admin/hak-akses
func (c *HakAksesController) Daftar(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.Daftar(r.Context())
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Ubah: PUT /api/admin/hak-akses/{id}
func (c *HakAksesController) Ubah(w http.ResponseWriter, r *http.Request) {
	var in model.HakAksesInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	selesai(w, r, c.svc.Ubah(r.Context(), klaim(r), idPath(r), in))
}
