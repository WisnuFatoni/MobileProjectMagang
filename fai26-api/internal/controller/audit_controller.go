package controller

import (
	"net/http"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type AuditController struct{ svc *service.AuditService }

func NewAuditController(svc *service.AuditService) *AuditController {
	return &AuditController{svc: svc}
}

// TerimaBatch: POST /api/audit/batch
func (c *AuditController) TerimaBatch(w http.ResponseWriter, r *http.Request) {
	var batch []model.AuditInput
	if err := decodeJSON(w, r, 4<<20, &batch); err != nil {
		response.Error(w, r, err)
		return
	}
	diterima, dilewati, err := c.svc.TerimaBatch(r.Context(), auth.KlaimDari(r.Context()), batch, ipDari(r))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]int{"diterima": diterima, "dilewati": dilewati})
}
