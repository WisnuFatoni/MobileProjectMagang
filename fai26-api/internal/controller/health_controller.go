package controller

import (
	"context"
	"net/http"

	"fai26-api/internal/apperror"
	"fai26-api/internal/response"
)

type Pinger interface {
	PingContext(ctx context.Context) error
}

type HealthController struct{ db Pinger }

func NewHealthController(db Pinger) *HealthController { return &HealthController{db: db} }

func (c *HealthController) Cek(w http.ResponseWriter, r *http.Request) {
	if err := c.db.PingContext(r.Context()); err != nil {
		response.Error(w, r, apperror.New(http.StatusServiceUnavailable, "Database tidak terjangkau"))
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "ok"})
}
