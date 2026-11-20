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

// Buat: POST /api/admin/hak-akses (role baru beserta hak awal)
func (c *HakAksesController) Buat(w http.ResponseWriter, r *http.Request) {
	var in model.RoleInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	id, err := c.svc.Buat(r.Context(), klaim(r), in)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusCreated, map[string]int64{"id": id})
}

// Hapus: DELETE /api/admin/hak-akses/{id} (hanya role buatan admin yang tidak dipegang akun aktif)
func (c *HakAksesController) Hapus(w http.ResponseWriter, r *http.Request) {
	selesai(w, r, c.svc.Hapus(r.Context(), klaim(r), idPath(r)))
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
