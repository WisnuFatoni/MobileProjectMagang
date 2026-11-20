// Package app adalah composition root: merakit repository, service, dan controller menjadi satu http.Handler.
// Dipakai oleh cmd/api dan oleh tes integrasi supaya keduanya merakit dengan cara yang sama.
package app

import (
	"database/sql"
	"net/http"

	"fai26-api/internal/auth"
	"fai26-api/internal/config"
	"fai26-api/internal/controller"
	"fai26-api/internal/middleware"
	"fai26-api/internal/repository"
	"fai26-api/internal/routes"
	"fai26-api/internal/service"
	"fai26-api/internal/storage"
	"fai26-api/internal/webui"
)

func Bangun(cfg config.Config, db *sql.DB) http.Handler {
	// repository
	users := repository.NewUserRepository(db)
	devices := repository.NewDeviceRepository(db)
	sessions := repository.NewSessionRepository(db)
	audits := repository.NewAuditRepository(db)
	panens := repository.NewPanenRepository(db)
	units := repository.NewUnitRepository(db)
	fotos := repository.NewFotoRepository(db)
	masters := repository.NewMasterRepository(db)
	tphs := repository.NewTphRepository(db)
	adminMasters := repository.NewAdminMasterRepository(db)
	admins := repository.NewAdminRepository(db)
	hakAkses := repository.NewHakAksesRepository(db)
	ipds := repository.NewIpdRepository(db)
	files := storage.NewFileStorage(cfg.MediaDir)

	// service
	tokens := auth.NewTokenManager(cfg.JWTSecret, cfg.AksesTTL)
	akses := service.NewAksesService(users)
	authSvc := service.NewAuthService(users, devices, sessions, audits, tokens, service.AuthConfig{
		RefreshTTL: cfg.RefreshTTL, MaksGagal: cfg.MaksGagal, DurasiKunci: cfg.DurasiKunci,
	})
	panenSvc := service.NewPanenService(panens, units, users, akses)
	mediaSvc := service.NewMediaService(panens, fotos, files, akses)
	auditSvc := service.NewAuditService(audits)
	masterSvc := service.NewMasterService(masters)
	tphSvc := service.NewTphService(tphs)
	adminMasterSvc := service.NewAdminMasterService(adminMasters)
	adminSvc := service.NewAdminService(admins)
	hakAksesSvc := service.NewHakAksesService(hakAkses)
	ipdSvc := service.NewIpdService(ipds, units, akses, files)

	// controller dan routes
	api := routes.Daftar(routes.Controller{
		Health:      controller.NewHealthController(db),
		Auth:        controller.NewAuthController(authSvc),
		Panen:       controller.NewPanenController(panenSvc),
		Ipd:         controller.NewIpdController(ipdSvc, files),
		Media:       controller.NewMediaController(mediaSvc),
		Audit:       controller.NewAuditController(auditSvc),
		Master:      controller.NewMasterController(masterSvc),
		Tph:         controller.NewTphController(tphSvc),
		AdminMaster: controller.NewAdminMasterController(adminMasterSvc),
		Admin:       controller.NewAdminController(adminSvc, files),
		HakAkses:    controller.NewHakAksesController(hakAksesSvc),
	}, tokens, akses, routes.Opsi{
		BatasLogin:    middleware.NewPembatasLaju(cfg.BatasLoginPerMenit),
		BatasUmum:     middleware.NewPembatasLaju(cfg.BatasUmumPerMenit),
		PercayaiProxy: cfg.PercayaiProxy,
	})

	if cfg.WebDir == "" {
		return api
	}
	// Satu alamat untuk web dan API: /api/ ke API, selain itu ke web admin (tanpa CORS).
	gabung := http.NewServeMux()
	gabung.Handle("/api/", api)
	gabung.Handle("/", webui.Handler(cfg.WebDir))
	return gabung
}
