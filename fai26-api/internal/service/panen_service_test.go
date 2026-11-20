package service

import (
	"context"
	"net/http"
	"strings"
	"testing"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

const idPanen = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

func panenSah() model.PanenInput {
	return model.PanenInput{
		PanenID: idPanen, UnitKode: "KP_STL", TanggalPanen: "2026-11-10", Asm3Oid: 101, BlokKode: "A12", TphKode: "01",
		StatusLokasi: "DI_TPH", DibuatPada: "2026-11-10T01:00:00Z",
		DisetujuiUsername: "mandor1", DisetujuiPada: "2026-11-10T03:00:00Z",
		Grading: []model.GradingInput{{KelasKode: "ripe", JmlAI: 7, JmlFinal: 9}, {KelasKode: "unripe", JmlFinal: 1}},
		Pemanen: []model.PemanenInput{{HrmbCode: 5001, JjgAlokasi: 10}},
	}
}

var klaimMandor = &auth.Klaim{UserID: 20, UnitID: 1, DeviceID: "22222222-2222-4222-8222-222222222222"}

type rakitanPanen struct {
	svc   *PanenService
	panen *fakePanen
	users *fakeUsers
}

func rakitPanen() rakitanPanen {
	users := &fakeUsers{
		ids:   map[string]int64{"mandor1": 20, "krani1": 10},
		perms: map[int64][]string{20: {"panen.setujui", "panen.koreksi"}, 10: {"panen.kirim"}},
		aktif: map[int64]bool{10: true, 20: true},
		user:  &model.User{Username: "uji"},
	}
	panen := newFakePanen()
	svc := NewPanenService(panen, &fakeUnits{ids: map[string]int64{"KP_STL": 1}}, users, NewAksesService(users))
	return rakitanPanen{svc: svc, panen: panen, users: users}
}

var klaimKrani = &auth.Klaim{UserID: 10, UnitID: 1, DeviceID: "11111111-1111-4111-8111-111111111111"}

func statusHTTP(err error) int {
	if err == nil {
		return 0
	}
	return apperror.Dari(err).Status
}

func TestTerimaPanenMenyimpanDenganIdentitasDariToken(t *testing.T) {
	r := rakitPanen()
	status, err := r.svc.Terima(context.Background(), klaimKrani, panenSah(), "1.2.3.4")
	if err != nil || status != StatusDiterima {
		t.Fatalf("status=%q err=%v", status, err)
	}
	p := r.panen.tersimpan[idPanen]
	if p.UserID != 10 || p.DeviceID != klaimKrani.DeviceID || p.UnitID != 1 || p.PenyetujuID != 20 || p.IP != "1.2.3.4" {
		t.Fatalf("identitas tidak dari token/server: %+v", p)
	}
	if p.DisetujuiPada.IsZero() || p.DibuatPada.Hour() != 1 {
		t.Fatalf("waktu salah: %+v", p)
	}
}

func TestTerimaPanenIdempoten(t *testing.T) {
	r := rakitPanen()
	if _, err := r.svc.Terima(context.Background(), klaimKrani, panenSah(), ""); err != nil {
		t.Fatal(err)
	}
	// UUID huruf besar tetap dianggap sama.
	in := panenSah()
	in.PanenID = strings.ToUpper(idPanen)
	status, err := r.svc.Terima(context.Background(), klaimKrani, in, "")
	if err != nil || status != StatusSudahAda {
		t.Fatalf("kirim ulang: status=%q err=%v", status, err)
	}
	if len(r.panen.tersimpan) != 1 {
		t.Fatal("catatan dobel")
	}
}

func TestTerimaPanenAturanBisnis(t *testing.T) {
	kasus := []struct {
		nama string
		ubah func(*model.PanenInput)
		kode int
	}{
		{"penyetuju tanpa waktu", func(p *model.PanenInput) { p.DisetujuiPada = "" }, 422},
		{"tph kosong", func(p *model.PanenInput) { p.TphKode = "" }, 422},
		{"penyetuju krani tidak berhak", func(p *model.PanenInput) { p.DisetujuiUsername = "krani1" }, 422},
		{"penyetuju tak dikenal", func(p *model.PanenInput) { p.DisetujuiUsername = "hantu" }, 422},
		{"unit tak dikenal", func(p *model.PanenInput) { p.UnitKode = "XYZ" }, 422},
		{"status lokasi aneh", func(p *model.PanenInput) { p.StatusLokasi = "DI_BULAN" }, 422},
		{"kelas aneh", func(p *model.PanenInput) { p.Grading[0].KelasKode = "pisang" }, 422},
		{"kelas ganda", func(p *model.PanenInput) { p.Grading[1].KelasKode = "ripe" }, 422},
		{"jumlah negatif", func(p *model.PanenInput) { p.Grading[0].JmlFinal = -1 }, 422},
		{"total nol", func(p *model.PanenInput) { p.Grading[0].JmlFinal = 0; p.Grading[1].JmlFinal = 0 }, 422},
		{"tanggal buruk", func(p *model.PanenInput) { p.TanggalPanen = "10/11/2026" }, 422},
		{"uuid buruk", func(p *model.PanenInput) { p.PanenID = "123" }, 422},
		{"waktu setuju buruk", func(p *model.PanenInput) { p.DisetujuiPada = "kemarin" }, 422},
	}
	for _, k := range kasus {
		t.Run(k.nama, func(t *testing.T) {
			r := rakitPanen()
			in := panenSah()
			k.ubah(&in)
			_, err := r.svc.Terima(context.Background(), klaimKrani, in, "")
			if statusHTTP(err) != k.kode {
				t.Fatalf("harapan %d, dapat %v", k.kode, err)
			}
			if len(r.panen.tersimpan) != 0 {
				t.Fatal("catatan tidak sah ikut tersimpan")
			}
		})
	}
}

func TestTerimaPanenUnitBerbedaDenganAkun(t *testing.T) {
	r := rakitPanen()
	k := &auth.Klaim{UserID: 10, UnitID: 2, DeviceID: klaimKrani.DeviceID}
	_, err := r.svc.Terima(context.Background(), k, panenSah(), "")
	if statusHTTP(err) != http.StatusForbidden {
		t.Fatalf("harapan 403, dapat %v", err)
	}
}

func TestGalatDatabaseTidakBocorKeKlien(t *testing.T) {
	r := rakitPanen()
	r.panen.errSimpan = errAneh
	_, err := r.svc.Terima(context.Background(), klaimKrani, panenSah(), "")
	ae := apperror.Dari(err)
	if ae.Status != 500 || strings.Contains(ae.Message, "rahasia") || strings.Contains(ae.Message, "fai_panen") {
		t.Fatalf("pesan ke klien membocorkan detail teknis: %+v", ae)
	}
	if ae.Err == nil {
		t.Fatal("sebab teknis harus tetap tersimpan untuk log")
	}
}

func TestPencatatOrangLain(t *testing.T) {
	kasus := []struct {
		nama     string
		pengirim *auth.Klaim
		pencatat string
		kode     int
		wantID   int64
	}{
		{"kosong berarti pengirim", klaimKrani, "", 0, 10},
		{"pencatat sama dengan pengirim", klaimKrani, "krani1", 0, 10},
		{"krani mengirim milik orang lain ditolak", klaimKrani, "mandor1", 403, 0},
		{"mandor mengirim atas nama krani", &auth.Klaim{UserID: 20, UnitID: 1, DeviceID: klaimKrani.DeviceID}, "krani1", 0, 10},
		{"pencatat tak dikenal", &auth.Klaim{UserID: 20, UnitID: 1, DeviceID: klaimKrani.DeviceID}, "hantu", 422, 0},
	}
	for _, k := range kasus {
		t.Run(k.nama, func(t *testing.T) {
			r := rakitPanen()
			in := panenSah()
			in.PencatatUsername = k.pencatat
			_, err := r.svc.Terima(context.Background(), k.pengirim, in, "")
			if statusHTTP(err) != k.kode {
				t.Fatalf("harapan %d, dapat %v", k.kode, err)
			}
			if k.kode != 0 {
				if len(r.panen.tersimpan) != 0 {
					t.Fatal("tidak boleh tersimpan")
				}
				return
			}
			p := r.panen.tersimpan[idPanen]
			if p.PencatatID != k.wantID || p.UserID != k.pengirim.UserID {
				t.Fatalf("pencatat=%d pengirim=%d, harapan pencatat %d", p.PencatatID, p.UserID, k.wantID)
			}
		})
	}
}

func TestMandorMenginputMenyetujuiSendiri(t *testing.T) {
	r := rakitPanen()
	in := panenSah()
	in.DisetujuiUsername, in.DisetujuiPada = "", ""
	status, err := r.svc.Terima(context.Background(), klaimMandor, in, "")
	if err != nil || status != StatusDiterima {
		t.Fatalf("status=%q err=%v", status, err)
	}
	p := r.panen.tersimpan[idPanen]
	if p.PenyetujuID != 20 || p.PencatatID != 20 || p.DisetujuiPada.IsZero() {
		t.Fatalf("mandor harus menjadi pencatat dan penyetuju: %+v", p)
	}
}

func TestMandorDanAfdelingDariAkunBukanDariIsiKiriman(t *testing.T) {
	r := rakitPanen()
	mandor, afd := int64(7001), "DIV2"
	r.users.profil = map[int64]model.Profil{20: {UserID: 20, MandorHrmbCode: &mandor, AfdelingKode: &afd}}
	r.panen.master = &model.MasterPanen{BlokAda: true, TphAda: true, PemanenAda: true, PemanenMandor: &mandor}
	in := panenSah()
	in.DisetujuiUsername, in.DisetujuiPada = "", ""
	if _, err := r.svc.Terima(context.Background(), klaimMandor, in, ""); err != nil {
		t.Fatal(err)
	}
	p := r.panen.tersimpan[idPanen]
	if p.MandorHrmbCode == nil || *p.MandorHrmbCode != 7001 || p.AfdelingKode != "DIV2" {
		t.Fatalf("mandor/afdeling harus dari akun: %+v %q", p.MandorHrmbCode, p.AfdelingKode)
	}
}

func TestPemanenBukanMilikMandorDitolak(t *testing.T) {
	r := rakitPanen()
	mandor, lain := int64(7001), int64(7002)
	r.users.profil = map[int64]model.Profil{20: {UserID: 20, MandorHrmbCode: &mandor}}
	r.panen.master = &model.MasterPanen{BlokAda: true, TphAda: true, PemanenAda: true, PemanenMandor: &lain}
	in := panenSah()
	in.DisetujuiUsername, in.DisetujuiPada = "", ""
	_, err := r.svc.Terima(context.Background(), klaimMandor, in, "")
	if statusHTTP(err) != 422 || !strings.Contains(err.Error(), "bukan pemanen milik mandor") {
		t.Fatalf("harapan 422 pemanen bukan milik mandor, dapat %v", err)
	}
	// pemanen tanpa mandor di master juga bukan miliknya
	r.panen.master = &model.MasterPanen{BlokAda: true, TphAda: true, PemanenAda: true}
	if _, err := r.svc.Terima(context.Background(), klaimMandor, in, ""); statusHTTP(err) != 422 {
		t.Fatalf("pemanen tanpa mandor harus ditolak untuk akun mandor: %v", err)
	}
}

func TestMasterTidakLengkapDitolak(t *testing.T) {
	kasus := map[string]model.MasterPanen{
		"blok":    {TphAda: true, PemanenAda: true},
		"tph":     {BlokAda: true, PemanenAda: true},
		"pemanen": {BlokAda: true, TphAda: true},
	}
	for nama, m := range kasus {
		r := rakitPanen()
		mm := m
		r.panen.master = &mm
		_, err := r.svc.Terima(context.Background(), klaimKrani, panenSah(), "")
		if statusHTTP(err) != 422 || !strings.Contains(strings.ToLower(err.Error()), nama) {
			t.Errorf("%s: harapan 422 yang menyebut %s, dapat %v", nama, nama, err)
		}
	}
}

func TestKombinasiGandaDijawab409(t *testing.T) {
	r := rakitPanen()
	r.panen.dobel = true
	_, err := r.svc.Terima(context.Background(), klaimKrani, panenSah(), "")
	if statusHTTP(err) != 409 {
		t.Fatalf("harapan 409, dapat %v", err)
	}
	if len(r.panen.tersimpan) != 0 {
		t.Fatal("kombinasi ganda tidak boleh tersimpan")
	}
}

func TestKirimUlangCatatanDitolak(t *testing.T) {
	r := rakitPanen()
	if _, err := r.svc.Terima(context.Background(), klaimKrani, panenSah(), ""); err != nil {
		t.Fatal(err)
	}
	r.panen.status[idPanen] = "DITOLAK"
	// Percobaan ulang biasa (tanpa kirim_ulang) tidak boleh menghidupkan catatan yang ditolak.
	if st, err := r.svc.Terima(context.Background(), klaimKrani, panenSah(), ""); err != nil || st != StatusSudahAda {
		t.Fatalf("tanpa kirim_ulang: %q %v", st, err)
	}
	if r.panen.status[idPanen] != "DITOLAK" {
		t.Fatal("status berubah tanpa kirim_ulang")
	}
	// Kirim ulang sengaja: isi diganti dan status kembali BARU.
	in := panenSah()
	in.KirimUlang = true
	in.BrondolanKg = 3
	st, err := r.svc.Terima(context.Background(), klaimKrani, in, "")
	if err != nil || st != StatusDiterima {
		t.Fatalf("kirim ulang: %q %v", st, err)
	}
	if !r.panen.tersimpan[idPanen].Ganti || r.panen.tersimpan[idPanen].Input.BrondolanKg != 3 || r.panen.status[idPanen] != "BARU" {
		t.Fatalf("catatan harus diganti dan BARU: %+v", r.panen.tersimpan[idPanen])
	}
	// Catatan yang sudah disetujui (BARU) terkunci: kirim_ulang=true dijawab 423, isi tidak berubah.
	in.BrondolanKg = 9
	if _, err := r.svc.Terima(context.Background(), klaimKrani, in, ""); statusHTTP(err) != http.StatusLocked {
		t.Fatalf("catatan BARU harus terkunci (423): %v", err)
	}
	if r.panen.tersimpan[idPanen].Input.BrondolanKg != 3 {
		t.Fatal("isi catatan terkunci berubah")
	}
	in.BrondolanKg = 3
	// Orang lain tanpa hak koreksi tidak boleh mengirim ulang.
	r.panen.status[idPanen] = "DITOLAK"
	lain := &auth.Klaim{UserID: 11, UnitID: 1, DeviceID: klaimKrani.DeviceID}
	r.users.perms[11] = []string{"panen.kirim"}
	r.users.aktif[11] = true
	if _, err := r.svc.Terima(context.Background(), lain, in, ""); statusHTTP(err) != http.StatusForbidden {
		t.Fatalf("harapan 403, dapat %v", err)
	}
}

// Mandor boleh mengubah catatan yang sudah terkirim selama krani belum menyetujui; setelah itu terkunci.
func TestUbahCatatanMenungguSebelumDisetujui(t *testing.T) {
	r := rakitPanen()
	in := panenSah()
	in.DisetujuiUsername, in.DisetujuiPada = "", ""
	if _, err := r.svc.Terima(context.Background(), klaimKrani, in, ""); err != nil {
		t.Fatal(err)
	}
	if r.panen.status[idPanen] != "MENUNGGU" {
		t.Fatalf("status awal %s", r.panen.status[idPanen])
	}
	// Tanpa kirim_ulang (percobaan ulang jaringan): tidak mengubah apa pun.
	in.BrondolanKg = 5
	if st, err := r.svc.Terima(context.Background(), klaimKrani, in, ""); err != nil || st != StatusSudahAda {
		t.Fatalf("tanpa kirim_ulang: %q %v", st, err)
	}
	// Dengan kirim_ulang: isi diganti, tetap MENUNGGU.
	in.KirimUlang = true
	if st, err := r.svc.Terima(context.Background(), klaimKrani, in, ""); err != nil || st != StatusDiterima {
		t.Fatalf("ubah saat MENUNGGU: %q %v", st, err)
	}
	if p := r.panen.tersimpan[idPanen]; !p.Ganti || p.Input.BrondolanKg != 5 || !p.Menunggu || r.panen.status[idPanen] != "MENUNGGU" {
		t.Fatalf("isi harus diganti dan tetap MENUNGGU: %+v status=%s", p, r.panen.status[idPanen])
	}
	// Krani menyetujui: catatan terkunci.
	r.panen.status[idPanen] = "BARU"
	in.BrondolanKg = 8
	_, err := r.svc.Terima(context.Background(), klaimKrani, in, "")
	if statusHTTP(err) != http.StatusLocked || !strings.Contains(err.Error(), "disetujui krani") {
		t.Fatalf("harapan 423 'disetujui krani': %v", err)
	}
	if r.panen.tersimpan[idPanen].Input.BrondolanKg != 5 {
		t.Fatal("isi catatan terkunci berubah")
	}
}

func TestPengirimTanpaHakSetujuDisimpanMenunggu(t *testing.T) {
	r := rakitPanen()
	in := panenSah()
	in.DisetujuiUsername, in.DisetujuiPada = "", ""
	status, err := r.svc.Terima(context.Background(), klaimKrani, in, "") // klaimKrani tidak memegang panen.setujui
	if err != nil || status != StatusDiterima {
		t.Fatalf("status=%q err=%v", status, err)
	}
	p := r.panen.tersimpan[idPanen]
	if !p.Menunggu || p.PenyetujuID != 0 || r.panen.status[idPanen] != "MENUNGGU" {
		t.Fatalf("harus MENUNGGU tanpa penyetuju: %+v status=%s", p, r.panen.status[idPanen])
	}
	// Mandor (pemegang panen.setujui) mengirim sendiri: langsung disetujui, tidak menunggu.
	r2 := rakitPanen()
	if _, err := r2.svc.Terima(context.Background(), klaimMandor, in, ""); err != nil || r2.panen.tersimpan[idPanen].Menunggu {
		t.Fatalf("pemegang hak setuju tidak boleh menunggu: %v", err)
	}
}

func TestSetujuiDanTolakCatatanMenunggu(t *testing.T) {
	r := rakitPanen()
	in := panenSah()
	in.DisetujuiUsername, in.DisetujuiPada = "", ""
	if _, err := r.svc.Terima(context.Background(), klaimKrani, in, ""); err != nil {
		t.Fatal(err)
	}
	if l, _ := r.svc.Menunggu(context.Background(), klaimMandor); len(l) != 1 {
		t.Fatalf("daftar menunggu: %d", len(l))
	}
	if err := r.svc.Setujui(context.Background(), klaimMandor, idPanen, ""); err != nil || r.panen.status[idPanen] != "BARU" {
		t.Fatalf("setujui: %v %s", err, r.panen.status[idPanen])
	}
	if err := r.svc.Setujui(context.Background(), klaimMandor, idPanen, ""); statusHTTP(err) != 409 {
		t.Fatalf("setujui dua kali harus 409, dapat %v", err)
	}
	if err := r.svc.Tolak(context.Background(), klaimMandor, idPanen, "salah blok", ""); statusHTTP(err) != 409 {
		t.Fatalf("tolak catatan yang sudah disetujui harus 409, dapat %v", err)
	}
	if err := r.svc.Setujui(context.Background(), klaimMandor, "bukan-uuid", ""); statusHTTP(err) != 422 {
		t.Fatalf("id buruk harus 422, dapat %v", err)
	}
	if err := r.svc.Setujui(context.Background(), klaimMandor, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", ""); statusHTTP(err) != 404 {
		t.Fatalf("tidak ada harus 404, dapat %v", err)
	}
	// Tolak: alasan wajib, lalu berhasil untuk catatan menunggu.
	in2 := panenSah()
	in2.PanenID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
	in2.TphKode = "02"
	in2.DisetujuiUsername, in2.DisetujuiPada = "", ""
	if _, err := r.svc.Terima(context.Background(), klaimKrani, in2, ""); err != nil {
		t.Fatal(err)
	}
	if err := r.svc.Tolak(context.Background(), klaimMandor, in2.PanenID, "  ", ""); statusHTTP(err) != 422 {
		t.Fatalf("alasan kosong harus 422, dapat %v", err)
	}
	if err := r.svc.Tolak(context.Background(), klaimMandor, in2.PanenID, "salah blok", ""); err != nil || r.panen.status[in2.PanenID] != "DITOLAK" {
		t.Fatalf("tolak: %v %s", err, r.panen.status[in2.PanenID])
	}
}

// Dashboard: krani (panen.setujui) yang memeriksa catatan mandor harus melihat se-unit, bukan hanya catatannya sendiri.
func TestRingkasanLingkupMengikutiHak(t *testing.T) {
	r := rakitPanen()
	r.users.ids["rony"] = 30
	r.users.perms[30] = []string{"panen.setujui"} // krani produksi: hanya memeriksa, tidak mengoreksi
	r.users.aktif[30] = true
	kasus := []struct {
		nama  string
		klaim *auth.Klaim
		semua bool
	}{
		{"krani tanpa hak setuju atau koreksi: miliknya sendiri", klaimKrani, false},
		{"mandor (koreksi): se-unit", klaimMandor, true},
		{"krani penyetuju: se-unit", &auth.Klaim{UserID: 30, UnitID: 1}, true},
	}
	for _, k := range kasus {
		if _, err := r.svc.Ringkasan(context.Background(), k.klaim, "2026-11-10"); err != nil {
			t.Fatalf("%s: %v", k.nama, err)
		}
		if r.panen.semuaTerakhir != k.semua {
			t.Fatalf("%s: semua=%v, harapan %v", k.nama, r.panen.semuaTerakhir, k.semua)
		}
	}
}
