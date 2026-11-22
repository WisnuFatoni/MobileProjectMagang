package service

import (
	"context"
	"errors"
	"math"
	"strings"
	"testing"
	"time"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

func titikUji(menit int, lat, lon float64) model.IpdTitikOut {
	ak := 6.0
	return model.IpdTitikOut{T: time.Date(2026, 11, 20, 3, menit, 0, 0, time.UTC), Lat: lat, Lon: lon, Akurasi: &ak}
}

func TestRingkasJejakMenghitungJarakDanDurasi(t *testing.T) {
	// 0,001 derajat lintang = sekitar 111,2 m; dua langkah ke utara = sekitar 222,4 m. Dari menit 0 sampai 10.
	j, d := RingkasJejak([]model.IpdTitikOut{titikUji(0, -2.7, 104.3), titikUji(5, -2.699, 104.3), titikUji(10, -2.698, 104.3)})
	if math.Abs(j-222.4) > 1.0 {
		t.Fatalf("jarak: %.1f m", j)
	}
	if d != 10 {
		t.Fatalf("durasi: %.1f menit", d)
	}
	if j, d := RingkasJejak(nil); j != 0 || d != 0 {
		t.Fatalf("kosong: %v %v", j, d)
	}
	if j, d := RingkasJejak([]model.IpdTitikOut{titikUji(0, -2.7, 104.3)}); j != 0 || d != 0 {
		t.Fatalf("satu titik: %v %v", j, d)
	}
}

func masukanJejak(titik ...model.IpdTitikInput) model.IpdInput {
	in := ipdSah()
	in.Jejak = titik
	return in
}

func TestValidasiJejakMenolakTitikTidakSah(t *testing.T) {
	ak := 7.5
	sah := model.IpdTitikInput{T: "2026-11-20T03:00:00Z", Lat: -2.7, Lon: 104.3, Akurasi: &ak}
	in := masukanJejak(sah)
	if err := in.Validasi(); err != nil {
		t.Fatalf("titik sah harus lolos: %v", err)
	}
	salah := map[string]model.IpdTitikInput{
		"lintang di luar": {T: sah.T, Lat: 91, Lon: 104.3},
		"bujur di luar":   {T: sah.T, Lat: -2.7, Lon: 181},
		"nol-nol":         {T: sah.T, Lat: 0, Lon: 0},
		"waktu salah":     {T: "kemarin", Lat: -2.7, Lon: 104.3},
		"waktu kosong":    {T: "", Lat: -2.7, Lon: 104.3},
		"akurasi negatif": {T: sah.T, Lat: -2.7, Lon: 104.3, Akurasi: func() *float64 { v := -1.0; return &v }()},
	}
	for nama, titik := range salah {
		in := masukanJejak(titik)
		if err := in.Validasi(); err == nil || !strings.Contains(err.Error(), "jejak titik 1") {
			t.Errorf("%s harus ditolak: %v", nama, err)
		}
	}
	banyak := ipdSah()
	banyak.Jejak = make([]model.IpdTitikInput, model.MaksTitikJejak+1)
	if err := banyak.Validasi(); err == nil || !strings.Contains(err.Error(), "maksimal") {
		t.Fatalf("terlalu banyak titik harus ditolak: %v", err)
	}
	// Waktu pokok yang rusak ditolak, yang kosong boleh (HP lama).
	pk := ipdSah()
	pk.Pokok[0].DicatatPada = "bukan waktu"
	if err := pk.Validasi(); err == nil || !strings.Contains(err.Error(), "dicatat_pada") {
		t.Fatalf("dicatat_pada rusak harus ditolak: %v", err)
	}
	tanpa := ipdSah()
	tanpa.Pokok[0].DicatatPada = ""
	if err := tanpa.Validasi(); err != nil {
		t.Fatalf("tanpa dicatat_pada harus boleh: %v", err)
	}
}

func TestDetailHanyaMemuatJejakUntukPemegangHak(t *testing.T) {
	r := rakitIpd()
	id := "bbbb0001-0000-4000-8000-000000000001"
	r.repo.baris = &model.IpdBaris{ID: id, Status: "DISETUJUI"}
	r.repo.jejak = []model.IpdTitikOut{titikUji(0, -2.7, 104.3), titikUji(4, -2.6995, 104.3)}
	r.users.perms[50] = []string{"ipd.lihat", "ipd.jejak"}
	r.users.aktif[50] = true

	tanpaHak := &auth.Klaim{UserID: 20, UnitID: 1} // mandor: ipd.lihat saja
	d, err := r.svc.Detail(context.Background(), tanpaHak, id, "10.0.0.5")
	if err != nil || d.Jejak != nil {
		t.Fatalf("tanpa ipd.jejak harus null: %v %+v", err, d)
	}
	denganHak := &auth.Klaim{UserID: 50, UnitID: 1}
	d, err = r.svc.Detail(context.Background(), denganHak, id, "10.0.0.5")
	if err != nil || d.Jejak == nil || len(d.Jejak.Titik) != 2 || d.Jejak.DurasiMenit != 4 || d.Jejak.JarakM < 50 {
		t.Fatalf("dengan ipd.jejak: %v %+v", err, d.Jejak)
	}
	// Tidak ada jalur terkirim: tetap null walau berhak.
	r.repo.jejak = nil
	d, _ = r.svc.Detail(context.Background(), denganHak, id, "10.0.0.5")
	if d.Jejak != nil {
		t.Fatalf("tanpa titik harus null: %+v", d.Jejak)
	}
}

func TestPembukaanJalurDicatatAuditHanyaBilaJalurDikirim(t *testing.T) {
	r := rakitIpd()
	id := "bbbb0001-0000-4000-8000-000000000001"
	r.repo.baris = &model.IpdBaris{ID: id, Status: "DISETUJUI"}
	r.repo.jejak = []model.IpdTitikOut{titikUji(0, -2.7, 104.3), titikUji(4, -2.6995, 104.3)}
	r.users.perms[50] = []string{"ipd.lihat", "ipd.jejak"}
	r.users.aktif[50] = true
	ctx := context.Background()

	// Tanpa hak: jalur tidak dikirim, jadi tidak ada yang perlu dicatat.
	if _, err := r.svc.Detail(ctx, &auth.Klaim{UserID: 20, UnitID: 1}, id, "10.0.0.5"); err != nil || len(r.repo.dilihat) != 0 {
		t.Fatalf("tanpa hak tidak boleh tercatat: %v %+v", err, r.repo.dilihat)
	}
	// Berhak: tercatat dengan pengguna, perangkat, IP, inspeksi, dan jumlah titik.
	if _, err := r.svc.Detail(ctx, &auth.Klaim{UserID: 50, UnitID: 1, DeviceID: "dev-web"}, id, "10.0.0.7"); err != nil {
		t.Fatal(err)
	}
	want := catatanLihat{uid: 50, dev: "dev-web", ip: "10.0.0.7", id: id, titik: 2}
	if len(r.repo.dilihat) != 1 || r.repo.dilihat[0] != want {
		t.Fatalf("audit lihat jalur: %+v", r.repo.dilihat)
	}
	// Berhak tetapi tidak ada jalur: tidak ada data lokasi yang dibuka, tidak dicatat.
	r.repo.jejak = nil
	if _, err := r.svc.Detail(ctx, &auth.Klaim{UserID: 50, UnitID: 1}, id, "10.0.0.7"); err != nil || len(r.repo.dilihat) != 1 {
		t.Fatalf("tanpa jalur tidak boleh tercatat: %v %+v", err, r.repo.dilihat)
	}
}

func TestJalurTidakDikirimBilaPencatatanAuditGagal(t *testing.T) {
	r := rakitIpd()
	id := "bbbb0001-0000-4000-8000-000000000001"
	r.repo.baris = &model.IpdBaris{ID: id, Status: "DISETUJUI"}
	r.repo.jejak = []model.IpdTitikOut{titikUji(0, -2.7, 104.3), titikUji(4, -2.6995, 104.3)}
	r.users.perms[50] = []string{"ipd.lihat", "ipd.jejak"}
	r.users.aktif[50] = true
	r.repo.lihatErr = errors.New("db mati")
	d, err := r.svc.Detail(context.Background(), &auth.Klaim{UserID: 50, UnitID: 1}, id, "10.0.0.7")
	if err == nil || d != nil {
		t.Fatalf("gagal mencatat harus menggagalkan permintaan, bukan membocorkan jalur: %v %+v", err, d)
	}
}

func TestBersihkanJejakMemakaiBatasHariDanMatiBilaNol(t *testing.T) {
	r := rakitIpd()
	r.svc.sekarang = func() time.Time { return time.Date(2026, 11, 22, 10, 0, 0, 0, time.UTC) }
	ctx := context.Background()

	// 0 hari = kebijakan tidak aktif: repo tidak disentuh.
	if h, err := r.svc.BersihkanJejak(ctx, 0); err != nil || h.Titik != 0 || r.repo.batasBersih != "" {
		t.Fatalf("0 hari harus tidak menghapus: %v %+v batas=%q", err, h, r.repo.batasBersih)
	}
	r.repo.hasilBersih = model.HasilBersihJejak{Inspeksi: 3, Titik: 120}
	h, err := r.svc.BersihkanJejak(ctx, 90)
	if err != nil || h.Inspeksi != 3 || h.Titik != 120 {
		t.Fatalf("hasil: %v %+v", err, h)
	}
	if r.repo.batasBersih != "2026-08-24" { // 90 hari sebelum 22 Nov 2026
		t.Fatalf("batas tanggal salah: %q", r.repo.batasBersih)
	}
	r.repo.bersihErr = errors.New("db mati")
	if _, err := r.svc.BersihkanJejak(ctx, 90); err == nil {
		t.Fatal("galat repo harus dikembalikan")
	}
}
