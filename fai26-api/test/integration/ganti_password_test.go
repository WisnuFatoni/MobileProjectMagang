package integration

import (
	"testing"

	"fai26-api/internal/auth"
)

func TestGantiPasswordSendiri(t *testing.T) {
	e := siapkanUji(t)
	// Password seed dikembalikan di akhir supaya tes lain tidak terpengaruh.
	t.Cleanup(func() {
		h, _ := auth.HashPassword("krani123")
		_, _ = e.db.Exec(`UPDATE dbo.app_user SET password_hash=@p1, wajib_ganti_password=0, gagal_login=0, terkunci_sampai=NULL WHERE username='krani1'`, h)
	})

	_, a := e.login("krani1", "krani123", devKrani) // perangkat yang dipakai mengganti
	_, b := e.login("krani1", "krani123", devLain)  // perangkat lain: sesinya harus dicabut

	// Tanpa token ditolak.
	if kode, _ := e.kirim("POST", "/api/auth/password", "", berkas{"password_lama": "krani123", "password_baru": "PasswordBaru#2026"}, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}
	// Password lama salah: 422 (bukan 401), dan password tidak berubah.
	if kode, bd := e.kirim("POST", "/api/auth/password", a.AccessToken, berkas{"password_lama": "salah", "password_baru": "PasswordBaru#2026"}, nil); kode != 422 {
		t.Fatalf("password lama salah harus 422, dapat %d %s", kode, bd)
	}
	if kode, _ := e.login("krani1", "krani123", devKrani); kode != 200 {
		t.Fatalf("password lama harus masih berlaku, dapat %d", kode)
	}
	// Aturan password baru.
	for nama, badan := range map[string]berkas{
		"pendek":     {"password_lama": "krani123", "password_baru": "abc123"},
		"sama":       {"password_lama": "krani123", "password_baru": "krani123"},
		"tanpa lama": {"password_baru": "PasswordBaru#2026"},
	} {
		if kode, bd := e.kirim("POST", "/api/auth/password", a.AccessToken, badan, nil); kode != 422 && kode != 400 {
			t.Errorf("%s: harus 400 atau 422, dapat %d %s", nama, kode, bd)
		}
	}

	// Berhasil.
	e.json("POST", "/api/auth/password", a.AccessToken, berkas{"password_lama": "krani123", "password_baru": "PasswordBaru#2026"}, 200)

	if kode, _ := e.login("krani1", "krani123", devKrani); kode != 401 {
		t.Fatalf("password lama harus ditolak setelah diganti, dapat %d", kode)
	}
	kode, baru := e.login("krani1", "PasswordBaru#2026", devKrani)
	if kode != 200 || baru.Profil.WajibGanti {
		t.Fatalf("password baru harus bisa login dan tidak lagi wajib ganti: %d %+v", kode, baru.Profil)
	}
	// Perangkat yang dipakai mengganti tetap masuk; perangkat lain dicabut sesinya.
	if kode, _ := e.kirim("POST", "/api/auth/refresh", "", berkas{"refresh_token": b.RefreshToken}, nil); kode != 401 {
		t.Fatalf("sesi perangkat lain harus dicabut (401), dapat %d", kode)
	}
	if kode, _ := e.kirim("POST", "/api/auth/refresh", "", berkas{"refresh_token": a.RefreshToken}, nil); kode != 200 {
		t.Fatalf("sesi perangkat ini harus tetap berlaku, dapat %d", kode)
	}
	// Audit.
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='GANTI_PASSWORD'`) != 1 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='GANTI_PASSWORD_GAGAL'`) != 1 {
		t.Fatal("audit GANTI_PASSWORD dan GANTI_PASSWORD_GAGAL harus tepat satu baris masing-masing")
	}

	// Wajib ganti password (setelah reset admin) dihapus oleh penggantian sendiri.
	_, ad := e.login("admin1", "admin12345", devAdmin)
	var uid int64
	if err := e.db.QueryRow(`SELECT user_id FROM dbo.app_user WHERE username='krani1'`).Scan(&uid); err != nil {
		t.Fatal(err)
	}
	e.json("POST", "/api/admin/pengguna/"+itoa(uid)+"/reset-password", ad.AccessToken, berkas{"password": "SementaraAdmin1"}, 200)
	_, r2 := e.login("krani1", "SementaraAdmin1", devKrani)
	if !r2.Profil.WajibGanti {
		t.Fatal("setelah reset admin harus wajib ganti password")
	}
	e.json("POST", "/api/auth/password", r2.AccessToken, berkas{"password_lama": "SementaraAdmin1", "password_baru": "PilihanSendiri#9"}, 200)
	if _, r3 := e.login("krani1", "PilihanSendiri#9", devKrani); r3.Profil.WajibGanti {
		t.Fatal("wajib ganti password harus hilang setelah mengganti sendiri")
	}
}

func itoa(n int64) string {
	return string(appendInt(nil, n))
}

func appendInt(b []byte, n int64) []byte {
	if n == 0 {
		return append(b, '0')
	}
	var d [20]byte
	i := len(d)
	for n > 0 {
		i--
		d[i] = byte('0' + n%10)
		n /= 10
	}
	return append(b, d[i:]...)
}
