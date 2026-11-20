package controller

import (
	"net/http"

	"fai26-api/internal/auth"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type MasterController struct{ svc *service.MasterService }

func NewMasterController(svc *service.MasterService) *MasterController {
	return &MasterController{svc: svc}
}

// Ambil: GET /api/master?versi=
func (c *MasterController) Ambil(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.Ambil(r.Context(), auth.KlaimDari(r.Context()), r.URL.Query().Get("versi"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}
