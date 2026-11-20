package integration

import (
	"bytes"
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"fai26-api/internal/app"
	"fai26-api/internal/auth"
	"fai26-api/internal/config"
	"fai26-api/internal/model"
	_ "github.com/microsoft/go-mssqldb"
	_ "github.com/microsoft/go-mssqldb/namedpipe"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

// Tes ini berjalan terhadap database uji SQL Server yang sudah berisi skema (erd/fAI26_server_sqlserver.sql).
// Atur FAI26_TEST_DSN (tidak ada nilai bawaan); tanpa itu atau bila tidak tersambung, tes dilewati. JANGAN diarahkan ke database sungguhan:
// tes menghapus isi tabel fai_*, auth_sesi, dan audit_log.

type lingkunganUji struct {
	t   *testing.T
	db  *sql.DB
	srv *httptest.Server
	cfg config.Config
}

func siapkanUji(t *testing.T) *lingkunganUji {
	t.Helper()
	dsn := os.Getenv("FAI26_TEST_DSN")
	if dsn == "" {
		t.Skip("FAI26_TEST_DSN belum diisi: tes integrasi dilewati")
	}
	db, err := sql.Open("sqlserver", dsn)
	if err != nil {
		t.Skip("tidak bisa membuka database uji: ", err)
	}
	db.SetMaxOpenConns(2) // named pipe lokal sering macet bila banyak sambungan bersamaan
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		t.Skip("database uji tidak tersambung: ", err)
	}
	// Pengaman: tes ini MENGHAPUS isi tabel panen/sesi/audit dan menambah akun contoh. Hanya jalan di database khusus tes.
	var namaDB string
	if err := db.QueryRowContext(ctx, `SELECT DB_NAME()`).Scan(&namaDB); err != nil || !strings.HasSuffix(namaDB, "_tes") {
		t.Skip("tes integrasi hanya berjalan di database berakhiran _tes (database ini: " + namaDB + "); buat database khusus tes, lihat README")
	}
	var tabel int
	if err := db.QueryRowContext(ctx, `SELECT COUNT(*) FROM sys.tables WHERE name='fai_panen'`).Scan(&tabel); err != nil || tabel == 0 {
		t.Skip("skema fai26 belum ada di database uji: ", err, " tabel=", tabel)
	}
	for _, q := range []string{
		`DELETE FROM dbo.fai_impor_log`, `DELETE FROM dbo.fai_foto`, `DELETE FROM dbo.fai_panen_pemanen`,
		`DELETE FROM dbo.fai_panen_grading`, `DELETE FROM dbo.fai_panen`, `DELETE FROM dbo.auth_sesi`,
		`DELETE FROM dbo.audit_log`, `DELETE FROM dbo.device`,
	} {
		if _, err := db.ExecContext(ctx, q); err != nil {
			t.Fatalf("membersihkan data uji (%s): %v", q, err)
		}
	}
	if err := seedDev(ctx, db); err != nil {
		t.Fatal("seed-dev: ", err)
	}
	if _, err := db.ExecContext(ctx, `UPDATE dbo.app_user SET gagal_login=0, terkunci_sampai=NULL, aktif=1`); err != nil {
		t.Fatal(err)
	}
	cfg := config.Config{
		JWTSecret: []byte("rahasia-uji-yang-cukup-panjang-123456"), MediaDir: t.TempDir(),
		AksesTTL: 15 * time.Minute, RefreshTTL: time.Hour, MaksGagal: 5, DurasiKunci: 15 * time.Minute,
	}
	srv := httptest.NewServer(app.Bangun(cfg, db))
	t.Cleanup(func() { srv.Close(); db.Close() })
	return &lingkunganUji{t: t, db: db, srv: srv, cfg: cfg}
}

func (e *lingkunganUji) kirim(metode, path, token string, badan any, header map[string]string) (int, []byte) {
	e.t.Helper()
	var rd io.Reader
	switch v := badan.(type) {
	case nil:
	case []byte:
		rd = bytes.NewReader(v)
	default:
		b, _ := json.Marshal(v)
		rd = bytes.NewReader(b)
	}
	req, _ := http.NewRequest(metode, e.srv.URL+path, rd)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	if _, ok := badan.([]byte); !ok && badan != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	for k, v := range header {
		req.Header.Set(k, v)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		e.t.Fatal(err)
	}
	defer res.Body.Close()
	b, _ := io.ReadAll(res.Body)
	return res.StatusCode, b
}

const (
	devKrani = "11111111-1111-4111-8111-111111111111"
	devLain  = "22222222-2222-4222-8222-222222222222"
)

func (e *lingkunganUji) login(user, pass, dev string) (int, model.LoginResponse) {
	e.t.Helper()
	kode, b := e.kirim("POST", "/api/auth/login", "", model.LoginRequest{Username: user, Password: pass, DeviceID: dev, DeviceNama: "HP uji"}, nil)
	var j model.LoginResponse
	_ = json.Unmarshal(b, &j)
	return kode, j
}

func panenContoh(id string) model.PanenInput {
	gps := -2.0
	return model.PanenInput{
		PanenID: id, UnitKode: "KP_STL", TanggalPanen: "2026-11-10", Asm3Oid: 1012, BlokKode: "A12", TphKode: "01",
		BrondolanKg: 12.5, GpsLat: &gps, GpsLon: &gps, StatusLokasi: "DI_TPH", ModelVersi: "best 1",
		DibuatPada: "2026-11-10T01:00:00Z", DisetujuiUsername: "mandor1", DisetujuiPada: "2026-11-10T03:00:00Z",
		Grading: []model.GradingInput{
			{KelasKode: "ripe", ErpKolom: "JJG_RIPE", JmlAI: 7, JmlFinal: 9, DiubahManual: true},
			{KelasKode: "unripe", JmlAI: 1, JmlFinal: 1},
		},
		Pemanen: []model.PemanenInput{{HrmbCode: 90001, JjgAlokasi: 10}},
	}
}

func hitung(t *testing.T, db *sql.DB, q string, args ...any) int {
	t.Helper()
	var n int
	if err := db.QueryRow(q, args...).Scan(&n); err != nil {
		t.Fatal(err)
	}
	return n
}

func TestLoginDanSesi(t *testing.T) {
	e := siapkanUji(t)

	if kode, _ := e.kirim("GET", "/api/health", "", nil, nil); kode != 200 {
		t.Fatalf("health: %d", kode)
	}
	if kode, _ := e.login("krani1", "salah", devKrani); kode != 401 {
		t.Fatalf("password salah harus 401, dapat %d", kode)
	}
	if kode, _ := e.login("tidakada", "x", devKrani); kode != 401 {
		t.Fatalf("username tak ada harus 401, dapat %d", kode)
	}
	if kode, _ := e.kirim("POST", "/api/auth/login", "", model.LoginRequest{Username: "krani1", Password: "krani123", DeviceID: "bukan-uuid"}, nil); kode != 400 {
		t.Fatalf("device_id buruk harus 400, dapat %d", kode)
	}

	kode, kr := e.login("krani1", "krani123", devKrani)
	if kode != 200 || kr.AccessToken == "" || kr.RefreshToken == "" {
		t.Fatalf("login krani gagal: %d", kode)
	}
	if !contains(kr.Profil.Permission, "panen.kirim") || contains(kr.Profil.Permission, "panen.setujui") {
		t.Fatalf("permission krani salah: %v", kr.Profil.Permission)
	}
	_, md := e.login("mandor1", "mandor123", devLain)
	if !contains(md.Profil.Permission, "panen.setujui") || md.Profil.Role[0] != "MANDOR" {
		t.Fatalf("permission mandor salah: %+v", md.Profil)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.device`) != 2 {
		t.Fatal("dua perangkat seharusnya terdaftar")
	}

	// me dan token buruk
	if kode, _ := e.kirim("GET", "/api/auth/me", kr.AccessToken, nil, nil); kode != 200 {
		t.Fatalf("me: %d", kode)
	}
	if kode, _ := e.kirim("GET", "/api/auth/me", "", nil, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}
	if kode, _ := e.kirim("GET", "/api/auth/me", kr.AccessToken+"x", nil, nil); kode != 401 {
		t.Fatalf("token rusak harus 401, dapat %d", kode)
	}

	// refresh: token diganti, yang lama tidak berlaku lagi
	kode, b := e.kirim("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": kr.RefreshToken}, nil)
	if kode != 200 {
		t.Fatalf("refresh: %d %s", kode, b)
	}
	var baru struct {
		Akses   string `json:"access_token"`
		Refresh string `json:"refresh_token"`
	}
	_ = json.Unmarshal(b, &baru)
	if baru.Refresh == "" || baru.Refresh == kr.RefreshToken {
		t.Fatal("refresh token harus diganti")
	}
	if kode, _ := e.kirim("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": kr.RefreshToken}, nil); kode != 401 {
		t.Fatalf("refresh token lama harus ditolak, dapat %d", kode)
	}

	// logout mencabut sesi
	if kode, _ := e.kirim("POST", "/api/auth/logout", baru.Akses, nil, nil); kode != 200 {
		t.Fatalf("logout: %d", kode)
	}
	if kode, _ := e.kirim("POST", "/api/auth/refresh", "", map[string]string{"refresh_token": baru.Refresh}, nil); kode != 401 {
		t.Fatalf("setelah logout refresh harus ditolak, dapat %d", kode)
	}

	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='LOGIN'`) != 2 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='LOGIN_GAGAL'`) != 2 {
		t.Fatal("audit LOGIN dan LOGIN_GAGAL tidak tercatat sesuai")
	}
}

func TestKunciAkunDanPerangkatNonaktif(t *testing.T) {
	e := siapkanUji(t)
	for i := 0; i < 5; i++ {
		if kode, _ := e.login("krani1", "salah", devKrani); kode != 401 {
			t.Fatalf("percobaan %d: %d", i, kode)
		}
	}
	// Setelah 5 kali gagal, bahkan password benar ditolak sementara.
	if kode, _ := e.login("krani1", "krani123", devKrani); kode != http.StatusLocked {
		t.Fatalf("akun harus terkunci (423), dapat %d", kode)
	}
	// Buka kunci lalu nonaktifkan perangkat.
	if _, err := e.db.Exec(`UPDATE dbo.app_user SET terkunci_sampai=NULL, gagal_login=0`); err != nil {
		t.Fatal(err)
	}
	if kode, _ := e.login("krani1", "krani123", devKrani); kode != 200 {
		t.Fatalf("login setelah kunci dibuka: %d", kode)
	}
	if _, err := e.db.Exec(`UPDATE dbo.device SET aktif=0 WHERE device_id=@p1`, devKrani); err != nil {
		t.Fatal(err)
	}
	if kode, _ := e.login("krani1", "krani123", devKrani); kode != 403 {
		t.Fatalf("perangkat nonaktif harus 403, dapat %d", kode)
	}
}

func TestTerimaPanenIdempotenDanValidasi(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)
	id := "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

	kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, panenContoh(id), nil)
	if kode != 201 {
		t.Fatalf("kirim pertama: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen WHERE id=@p1 AND status_erp='BARU' AND disetujui_oleh IS NOT NULL AND dibuat_oleh=@p2`, id, kr.Profil.UserID) != 1 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen_grading WHERE panen_id=@p1`, id) != 2 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen_pemanen WHERE panen_id=@p1 AND jjg_alokasi=10`, id) != 1 ||
		hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE aksi='TERIMA' AND baris_id=@p1`, id) != 1 {
		t.Fatal("isi catatan di database tidak sesuai")
	}
	// Dikirim ulang: tidak dobel.
	kode, b = e.kirim("POST", "/api/panen", kr.AccessToken, panenContoh(id), nil)
	if kode != 200 || !bytes.Contains(b, []byte("SUDAH_ADA")) {
		t.Fatalf("kirim ulang harus 200 SUDAH_ADA: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen`) != 1 {
		t.Fatal("catatan dobel")
	}

	kasus := []struct {
		nama string
		ubah func(p *model.PanenInput)
		kode int
	}{
		{"penyetuju krani tidak berhak", func(p *model.PanenInput) { p.DisetujuiUsername = "krani1" }, 422},
		{"penyetuju tak dikenal", func(p *model.PanenInput) { p.DisetujuiUsername = "hantu" }, 422},
		{"unit tak dikenal", func(p *model.PanenInput) { p.UnitKode = "XYZ" }, 422},
		{"status lokasi aneh", func(p *model.PanenInput) { p.StatusLokasi = "DI_BULAN" }, 422},
		{"kelas aneh", func(p *model.PanenInput) { p.Grading[0].KelasKode = "pisang" }, 422},
		{"jumlah negatif", func(p *model.PanenInput) { p.Grading[0].JmlFinal = -1 }, 422},
		{"total nol", func(p *model.PanenInput) { p.Grading[0].JmlFinal = 0; p.Grading[1].JmlFinal = 0 }, 422},
		{"tanggal buruk", func(p *model.PanenInput) { p.TanggalPanen = "10/11/2026" }, 422},
		{"uuid buruk", func(p *model.PanenInput) { p.PanenID = "123" }, 422},
	}
	for i, k := range kasus {
		p := panenContoh("bbbbbbbb-bbbb-4bbb-8bbb-00000000000" + string(rune('a'+i)))
		k.ubah(&p)
		if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, p, nil); kode != k.kode {
			t.Errorf("%s: harapan %d, dapat %d %s", k.nama, k.kode, kode, b)
		}
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_panen`) != 1 {
		t.Fatal("catatan tidak sah ikut tersimpan")
	}
}

func TestTanpaPermissionDitolak(t *testing.T) {
	e := siapkanUji(t)
	h, _ := auth.HashPassword("uji.admin23")
	if _, err := e.db.Exec(`
IF NOT EXISTS (SELECT 1 FROM dbo.app_user WHERE username='uji.admin')
BEGIN
  INSERT INTO dbo.app_user (unit_id, username, nama, password_hash) VALUES ((SELECT unit_id FROM dbo.unit WHERE kode='KP_STL'),'uji.admin','Admin',@p1);
  INSERT INTO dbo.user_role (user_id, role_id) VALUES (SCOPE_IDENTITY(), (SELECT role_id FROM dbo.role WHERE kode='ADMIN'));
END`, h); err != nil {
		t.Fatal(err)
	}
	_, ad := e.login("uji.admin", "uji.admin23", devLain)
	if kode, _ := e.kirim("POST", "/api/panen", ad.AccessToken, panenContoh("cccccccc-cccc-4ccc-8ccc-cccccccccccc"), nil); kode != 403 {
		t.Fatalf("ADMIN tidak punya panen.kirim, harus 403, dapat %d", kode)
	}
	// Akun dinonaktifkan: token yang masih berlaku langsung tidak berguna.
	_, kr := e.login("krani1", "krani123", devKrani)
	if _, err := e.db.Exec(`UPDATE dbo.app_user SET aktif=0 WHERE username='krani1'`); err != nil {
		t.Fatal(err)
	}
	if kode, _ := e.kirim("GET", "/api/auth/me", kr.AccessToken, nil, nil); kode != 403 {
		t.Fatalf("akun nonaktif harus 403, dapat %d", kode)
	}
}

func TestMediaStatusDanAudit(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)
	_, md := e.login("mandor1", "mandor123", devLain)
	id := "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
	foto := "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"

	jpeg := append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, bytes.Repeat([]byte{0x42}, 2000)...)
	h := sha256.Sum256(jpeg)
	sha := hex.EncodeToString(h[:])
	hdr := map[string]string{"X-Foto-Id": foto, "X-Sha256": sha, "Content-Type": "image/jpeg"}

	// Foto sebelum catatan: ditolak.
	if kode, _ := e.kirim("POST", "/api/panen/"+id+"/media", kr.AccessToken, jpeg, hdr); kode != 404 {
		t.Fatalf("foto tanpa catatan harus 404, dapat %d", kode)
	}
	if kode, b := e.kirim("POST", "/api/panen", kr.AccessToken, panenContoh(id), nil); kode != 201 {
		t.Fatalf("kirim catatan: %d %s", kode, b)
	}
	// Checksum salah.
	salah := map[string]string{"X-Foto-Id": foto, "X-Sha256": sha[:63] + "0"}
	if kode, _ := e.kirim("POST", "/api/panen/"+id+"/media", kr.AccessToken, jpeg, salah); kode != 422 {
		t.Fatalf("checksum salah harus 422, dapat %d", kode)
	}
	// Bukan JPEG.
	teks := []byte("bukan gambar")
	ht := sha256.Sum256(teks)
	if kode, _ := e.kirim("POST", "/api/panen/"+id+"/media", kr.AccessToken, teks,
		map[string]string{"X-Foto-Id": foto, "X-Sha256": hex.EncodeToString(ht[:])}); kode != 415 {
		t.Fatalf("non-JPEG harus 415, dapat %d", kode)
	}
	// Benar, lalu diulang.
	if kode, b := e.kirim("POST", "/api/panen/"+id+"/media", kr.AccessToken, jpeg, hdr); kode != 201 {
		t.Fatalf("foto: %d %s", kode, b)
	}
	if kode, b := e.kirim("POST", "/api/panen/"+id+"/media", kr.AccessToken, jpeg, hdr); kode != 200 || !bytes.Contains(b, []byte("SUDAH_ADA")) {
		t.Fatalf("foto ulang: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.fai_foto WHERE panen_id=@p1 AND sha256=@p2`, id, sha) != 1 {
		t.Fatal("baris fai_foto tidak sesuai")
	}
	if _, err := os.Stat(e.cfg.MediaDir + "/" + id + "/" + foto + ".jpg"); err != nil {
		t.Fatal("berkas foto tidak tersimpan: ", err)
	}

	// Status: baru dikirim = TERKIRIM; setelah ERP mengimpor = DITERIMA; ditolak = DITOLAK dengan pesan.
	baca := func(token string) []model.StatusPanen {
		kode, b := e.kirim("GET", "/api/panen?sejak=2020-01-01T00:00:00Z", token, nil, nil)
		if kode != 200 {
			t.Fatalf("status: %d %s", kode, b)
		}
		var s []model.StatusPanen
		_ = json.Unmarshal(b, &s)
		return s
	}
	s := baca(kr.AccessToken)
	if len(s) != 1 || s[0].Status != "TERKIRIM" || s[0].ID != id {
		t.Fatalf("status awal: %+v", s)
	}
	if _, err := e.db.Exec(`UPDATE dbo.fai_panen SET status_erp='DITOLAK', diubah_pada=SYSUTCDATETIME() WHERE id=@p1`, id); err != nil {
		t.Fatal(err)
	}
	if _, err := e.db.Exec(`INSERT INTO dbo.fai_impor_log (panen_id, waktu_impor, hasil, pesan_error) VALUES (@p1, SYSUTCDATETIME(), 'GAGAL', 'Blok tidak ditemukan di ERP')`, id); err != nil {
		t.Fatal(err)
	}
	s = baca(kr.AccessToken)
	if s[0].Status != "DITOLAK" || s[0].Pesan == nil || *s[0].Pesan != "Blok tidak ditemukan di ERP" {
		t.Fatalf("status ditolak: %+v", s)
	}
	if _, err := e.db.Exec(`UPDATE dbo.fai_panen SET status_erp='DIIMPOR', diubah_pada=SYSUTCDATETIME() WHERE id=@p1`, id); err != nil {
		t.Fatal(err)
	}
	if s = baca(kr.AccessToken); s[0].Status != "DITERIMA" {
		t.Fatalf("status diterima: %+v", s)
	}
	// Filter sejak di masa depan: kosong.
	kode, b := e.kirim("GET", "/api/panen?sejak=2999-01-01T00:00:00Z", kr.AccessToken, nil, nil)
	if kode != 200 || string(bytes.TrimSpace(b)) != "[]" {
		t.Fatalf("sejak masa depan: %d %s", kode, b)
	}
	// Mandor (panen.koreksi) melihat semua, krani lain tidak melihat milik orang.
	if len(baca(md.AccessToken)) != 1 {
		t.Fatal("mandor seharusnya melihat catatan krani")
	}

	// Audit batch: dobel dilewati, identitas dari token.
	a1 := "ffffffff-ffff-4fff-8fff-ffffffffff01"
	a2 := "ffffffff-ffff-4fff-8fff-ffffffffff02"
	batch := []model.AuditInput{
		{AuditID: a1, Tabel: "panen_header", BarisID: id, Aksi: "BUAT", Waktu: "2026-11-10T01:00:00Z", Username: "orangLain"},
		{AuditID: a2, Tabel: "panen_header", BarisID: id, Aksi: "SETUJU", Waktu: "2026-11-10T03:00:00Z"},
	}
	kode, b = e.kirim("POST", "/api/audit/batch", kr.AccessToken, batch, nil)
	if kode != 200 || !bytes.Contains(b, []byte(`"diterima":2`)) {
		t.Fatalf("audit: %d %s", kode, b)
	}
	kode, b = e.kirim("POST", "/api/audit/batch", kr.AccessToken, batch, nil)
	if kode != 200 || !bytes.Contains(b, []byte(`"dilewati":2`)) {
		t.Fatalf("audit ulang: %d %s", kode, b)
	}
	if hitung(t, e.db, `SELECT COUNT(*) FROM dbo.audit_log WHERE audit_id=@p1 AND user_id=@p2`, a1, kr.Profil.UserID) != 1 {
		t.Fatal("user_id audit harus dari token")
	}
	bad := []model.AuditInput{{AuditID: "bukan-uuid", Tabel: "x", BarisID: "y", Aksi: "z", Waktu: "2026-11-10T01:00:00Z"}}
	if kode, _ := e.kirim("POST", "/api/audit/batch", kr.AccessToken, bad, nil); kode != 422 {
		t.Fatalf("audit tak sah harus 422, dapat %d", kode)
	}
}

func contains(s []string, x string) bool {
	for _, v := range s {
		if v == x {
			return true
		}
	}
	return false
}

// Hak role ikut diunduh HP, dan perubahan hak role mengubah versi master (supaya HP mengunduh ulang).
func TestMasterHakRole(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)
	ambil := func() (string, map[string][]string) {
		kode, b := e.kirim("GET", "/api/master", kr.AccessToken, nil, nil)
		if kode != 200 {
			t.Fatalf("master: %d %s", kode, b)
		}
		var m struct {
			Versi          string `json:"versi"`
			RolePermission []struct {
				Role       string   `json:"role"`
				Permission []string `json:"permission"`
			} `json:"role_permission"`
		}
		if err := json.Unmarshal(b, &m); err != nil {
			t.Fatal(err)
		}
		hak := map[string][]string{}
		for _, r := range m.RolePermission {
			hak[r.Role] = r.Permission
		}
		return m.Versi, hak
	}
	punya := func(hak map[string][]string, role, izin string) bool {
		for _, p := range hak[role] {
			if p == izin {
				return true
			}
		}
		return false
	}

	versi0, hak := ambil()
	if !punya(hak, "KRANI", "panen.kirim") || punya(hak, "KRANI", "panen.setujui") || !punya(hak, "MANDOR", "panen.setujui") || !punya(hak, "ADMIN", "master.kelola") {
		t.Fatalf("hak role tidak sesuai data awal: %v", hak)
	}

	// Mencabut satu hak (hapus lunak, mengisi diubah_pada seperti aturan semua alat) harus mengubah versi dan isi.
	const kondisi = `role_id=(SELECT role_id FROM dbo.role WHERE kode='KRANI') AND permission_id=(SELECT permission_id FROM dbo.permission WHERE kode='grading.ubah')`
	t.Cleanup(func() {
		_, _ = e.db.Exec(`UPDATE dbo.role_permission SET dihapus_pada=NULL, diubah_pada=SYSUTCDATETIME() WHERE ` + kondisi)
	})
	if _, err := e.db.Exec(`UPDATE dbo.role_permission SET dihapus_pada=SYSUTCDATETIME(), diubah_pada=SYSUTCDATETIME() WHERE ` + kondisi); err != nil {
		t.Fatal(err)
	}
	versi1, hak1 := ambil()
	if versi1 == versi0 {
		t.Fatal("versi master harus berubah setelah hak role berubah")
	}
	if punya(hak1, "KRANI", "grading.ubah") {
		t.Fatalf("hak yang dicabut tidak boleh ikut terkirim: %v", hak1["KRANI"])
	}

	// Dikembalikan: isi kembali seperti semula.
	if _, err := e.db.Exec(`UPDATE dbo.role_permission SET dihapus_pada=NULL, diubah_pada=SYSUTCDATETIME() WHERE ` + kondisi); err != nil {
		t.Fatal(err)
	}
	_, hak2 := ambil()
	if !punya(hak2, "KRANI", "grading.ubah") {
		t.Fatalf("hak yang dipulihkan harus kembali: %v", hak2["KRANI"])
	}
}

func TestMasterUnit(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)

	ambil := func(versi string) map[string]any {
		path := "/api/master"
		if versi != "" {
			path += "?versi=" + versi
		}
		kode, b := e.kirim("GET", path, kr.AccessToken, nil, nil)
		if kode != 200 {
			t.Fatalf("master: %d %s", kode, b)
		}
		var m map[string]any
		if err := json.Unmarshal(b, &m); err != nil {
			t.Fatal(err)
		}
		return m
	}
	jumlah := func(m map[string]any, k string) int {
		a, _ := m[k].([]any)
		return len(a)
	}

	// Tanpa token ditolak.
	if kode, _ := e.kirim("GET", "/api/master", "", nil, nil); kode != 401 {
		t.Fatalf("tanpa token harus 401, dapat %d", kode)
	}

	m := ambil("")
	versi, _ := m["versi"].(string)
	if m["berubah"] != true || versi == "" {
		t.Fatalf("unduhan pertama: %v", m)
	}
	if jumlah(m, "blok") != 2 || jumlah(m, "tph") != 5 || jumlah(m, "pemanen") != 5 || jumlah(m, "divisi") != 1 {
		t.Fatalf("isi master tidak sesuai seed: blok=%d tph=%d pemanen=%d", jumlah(m, "blok"), jumlah(m, "tph"), jumlah(m, "pemanen"))
	}
	unit, _ := m["unit"].(map[string]any)
	if unit["kode"] != "KP_STL" {
		t.Fatalf("unit: %v", unit)
	}
	tph0 := m["tph"].([]any)[0].(map[string]any)
	if tph0["qr_kode"] != "FAI26|TPH|1|KP_STL|A12|01" || tph0["aktif"] != true {
		t.Fatalf("tph: %v", tph0)
	}
	// Akun dikirim dengan role, tanpa hash password.
	var kraniAda bool
	for _, u := range m["user"].([]any) {
		um := u.(map[string]any)
		if _, bocor := um["password_hash"]; bocor {
			t.Fatal("hash password tidak boleh dikirim")
		}
		if um["username"] == "krani1" {
			kraniAda = true
			if r, _ := um["role"].([]any); len(r) != 1 || r[0] != "KRANI" {
				t.Fatalf("role krani1: %v", um["role"])
			}
		}
	}
	if !kraniAda {
		t.Fatal("krani1 tidak ada di master")
	}

	// Versi sama: hanya versi.
	m2 := ambil(versi)
	if m2["berubah"] != false || m2["versi"] != versi || m2["blok"] != nil {
		t.Fatalf("versi sama: %v", m2)
	}

	// Perubahan master (koordinat TPH) mengubah versi; baris terhapus lunak tetap dikirim.
	if _, err := e.db.Exec(`UPDATE dbo.tph SET lat=-2.0, lon=113.0, diubah_pada=SYSUTCDATETIME() WHERE tph_id=1`); err != nil {
		t.Fatal(err)
	}
	m3 := ambil(versi)
	versi2, _ := m3["versi"].(string)
	if m3["berubah"] != true || versi2 == versi {
		t.Fatalf("versi seharusnya berubah: %v -> %v", versi, versi2)
	}
	if lat := m3["tph"].([]any)[0].(map[string]any)["lat"]; lat != -2.0 {
		t.Fatalf("lat: %v", lat)
	}
	if _, err := e.db.Exec(`UPDATE dbo.pemanen SET aktif=0, dihapus_pada=SYSUTCDATETIME(), diubah_pada=SYSUTCDATETIME() WHERE pemanen_id=5`); err != nil {
		t.Fatal(err)
	}
	m4 := ambil(versi2)
	if jumlah(m4, "pemanen") != 5 {
		t.Fatalf("baris terhapus lunak tetap dikirim, dapat %d", jumlah(m4, "pemanen"))
	}
	p5 := m4["pemanen"].([]any)[4].(map[string]any)
	if p5["dihapus_pada"] == nil {
		t.Fatalf("dihapus_pada harus terisi: %v", p5)
	}

	// Pulihkan data uji.
	_, _ = e.db.Exec(`UPDATE dbo.tph SET lat=NULL, lon=NULL, diubah_pada=NULL WHERE tph_id=1`)
	_, _ = e.db.Exec(`UPDATE dbo.pemanen SET aktif=1, dihapus_pada=NULL, diubah_pada=NULL WHERE pemanen_id=5`)
}
