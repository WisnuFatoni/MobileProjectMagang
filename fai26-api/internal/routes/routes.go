// Package routes memasang semua alamat API ke controller beserta middleware-nya.
package routes

import (
	"net/http"
	"time"

	"fai26-api/internal/auth"
	"fai26-api/internal/controller"
	"fai26-api/internal/middleware"
)

// Controller mengumpulkan controller yang dilayani router.
type Controller struct {
	Health      *controller.HealthController
	Auth        *controller.AuthController
	Panen       *controller.PanenController
	Media       *controller.MediaController
	Audit       *controller.AuditController
	Master      *controller.MasterController
	Tph         *controller.TphController
	AdminMaster *controller.AdminMasterController
	Admin       *controller.AdminController
	HakAkses    *controller.HakAksesController
}

// BatasWaktuPermintaan adalah waktu maksimum satu permintaan (HP menunggu 30 detik, jadi server menjawab 503 lebih dulu).
const BatasWaktuPermintaan = 20 * time.Second

// rute adalah satu alamat API: pola "METHOD /path", handler, dan middleware-nya.
type rute struct {
	pola       string
	handler    http.HandlerFunc
	middleware []middleware.Middleware
}

// tabel adalah satu-satunya daftar rute. Setiap rute baru ditambahkan DI SINI, lalu wajib didokumentasikan di
// docs/openapi.yaml dan docs/API.md (tes TestSemuaRuteTerdokumentasi akan gagal bila lupa).
func tabel(c Controller, login middleware.Middleware, izin func(string) []middleware.Middleware, batasLogin middleware.Middleware) []rute {
	return []rute{
		// Terbuka
		{"GET /api/health", c.Health.Cek, nil},
		{"POST /api/auth/login", c.Auth.Login, []middleware.Middleware{batasLogin}},
		{"POST /api/auth/refresh", c.Auth.Refresh, []middleware.Middleware{batasLogin}},

		// Harus login
		{"POST /api/auth/logout", c.Auth.Logout, []middleware.Middleware{login}},
		{"GET /api/auth/me", c.Auth.Me, []middleware.Middleware{login}},
		{"POST /api/auth/password", c.Auth.GantiPassword, []middleware.Middleware{login}},
		{"POST /api/audit/batch", c.Audit.TerimaBatch, []middleware.Middleware{login}},

		// Harus login dan memegang permission
		{"POST /api/panen", c.Panen.Terima, izin("panen.kirim")},
		{"GET /api/panen", c.Panen.DaftarStatus, izin("panen.lihat")},
		{"GET /api/panen/ringkasan", c.Panen.Ringkasan, izin("panen.lihat")},
		{"GET /api/panen/menunggu", c.Panen.Menunggu, izin("panen.setujui")},
		{"POST /api/panen/{id}/setujui", c.Panen.Setujui, izin("panen.setujui")},
		{"POST /api/panen/{id}/tolak", c.Panen.Tolak, izin("panen.setujui")},
		{"POST /api/panen/{id}/media", c.Media.Terima, izin("panen.kirim")},
		{"GET /api/master", c.Master.Ambil, izin("master.lihat")},
		{"PUT /api/tph/{id}/koordinat", c.Tph.RekamKoordinat, izin("panen.kirim")},

		// Web admin: master (baca master.lihat, tulis master.kelola)
		{"GET /api/admin/divisi", c.AdminMaster.DaftarDivisi, izin("master.lihat")},
		{"POST /api/admin/divisi", c.AdminMaster.BuatDivisi, izin("master.kelola")},
		{"PUT /api/admin/divisi/{id}", c.AdminMaster.UbahDivisi, izin("master.kelola")},
		{"DELETE /api/admin/divisi/{id}", c.AdminMaster.HapusDivisi, izin("master.kelola")},
		{"GET /api/admin/blok", c.AdminMaster.DaftarBlok, izin("master.lihat")},
		{"POST /api/admin/blok", c.AdminMaster.BuatBlok, izin("master.kelola")},
		{"PUT /api/admin/blok/{id}", c.AdminMaster.UbahBlok, izin("master.kelola")},
		{"DELETE /api/admin/blok/{id}", c.AdminMaster.HapusBlok, izin("master.kelola")},
		{"GET /api/admin/tph", c.AdminMaster.DaftarTph, izin("master.lihat")},
		{"POST /api/admin/tph", c.AdminMaster.BuatTph, izin("master.kelola")},
		{"POST /api/admin/tph/massal", c.AdminMaster.BuatTphMassal, izin("master.kelola")},
		{"PUT /api/admin/tph/{id}", c.AdminMaster.UbahTph, izin("master.kelola")},
		{"DELETE /api/admin/tph/{id}", c.AdminMaster.HapusTph, izin("master.kelola")},
		{"GET /api/admin/pemanen", c.AdminMaster.DaftarPemanen, izin("master.lihat")},
		{"POST /api/admin/pemanen", c.AdminMaster.BuatPemanen, izin("master.kelola")},
		{"PUT /api/admin/pemanen/{id}", c.AdminMaster.UbahPemanen, izin("master.kelola")},
		{"DELETE /api/admin/pemanen/{id}", c.AdminMaster.HapusPemanen, izin("master.kelola")},

		// Web admin: pengguna, perangkat, laporan, audit
		{"GET /api/admin/peran", c.Admin.DaftarRole, izin("user.kelola")},
		{"GET /api/admin/hak-akses", c.HakAkses.Daftar, izin("role.kelola")},
		{"PUT /api/admin/hak-akses/{id}", c.HakAkses.Ubah, izin("role.kelola")},
		{"GET /api/admin/pengguna", c.Admin.DaftarPengguna, izin("user.kelola")},
		{"POST /api/admin/pengguna", c.Admin.BuatPengguna, izin("user.kelola")},
		{"PUT /api/admin/pengguna/{id}", c.Admin.UbahPengguna, izin("user.kelola")},
		{"POST /api/admin/pengguna/{id}/reset-password", c.Admin.ResetPassword, izin("user.kelola")},
		{"GET /api/admin/perangkat", c.Admin.DaftarPerangkat, izin("device.kelola")},
		{"PUT /api/admin/perangkat/{id}", c.Admin.UbahPerangkat, izin("device.kelola")},
		{"GET /api/admin/ringkasan", c.Admin.Ringkasan, izin("laporan.lihat")},
		{"GET /api/admin/produktivitas", c.Admin.Produktivitas, izin("laporan.lihat")},
		{"GET /api/admin/panen", c.Admin.DaftarPanen, izin("panen.lihat")},
		{"GET /api/admin/panen/{id}", c.Admin.DetailPanen, izin("panen.lihat")},
		{"GET /api/admin/foto/{id}", c.Admin.Foto, izin("panen.lihat")},
		{"GET /api/admin/audit", c.Admin.DaftarAudit, izin("audit.lihat")},
	}
}

// Pola mengembalikan semua pola rute ("METHOD /path"). Dipakai tes untuk memastikan dokumentasi lengkap.
func Pola() []string {
	var out []string
	for _, r := range tabel(Controller{}, nil, func(string) []middleware.Middleware { return nil }, nil) {
		out = append(out, r.pola)
	}
	return out
}

// Opsi mengatur pembatasan laju. Pembatas nil berarti tanpa batas.
type Opsi struct {
	BatasLogin    *middleware.PembatasLaju // login dan refresh: pelindung tebak password
	BatasUmum     *middleware.PembatasLaju // semua permintaan
	PercayaiProxy bool
}

func Daftar(c Controller, tokens *auth.TokenManager, akses middleware.Akses, opsi Opsi) http.Handler {
	m := http.NewServeMux()
	login := middleware.Autentikasi(tokens, akses)
	izin := func(perm string) []middleware.Middleware {
		return []middleware.Middleware{login, middleware.Izin(akses, perm)}
	}
	batasLogin := middleware.BatasLaju(opsi.BatasLogin, opsi.PercayaiProxy)
	for _, r := range tabel(c, login, izin, batasLogin) {
		m.Handle(r.pola, middleware.Rantai(r.handler, r.middleware...))
	}
	// Berlaku untuk semua rute.
	return middleware.Rantai(m, middleware.Catat(), middleware.Pulih(),
		middleware.BatasLaju(opsi.BatasUmum, opsi.PercayaiProxy), middleware.BatasWaktu(BatasWaktuPermintaan))
}
