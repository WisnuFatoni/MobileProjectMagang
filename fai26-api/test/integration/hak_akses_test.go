package integration

import (
	"encoding/json"
	"sort"
	"testing"
)

type bacaanHak struct {
	Role []struct {
		RoleID     int64    `json:"role_id"`
		Kode       string   `json:"kode"`
		Permission []string `json:"permission"`
	} `json:"role"`
	Permission []struct {
		Kode  string `json:"kode"`
		Modul string `json:"modul"`
	} `json:"permission"`
}

func TestHakAksesPengelolaan(t *testing.T) {
	e := siapkanUji(t)
	_, ad := e.login("admin1", "admin12345", devAdmin)
	_, kr := e.login("krani1", "krani123", devKrani)

	baca := func() bacaanHak {
		kode, b := e.kirim("GET", "/api/admin/hak-akses", ad.AccessToken, nil, nil)
		if kode != 200 {
			t.Fatalf("GET hak-akses: %d %s", kode, b)
		}
		var h bacaanHak
		if err := json.Unmarshal(b, &h); err != nil {
			t.Fatal(err)
		}
		return h
	}
	versiMaster := func() string {
		_, b := e.kirim("GET", "/api/master", kr.AccessToken, nil, nil)
		var m struct {
			Versi string `json:"versi"`
		}
		_ = json.Unmarshal(b, &m)
		return m.Versi
	}
	idRole := func(h bacaanHak, kode string) (int64, []string) {
		for _, r := range h.Role {
			if r.Kode == kode {
				return r.RoleID, r.Permission
			}
		}
		t.Fatalf("role %s tidak ada", kode)
		return 0, nil
	}
	punya := func(daftar []string, k string) bool {
		for _, x := range daftar {
			if x == k {
				return true
			}
		}
		return false
	}

	// Hanya pemegang role.kelola: krani ditolak.
	if kode, _ := e.kirim("GET", "/api/admin/hak-akses", kr.AccessToken, nil, nil); kode != 403 {
		t.Fatalf("krani harus 403, dapat %d", kode)
	}
	if kode, _ := e.kirim("GET", "/api/admin/hak-akses", "", nil, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}

	h := baca()
	if len(h.Role) < 5 || len(h.Permission) < 15 {
		t.Fatalf("role=%d permission=%d tidak sesuai data awal", len(h.Role), len(h.Permission))
	}
	kraniID, asli := idRole(h, "KRANI")
	adminID, adminAsli := idRole(h, "ADMIN")
	if !punya(asli, "panen.kirim") || punya(asli, "laporan.lihat") {
		t.Fatalf("hak awal KRANI: %v", asli)
	}
	// Pulihkan hak asli apa pun yang terjadi.
	t.Cleanup(func() {
		e.kirim("PUT", "/api/admin/hak-akses/"+itoa(kraniID), ad.AccessToken, berkas{"permission": asli}, nil)
	})
	url := "/api/admin/hak-akses/" + itoa(kraniID)
	v0 := versiMaster()

	// Tanpa perubahan: 200, versi master tidak berubah dan tidak ada audit baru.
	auditSebelum := hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE tabel='role_permission'`)
	e.json("PUT", url, ad.AccessToken, berkas{"permission": asli}, 200)
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE tabel='role_permission'`) != auditSebelum || versiMaster() != v0 {
		t.Fatal("PUT tanpa perubahan tidak boleh mengubah versi master atau menulis audit")
	}

	// Menambah hak: terlihat di daftar, di master (HP), dan langsung berlaku bagi krani di server.
	tambah := append(append([]string{}, asli...), "laporan.lihat")
	e.json("PUT", url, ad.AccessToken, berkas{"permission": tambah}, 200)
	_, nowKr := idRole(baca(), "KRANI")
	if !punya(nowKr, "laporan.lihat") {
		t.Fatalf("hak baru tidak terlihat: %v", nowKr)
	}
	v1 := versiMaster()
	if v1 == v0 {
		t.Fatal("versi master harus berubah setelah hak role berubah")
	}
	_, b := e.kirim("GET", "/api/auth/me", kr.AccessToken, nil, nil)
	var me struct {
		Permission []string `json:"permission"`
	}
	_ = json.Unmarshal(b, &me)
	if !punya(me.Permission, "laporan.lihat") {
		t.Fatalf("hak baru harus langsung berlaku di server: %v", me.Permission)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE tabel='role_permission' AND aksi='UBAH' AND LEFT(keterangan, 22) = 'KRANI: +[laporan.lihat'`) != 1 {
		t.Fatal("audit perubahan hak harus tercatat dengan rincian tambah dan cabut")
	}

	// Mencabut lagi (hapus lunak) lalu menambah lagi (dihidupkan kembali, bukan baris ganda).
	e.json("PUT", url, ad.AccessToken, berkas{"permission": asli}, 200)
	if _, k := idRole(baca(), "KRANI"); punya(k, "laporan.lihat") {
		t.Fatalf("hak yang dicabut masih ada: %v", k)
	}
	e.json("PUT", url, ad.AccessToken, berkas{"permission": tambah}, 200)
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.role_permission WHERE role_id=@p1 AND permission_id=(SELECT permission_id FROM dbo.permission WHERE kode='laporan.lihat')`, kraniID) != 1 {
		t.Fatal("menambah ulang harus menghidupkan baris lama, bukan membuat ganda")
	}
	e.json("PUT", url, ad.AccessToken, berkas{"permission": asli}, 200)
	if v := versiMaster(); v == v1 || v == v0 {
		// versi berubah setiap perubahan nyata (waktu ubah baru), jadi tidak boleh sama dengan yang lama
		t.Fatalf("versi master harus selalu baru setelah perubahan: %s (v0=%s v1=%s)", v, v0, v1)
	}

	// Role tanpa hak sama sekali boleh, lalu dipulihkan.
	e.json("PUT", url, ad.AccessToken, berkas{"permission": []string{}}, 200)
	if _, k := idRole(baca(), "KRANI"); len(k) != 0 {
		t.Fatalf("role harus tanpa hak: %v", k)
	}
	e.json("PUT", url, ad.AccessToken, berkas{"permission": asli}, 200)
	_, pulih := idRole(baca(), "KRANI")
	sort.Strings(pulih)
	a2 := append([]string{}, asli...)
	sort.Strings(a2)
	if len(pulih) != len(a2) {
		t.Fatalf("hak harus pulih: %v vs %v", pulih, a2)
	}

	// Pengaman dan validasi.
	e.json("PUT", "/api/admin/hak-akses/"+itoa(adminID), ad.AccessToken, berkas{"permission": []string{"panen.lihat"}}, 409)
	if _, k := idRole(baca(), "ADMIN"); !punya(k, "role.kelola") || len(k) != len(adminAsli) {
		t.Fatal("penolakan 409 tidak boleh mengubah hak ADMIN")
	}
	e.json("PUT", url, ad.AccessToken, berkas{"permission": []string{"panen.lihat", "izin.tidak.ada"}}, 422)
	e.json("PUT", url, ad.AccessToken, berkas{}, 422)
	e.json("PUT", "/api/admin/hak-akses/999999", ad.AccessToken, berkas{"permission": []string{"panen.lihat"}}, 404)
	if _, k := idRole(baca(), "KRANI"); len(k) != len(asli) {
		t.Fatalf("permintaan yang ditolak tidak boleh mengubah hak: %v", k)
	}
}
