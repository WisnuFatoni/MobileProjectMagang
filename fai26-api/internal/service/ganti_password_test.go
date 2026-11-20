package service

import (
	"context"
	"errors"
	"strings"
	"testing"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

func gp(lama, baru string) model.GantiPasswordRequest {
	return model.GantiPasswordRequest{PasswordLama: lama, PasswordBaru: baru}
}

func TestGantiPasswordBerhasilMenyimpanHashBaruDanMempertahankanPerangkatIni(t *testing.T) {
	r := rakitAuth(t)
	if err := r.svc.GantiPassword(context.Background(), 10, devID, gp("krani123", "PasswordBaru#2026"), "1.2.3.4"); err != nil {
		t.Fatal(err)
	}
	if len(r.users.gantiPw) != 1 {
		t.Fatalf("GantiPassword harus dipanggil sekali: %v", r.users.gantiPw)
	}
	parts := strings.SplitN(r.users.gantiPw[0], "|", 2)
	if !auth.CocokPassword(parts[0], "PasswordBaru#2026") || strings.Contains(parts[0], "PasswordBaru") {
		t.Fatal("yang disimpan harus hash bcrypt dari password baru, bukan teksnya")
	}
	if parts[1] != devID {
		t.Fatalf("perangkat yang dipertahankan harus perangkat ini: %q", parts[1])
	}
	if n := len(r.audit.baris); n != 1 || r.audit.baris[0].Aksi != "GANTI_PASSWORD" || r.audit.baris[0].IP != "1.2.3.4" {
		t.Fatalf("audit: %+v", r.audit.baris)
	}
}

func TestGantiPasswordLamaSalahDitolak422TanpaMenyimpanDanMenghitungGagal(t *testing.T) {
	r := rakitAuth(t)
	err := r.svc.GantiPassword(context.Background(), 10, devID, gp("salah", "PasswordBaru#2026"), "")
	if statusHTTP(err) != 422 {
		t.Fatalf("password lama salah harus 422 (bukan 401), dapat %v", err)
	}
	if len(r.users.gantiPw) != 0 {
		t.Fatal("tidak boleh menyimpan bila password lama salah")
	}
	if len(r.users.gagal) != 1 || r.users.gagal[0] != 1 {
		t.Fatalf("percobaan salah harus dihitung seperti login: %v", r.users.gagal)
	}
	if r.audit.baris[0].Aksi != "GANTI_PASSWORD_GAGAL" {
		t.Fatalf("audit: %+v", r.audit.baris)
	}
}

func TestGantiPasswordSalahBerulangMengunciAkun(t *testing.T) {
	r := rakitAuth(t)
	r.users.user.GagalLogin = 4 // percobaan ke-5 mencapai batas
	_ = r.svc.GantiPassword(context.Background(), 10, devID, gp("salah", "PasswordBaru#2026"), "")
	if len(r.users.kunci) != 1 || r.users.kunci[0] != int((15*60)) {
		t.Fatalf("akun harus dikunci 15 menit pada percobaan ke-5: %v", r.users.kunci)
	}
}

func TestGantiPasswordAkunTerkunciAtauNonaktif(t *testing.T) {
	r := rakitAuth(t)
	r.users.user.Terkunci = true
	if err := r.svc.GantiPassword(context.Background(), 10, devID, gp("krani123", "PasswordBaru#2026"), ""); statusHTTP(err) != 423 {
		t.Fatalf("terkunci harus 423: %v", err)
	}
	r.users.user.Terkunci = false
	r.users.user.Aktif = false
	if err := r.svc.GantiPassword(context.Background(), 10, devID, gp("krani123", "PasswordBaru#2026"), ""); statusHTTP(err) != 403 {
		t.Fatalf("nonaktif harus 403: %v", err)
	}
	if len(r.users.gantiPw) != 0 {
		t.Fatal("tidak boleh menyimpan")
	}
}

func TestGantiPasswordAturanPasswordBaru(t *testing.T) {
	r := rakitAuth(t)
	kasus := map[string]model.GantiPasswordRequest{
		"terlalu pendek":       gp("krani123", "abc1234"),
		"terlalu panjang":      gp("krani123", strings.Repeat("a", 73)),
		"sama dengan lama":     gp("krani123", "krani123"),
		"kosong":               gp("krani123", ""),
		"password lama kosong": gp("", "PasswordBaru#2026"),
	}
	for nama, in := range kasus {
		err := r.svc.GantiPassword(context.Background(), 10, devID, in, "")
		kode := statusHTTP(err)
		if kode != 422 && kode != 400 {
			t.Errorf("%s: harus 400 atau 422, dapat %v", nama, err)
		}
	}
	if len(r.users.gantiPw) != 0 || len(r.users.gagal) != 0 {
		t.Fatalf("input tidak sah tidak boleh menyentuh data atau menghitung gagal: %v %v", r.users.gantiPw, r.users.gagal)
	}
}

func TestGantiPasswordAkunTidakAdaDanGalatDatabase(t *testing.T) {
	r := rakitAuth(t)
	r.users.user = nil
	if err := r.svc.GantiPassword(context.Background(), 10, devID, gp("krani123", "PasswordBaru#2026"), ""); statusHTTP(err) != 404 {
		t.Fatalf("akun tidak ada harus 404: %v", err)
	}

	r = rakitAuth(t)
	r.users.errGanti = errors.New("Invalid TDS stream: rincian internal")
	err := r.svc.GantiPassword(context.Background(), 10, devID, gp("krani123", "PasswordBaru#2026"), "")
	if statusHTTP(err) != 500 && statusHTTP(err) != 503 {
		t.Fatalf("galat database harus 5xx: %v", err)
	}
	if strings.Contains(err.Error(), "TDS") && statusHTTP(err) == 200 {
		t.Fatal("rincian internal tidak boleh bocor")
	}
}
