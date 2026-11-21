package service

import (
	"context"
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
	d, err := r.svc.Detail(context.Background(), tanpaHak, id)
	if err != nil || d.Jejak != nil {
		t.Fatalf("tanpa ipd.jejak harus null: %v %+v", err, d)
	}
	denganHak := &auth.Klaim{UserID: 50, UnitID: 1}
	d, err = r.svc.Detail(context.Background(), denganHak, id)
	if err != nil || d.Jejak == nil || len(d.Jejak.Titik) != 2 || d.Jejak.DurasiMenit != 4 || d.Jejak.JarakM < 50 {
		t.Fatalf("dengan ipd.jejak: %v %+v", err, d.Jejak)
	}
	// Tidak ada jalur terkirim: tetap null walau berhak.
	r.repo.jejak = nil
	d, _ = r.svc.Detail(context.Background(), denganHak, id)
	if d.Jejak != nil {
		t.Fatalf("tanpa titik harus null: %+v", d.Jejak)
	}
}
