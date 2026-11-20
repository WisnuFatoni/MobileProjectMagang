package controller

import (
	"net/http"

	"fai26-api/internal/auth"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type MediaController struct{ svc *service.MediaService }

func NewMediaController(svc *service.MediaService) *MediaController {
	return &MediaController{svc: svc}
}

// Terima: POST /api/panen/{id}/media (isi mentah JPEG; header X-Foto-Id dan X-Sha256)
func (c *MediaController) Terima(w http.ResponseWriter, r *http.Request) {
	fotoID := r.Header.Get("X-Foto-Id")
	req := service.MediaRequest{
		PanenID: r.PathValue("id"), FotoID: fotoID, SHA256: r.Header.Get("X-Sha256"), Jenis: r.URL.Query().Get("jenis"),
	}
	status, err := c.svc.Terima(r.Context(), auth.KlaimDari(r.Context()), req, r.Body)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	kode := http.StatusCreated
	if status == service.StatusSudahAda {
		kode = http.StatusOK
	}
	response.JSON(w, kode, map[string]string{"status": status, "id": fotoID})
}
