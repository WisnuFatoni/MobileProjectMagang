package service

import (
	"context"
	"errors"
	"log"
	"strings"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

type AuthConfig struct {
	RefreshTTL  time.Duration
	MaksGagal   int
	DurasiKunci time.Duration
}

type AuthService struct {
	users    UserRepo
	devices  DeviceRepo
	sessions SessionRepo
	audit    AuditRepo
	tokens   *auth.TokenManager
	cfg      AuthConfig
}

func NewAuthService(users UserRepo, devices DeviceRepo, sessions SessionRepo, audit AuditRepo, tokens *auth.TokenManager, cfg AuthConfig) *AuthService {
	return &AuthService{users: users, devices: devices, sessions: sessions, audit: audit, tokens: tokens, cfg: cfg}
}

// catat menulis audit sebagai pelengkap: gagal menulis audit tidak boleh menggagalkan login.
func (s *AuthService) catat(ctx context.Context, a model.AuditBaris) {
	if err := s.audit.Tulis(ctx, a); err != nil {
		log.Printf("audit gagal ditulis (%s %s): %v", a.Aksi, a.BarisID, err)
	}
}

func (s *AuthService) Login(ctx context.Context, req model.LoginRequest, ip string) (*model.LoginResponse, error) {
	req.Username = strings.TrimSpace(req.Username)
	if req.Username == "" || req.Password == "" || !model.ReUUID.MatchString(req.DeviceID) {
		return nil, apperror.BadRequest("username, password, dan device_id (UUID) wajib diisi")
	}
	gagal := func(uid *int64, ket string) {
		s.catat(ctx, model.AuditBaris{Tabel: "app_user", BarisID: req.Username, Aksi: "LOGIN_GAGAL",
			UserID: uid, Username: req.Username, DeviceID: req.DeviceID, IP: ip, Keterangan: ket})
	}

	u, err := s.users.CariByUsername(ctx, req.Username)
	if errors.Is(err, repository.ErrNotFound) {
		auth.CocokPassword(auth.HashDummy, req.Password) // samakan waktu respons
		gagal(nil, "username tidak ada")
		return nil, apperror.Unauthorized("Username atau password salah")
	}
	if err != nil {
		return nil, apperror.Internal("Gagal membaca akun", err)
	}
	if !u.Aktif {
		return nil, apperror.Forbidden("Akun tidak aktif")
	}
	if u.Terkunci {
		return nil, apperror.Locked("Akun terkunci sementara. Coba lagi nanti")
	}
	if !auth.CocokPassword(u.PasswordHash, req.Password) {
		hitung := u.GagalLogin + 1
		kunci := 0
		if hitung >= s.cfg.MaksGagal {
			kunci = int(s.cfg.DurasiKunci.Seconds())
		}
		if err := s.users.CatatGagal(ctx, u.ID, hitung, kunci); err != nil {
			log.Printf("gagal mencatat percobaan login: %v", err)
		}
		gagal(&u.ID, "password salah")
		return nil, apperror.Unauthorized("Username atau password salah")
	}

	// HP yang dinonaktifkan admin tidak boleh masuk.
	err = s.devices.Daftarkan(ctx, model.Perangkat{ID: req.DeviceID, Nama: req.DeviceNama, OSVersi: req.OSVersi, AppVersi: req.AppVersi}, u.ID)
	if errors.Is(err, repository.ErrPerangkatNonaktif) {
		return nil, apperror.Forbidden("Perangkat ini dinonaktifkan. Hubungi admin")
	}
	if err != nil {
		return nil, apperror.Internal("Gagal mencatat perangkat", err)
	}

	refresh, err := auth.TokenAcak()
	if err != nil {
		return nil, apperror.Internal("Gagal membuat sesi", err)
	}
	if err := s.sessions.Buat(ctx, u.ID, req.DeviceID, auth.SHA256Hex(refresh), s.cfg.RefreshTTL, ip); err != nil {
		return nil, apperror.Internal("Gagal menyimpan sesi", err)
	}
	if err := s.users.CatatLoginSukses(ctx, u.ID); err != nil {
		log.Printf("gagal mencatat login sukses: %v", err)
	}
	s.catat(ctx, model.AuditBaris{Tabel: "app_user", BarisID: req.Username, Aksi: "LOGIN", UserID: &u.ID,
		Username: req.Username, DeviceID: req.DeviceID, IP: ip})

	akses, exp, err := s.tokens.Buat(u.ID, u.UnitID, req.DeviceID)
	if err != nil {
		return nil, apperror.Internal("Gagal membuat token", err)
	}
	roles, err := s.users.Role(ctx, u.ID)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca role", err)
	}
	perms, err := s.users.Permission(ctx, u.ID)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca permission", err)
	}
	return &model.LoginResponse{
		AccessToken: akses, AccessExpires: exp, RefreshToken: refresh,
		Profil: model.Profil{UserID: u.ID, UnitID: u.UnitID, UnitKode: u.UnitKode, Username: u.Username, Nama: u.Nama,
			Role: roles, Permission: perms, WajibGanti: u.WajibGanti,
			MandorHrmbCode: u.MandorHrmbCode, AfdelingKode: u.AfdelingKode},
	}, nil
}

// Refresh menukar refresh token dengan token akses baru. Refresh token diganti tiap dipakai (rotasi).
func (s *AuthService) Refresh(ctx context.Context, refreshToken string) (*model.RefreshResponse, error) {
	if refreshToken == "" {
		return nil, apperror.BadRequest("refresh_token wajib diisi")
	}
	sesi, err := s.sessions.CariAktif(ctx, auth.SHA256Hex(refreshToken))
	if errors.Is(err, repository.ErrNotFound) {
		return nil, apperror.Unauthorized("Sesi tidak berlaku. Login ulang")
	}
	if err != nil {
		return nil, apperror.Internal("Gagal memeriksa sesi", err)
	}
	baru, err := auth.TokenAcak()
	if err != nil {
		return nil, apperror.Internal("Gagal membuat sesi", err)
	}
	if err := s.sessions.Rotasi(ctx, sesi.ID, auth.SHA256Hex(baru), s.cfg.RefreshTTL); err != nil {
		return nil, apperror.Internal("Gagal memperbarui sesi", err)
	}
	akses, exp, err := s.tokens.Buat(sesi.UserID, sesi.UnitID, sesi.DeviceID)
	if err != nil {
		return nil, apperror.Internal("Gagal membuat token", err)
	}
	return &model.RefreshResponse{AccessToken: akses, AccessExpires: exp, RefreshToken: baru}, nil
}

func (s *AuthService) Logout(ctx context.Context, userID int64, deviceID string) error {
	if err := s.sessions.Cabut(ctx, userID, deviceID); err != nil {
		return apperror.Internal("Gagal mencabut sesi", err)
	}
	return nil
}

func (s *AuthService) Me(ctx context.Context, userID int64) (*model.Profil, error) {
	p, err := s.users.Profil(ctx, userID)
	if errors.Is(err, repository.ErrNotFound) {
		return nil, apperror.NotFound("Akun tidak ditemukan")
	}
	if err != nil {
		return nil, apperror.Internal("Gagal membaca profil", err)
	}
	if p.Role, err = s.users.Role(ctx, userID); err != nil {
		return nil, apperror.Internal("Gagal membaca role", err)
	}
	if p.Permission, err = s.users.Permission(ctx, userID); err != nil {
		return nil, apperror.Internal("Gagal membaca permission", err)
	}
	return p, nil
}

// GantiPassword mengganti password milik sendiri. Password lama wajib benar (salah berulang mengunci akun seperti
// login), password baru harus lolos aturan yang sama dengan pembuatan akun dan berbeda dari yang lama. Sesi di
// perangkat lain dicabut; perangkat ini tetap masuk.
func (s *AuthService) GantiPassword(ctx context.Context, userID int64, deviceID string, req model.GantiPasswordRequest, ip string) error {
	if req.PasswordLama == "" {
		return apperror.BadRequest("password_lama wajib diisi")
	}
	if err := validasiPassword(req.PasswordBaru); err != nil {
		return apperror.Unprocessable(err.Error())
	}
	if req.PasswordBaru == req.PasswordLama {
		return apperror.Unprocessable("Password baru harus berbeda dari password lama")
	}
	p, err := s.users.Profil(ctx, userID)
	if errors.Is(err, repository.ErrNotFound) {
		return apperror.NotFound("Akun tidak ditemukan")
	}
	if err != nil {
		return apperror.Internal("Gagal membaca akun", err)
	}
	u, err := s.users.CariByUsername(ctx, p.Username)
	if errors.Is(err, repository.ErrNotFound) {
		return apperror.NotFound("Akun tidak ditemukan")
	}
	if err != nil {
		return apperror.Internal("Gagal membaca akun", err)
	}
	if !u.Aktif {
		return apperror.Forbidden("Akun tidak aktif")
	}
	if u.Terkunci {
		return apperror.Locked("Akun terkunci sementara. Coba lagi nanti")
	}
	if !auth.CocokPassword(u.PasswordHash, req.PasswordLama) {
		hitung := u.GagalLogin + 1
		kunci := 0
		if hitung >= s.cfg.MaksGagal {
			kunci = int(s.cfg.DurasiKunci.Seconds())
		}
		if err := s.users.CatatGagal(ctx, u.ID, hitung, kunci); err != nil {
			log.Printf("gagal mencatat percobaan ganti password: %v", err)
		}
		s.catat(ctx, model.AuditBaris{Tabel: "app_user", BarisID: u.Username, Aksi: "GANTI_PASSWORD_GAGAL", UserID: &u.ID,
			Username: u.Username, DeviceID: deviceID, IP: ip, Keterangan: "password lama salah"})
		// 422 (bukan 401): 401 berarti token tidak sah dan membuat klien menyangka sesinya habis.
		return apperror.Unprocessable("Password lama salah")
	}
	hash, err := auth.HashPassword(req.PasswordBaru)
	if err != nil {
		return apperror.Internal("Gagal mengamankan password", err)
	}
	if err := s.users.GantiPassword(ctx, u.ID, hash, deviceID); err != nil {
		return apperror.Internal("Gagal menyimpan password baru", err)
	}
	s.catat(ctx, model.AuditBaris{Tabel: "app_user", BarisID: u.Username, Aksi: "GANTI_PASSWORD", UserID: &u.ID,
		Username: u.Username, DeviceID: deviceID, IP: ip})
	return nil
}
