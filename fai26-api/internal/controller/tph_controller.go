package controller

import (
	"net/http"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type TphController struct{ svc *service.TphService }

func NewTphController(svc *service.TphService) *TphController { return &TphController{svc: svc} }

// RekamKoordinat: PUT /api/tph/{id}/koordinat
func (c *TphController) RekamKoordinat(w http.ResponseWriter, r *http.Request) {
	var in model.KoordinatInput
	if err := decodeJSON(w, r, 1<<12, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	out, err := c.svc.RekamKoordinat(r.Context(), auth.KlaimDari(r.Context()), idPath(r), in)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}
