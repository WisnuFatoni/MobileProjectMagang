package controller

import (
	"net/http"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

type AuthController struct{ svc *service.AuthService }

func NewAuthController(svc *service.AuthService) *AuthController { return &AuthController{svc: svc} }

func (c *AuthController) Login(w http.ResponseWriter, r *http.Request) {
	var req model.LoginRequest
	if err := decodeJSON(w, r, 1<<16, &req); err != nil {
		response.Error(w, r, err)
		return
	}
	out, err := c.svc.Login(r.Context(), req, ipDari(r))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

func (c *AuthController) Refresh(w http.ResponseWriter, r *http.Request) {
	var req model.RefreshRequest
	if err := decodeJSON(w, r, 1<<14, &req); err != nil {
		response.Error(w, r, err)
		return
	}
	out, err := c.svc.Refresh(r.Context(), req.RefreshToken)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

func (c *AuthController) Logout(w http.ResponseWriter, r *http.Request) {
	k := auth.KlaimDari(r.Context())
	if err := c.svc.Logout(r.Context(), k.UserID, k.DeviceID); err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "keluar"})
}

func (c *AuthController) Me(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.Me(r.Context(), auth.KlaimDari(r.Context()).UserID)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// GantiPassword: POST /api/auth/password
func (c *AuthController) GantiPassword(w http.ResponseWriter, r *http.Request) {
	var req model.GantiPasswordRequest
	if err := decodeJSON(w, r, 1<<14, &req); err != nil {
		response.Error(w, r, err)
		return
	}
	k := auth.KlaimDari(r.Context())
	if err := c.svc.GantiPassword(r.Context(), k.UserID, k.DeviceID, req, ipDari(r)); err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "OK"})
}
