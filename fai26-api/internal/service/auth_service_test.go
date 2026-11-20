package service

import (
	"context"
	"net/http"
	"testing"
	"time"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

const devID = "11111111-1111-4111-8111-111111111111"

type rakitanAuth struct {
	svc      *AuthService
	users    *fakeUsers
	devices  *fakeDevices
	sessions *fakeSessions
	audit    *fakeAudit
	tokens   *auth.TokenManager
}

func rakitAuth(t *testing.T) rakitanAuth {
	t.Helper()
	hash, err := auth.HashPassword("krani123")
	if err != nil {
		t.Fatal(err)
	}
	users := &fakeUsers{
		user:  &model.User{ID: 10, UnitID: 1, UnitKode: "KP_STL", Username: "krani1", Nama: "Krani", PasswordHash: hash, Aktif: true},
		perms: map[int64][]string{10: {"panen.kirim"}},
		aktif: map[int64]bool{10: true},
	}
	devices, sessions, audit := &fakeDevices{}, &fakeSessions{}, &fakeAudit{}
	tokens := auth.NewTokenManager([]byte("rahasia-uji-yang-cukup-panjang-123456"), 15*time.Minute)
	svc := NewAuthService(users, devices, sessions, audit, tokens, AuthConfig{RefreshTTL: time.Hour, MaksGagal: 5, DurasiKunci: 15 * time.Minute})
	return rakitanAuth{svc, users, devices, sessions, audit, tokens}
}

func req(user, pass string) model.LoginRequest {
	return model.LoginRequest{Username: user, Password: pass, DeviceID: devID}
}

func TestLoginBerhasil(t *testing.T) {
	r := rakitAuth(t)
	out, err := r.svc.Login(context.Background(), req("krani1", "krani123"), "1.1.1.1")
	if err != nil {
		t.Fatal(err)
	}
	k, err := r.tokens.Periksa(out.AccessToken)
	if err != nil || k.UserID != 10 || k.UnitID != 1 || k.DeviceID != devID {
		t.Fatalf("token tidak sesuai: %+v err=%v", k, err)
	}
	// Yang disimpan di database adalah hash refresh token, bukan token-nya.
	if len(r.sessions.dibuat) != 1 || r.sessions.dibuat[0] != auth.SHA256Hex(out.RefreshToken) || r.sessions.dibuat[0] == out.RefreshToken {
		t.Fatal("refresh token harus disimpan sebagai hash")
	}
	if len(out.Profil.Permission) != 1 || r.users.sukses != 1 {
		t.Fatalf("profil/catatan sukses salah: %+v", out.Profil)
	}
	if len(r.audit.baris) != 1 || r.audit.baris[0].Aksi != "LOGIN" {
		t.Fatalf("audit LOGIN tidak tercatat: %+v", r.audit.baris)
	}
}

func TestLoginGagal(t *testing.T) {
	r := rakitAuth(t)
	if _, err := r.svc.Login(context.Background(), req("krani1", "salah"), ""); statusHTTP(err) != 401 {
		t.Fatalf("password salah: %v", err)
	}
	if len(r.users.gagal) != 1 || r.users.gagal[0] != 1 || r.users.kunci[0] != 0 {
		t.Fatalf("hitungan gagal salah: %v %v", r.users.gagal, r.users.kunci)
	}
	r.users.user = nil
	if _, err := r.svc.Login(context.Background(), req("hantu", "x"), ""); statusHTTP(err) != 401 {
		t.Fatalf("username tak ada: %v", err)
	}
	if n := len(r.audit.baris); n != 2 || r.audit.baris[0].Aksi != "LOGIN_GAGAL" || r.audit.baris[1].Aksi != "LOGIN_GAGAL" {
		t.Fatalf("audit LOGIN_GAGAL: %+v", r.audit.baris)
	}
}

func TestPercobaanKelimaMengunciAkun(t *testing.T) {
	r := rakitAuth(t)
	r.users.user.GagalLogin = 4
	if _, err := r.svc.Login(context.Background(), req("krani1", "salah"), ""); statusHTTP(err) != 401 {
		t.Fatal(err)
	}
	if r.users.kunci[0] != 900 { // 15 menit
		t.Fatalf("akun seharusnya dikunci 900 detik, dapat %v", r.users.kunci)
	}
}

func TestAkunTerkunciDitolakMeskiPasswordBenar(t *testing.T) {
	r := rakitAuth(t)
	r.users.user.Terkunci = true
	_, err := r.svc.Login(context.Background(), req("krani1", "krani123"), "")
	if statusHTTP(err) != http.StatusLocked {
		t.Fatalf("harapan 423, dapat %v", err)
	}
	if len(r.sessions.dibuat) != 0 {
		t.Fatal("sesi tidak boleh dibuat")
	}
}

func TestAkunNonaktifDanPerangkatNonaktif(t *testing.T) {
	r := rakitAuth(t)
	r.users.user.Aktif = false
	if _, err := r.svc.Login(context.Background(), req("krani1", "krani123"), ""); statusHTTP(err) != 403 {
		t.Fatalf("akun nonaktif: %v", err)
	}
	r = rakitAuth(t)
	r.devices.nonaktif = true
	if _, err := r.svc.Login(context.Background(), req("krani1", "krani123"), ""); statusHTTP(err) != 403 {
		t.Fatalf("perangkat nonaktif: %v", err)
	}
	if len(r.sessions.dibuat) != 0 {
		t.Fatal("sesi tidak boleh dibuat untuk perangkat nonaktif")
	}
}

func TestLoginMasukanTidakValid(t *testing.T) {
	r := rakitAuth(t)
	for _, in := range []model.LoginRequest{
		{Username: "", Password: "x", DeviceID: devID},
		{Username: "krani1", Password: "", DeviceID: devID},
		{Username: "krani1", Password: "x", DeviceID: "bukan-uuid"},
	} {
		if _, err := r.svc.Login(context.Background(), in, ""); statusHTTP(err) != 400 {
			t.Fatalf("%+v: %v", in, err)
		}
	}
}

func TestRefreshMengganti(t *testing.T) {
	r := rakitAuth(t)
	if _, err := r.svc.Refresh(context.Background(), "tidak-ada"); statusHTTP(err) != 401 {
		t.Fatalf("refresh tak dikenal harus 401: %v", err)
	}
	if _, err := r.svc.Refresh(context.Background(), ""); statusHTTP(err) != 400 {
		t.Fatalf("refresh kosong harus 400: %v", err)
	}
	r.sessions.sesi = &model.Sesi{ID: "s1", UserID: 10, UnitID: 1, DeviceID: devID}
	out, err := r.svc.Refresh(context.Background(), "lama")
	if err != nil {
		t.Fatal(err)
	}
	if len(r.sessions.rotasi) != 1 || r.sessions.rotasi[0] != auth.SHA256Hex(out.RefreshToken) || out.RefreshToken == "lama" {
		t.Fatal("refresh token harus diganti dan disimpan sebagai hash")
	}
}
