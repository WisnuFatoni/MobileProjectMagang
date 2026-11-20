package service

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"strings"
	"testing"
	"time"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

const idIpd = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

// fakeIpd adalah IpdRepo di memori untuk tes service.
type fakeIpd struct {
	status     map[string]string
	pemilik    map[string]int64
	tersimpan  map[string]model.IpdSimpan
	master     model.IpdMaster
	kode       map[string]int64
	mandor     map[string]model.IpdOrang
	setujuErr  error
	terakhir   [3]string // dari, sampai, kecuali pada panggilan Terakhir
	foto       map[string]bool
	fotoSimpan []model.IpdFoto
}

func newFakeIpd() *fakeIpd {
	asisten := int64(30)
	hrmb := int64(7001)
	return &fakeIpd{
		status: map[string]string{}, pemilik: map[string]int64{}, tersimpan: map[string]model.IpdSimpan{}, foto: map[string]bool{},
		master: model.IpdMaster{DivisiID: 1, DivisiAda: true, AsistenID: &asisten, BlokAda: true, BlokDalamDivisi: true, PemanenAda: true},
		kode:   map[string]int64{"A": 1, "S": 3, "B1": 8, "C": 10},
		mandor: map[string]model.IpdOrang{"mandor1": {UserID: 20, Hrmb: &hrmb}, "mandor2": {UserID: 21}},
	}
}

func (f *fakeIpd) Cari(_ context.Context, id string) (*model.IpdAda, error) {
	s, ok := f.status[id]
	if !ok {
		return nil, nil
	}
	return &model.IpdAda{Status: s, PemilikID: f.pemilik[id]}, nil
}
func (f *fakeIpd) CekMaster(context.Context, int64, string, int64, int64) (model.IpdMaster, error) {
	return f.master, nil
}
func (f *fakeIpd) KodeAktif(context.Context) (map[string]int64, error) { return f.kode, nil }
func (f *fakeIpd) KodeMaster(context.Context) ([]model.IpdKodeMaster, error) {
	return []model.IpdKodeMaster{{Kode: "A", Nama: "Potong buah mentah", Kelompok: "BUAH", Urut: 1},
		{Kode: "B1", Nama: "Brondolan tak dikutip", Kelompok: "BRONDOLAN", Urut: 8}}, nil
}
func (f *fakeIpd) Mandor(_ context.Context, _ int64, username string) (model.IpdOrang, error) {
	o, ok := f.mandor[username]
	if !ok {
		return o, repository.ErrNotFound
	}
	return o, nil
}
func (f *fakeIpd) Simpan(_ context.Context, p model.IpdSimpan) error {
	f.tersimpan[p.Input.IpdID] = p
	f.pemilik[p.Input.IpdID] = p.UserID
	if p.Menunggu {
		f.status[p.Input.IpdID] = "MENUNGGU"
	} else {
		f.status[p.Input.IpdID] = "DISETUJUI"
	}
	return nil
}
func (f *fakeIpd) DaftarStatus(context.Context, int64, int64, time.Time) ([]model.IpdStatus, error) {
	return nil, nil
}
func (f *fakeIpd) DaftarMenunggu(context.Context, int64, int64) ([]model.IpdBaris, error) {
	return nil, nil
}
func (f *fakeIpd) DaftarAdmin(context.Context, int64, model.FilterIpd) (*model.HalamanIpd, error) {
	return &model.HalamanIpd{}, nil
}
func (f *fakeIpd) Baris(context.Context, int64, string) (*model.IpdBaris, error) {
	return nil, repository.ErrNotFound
}
func (f *fakeIpd) Pokok(context.Context, string) ([]model.IpdPokokOut, error) { return nil, nil }
func (f *fakeIpd) Mentah(context.Context, []string) (map[string]*model.IpdMentah, error) {
	return map[string]*model.IpdMentah{}, nil
}
func (f *fakeIpd) Foto(context.Context, []string) (map[string][]model.IpdFotoRef, error) {
	return map[string][]model.IpdFotoRef{}, nil
}
func (f *fakeIpd) Setujui(_ context.Context, id string, _, _ int64, _, _ string) (string, error) {
	if f.setujuErr != nil {
		return "DISETUJUI", f.setujuErr
	}
	f.status[id] = "DISETUJUI"
	return "DISETUJUI", nil
}
func (f *fakeIpd) Tolak(_ context.Context, id string, _, _ int64, _, _, _ string) (string, error) {
	if f.setujuErr != nil {
		return "DISETUJUI", f.setujuErr
	}
	f.status[id] = "DITOLAK"
	return "DITOLAK", nil
}
func (f *fakeIpd) Terakhir(_ context.Context, _, _, _ int64, dari, sampai, kecuali string) ([]model.IpdTerakhir, error) {
	f.terakhir = [3]string{dari, sampai, kecuali}
	return []model.IpdTerakhir{{ID: idIpd, TanggalInspeksi: "2026-11-13", Inspektur: "mandor1", InspekturNama: "Mandor Satu", Status: "DISETUJUI"}}, nil
}
func (f *fakeIpd) DasarLoses(context.Context, int64, model.FilterLosesIpd) ([]model.IpdDasar, error) {
	return nil, nil
}
func (f *fakeIpd) FotoAda(_ context.Context, id string) (bool, error) { return f.foto[id], nil }
func (f *fakeIpd) SimpanFoto(_ context.Context, x model.IpdFoto) error {
	f.foto[x.ID] = true
	f.fotoSimpan = append(f.fotoSimpan, x)
	return nil
}
func (f *fakeIpd) PathFoto(context.Context, int64, string) (string, error) {
	return "", repository.ErrNotFound
}
func (f *fakeIpd) PemilikID(_ context.Context, id string) (int64, error) {
	p, ok := f.pemilik[id]
	if !ok {
		return 0, repository.ErrNotFound
	}
	return p, nil
}

type rakitanIpd struct {
	svc   *IpdService
	repo  *fakeIpd
	users *fakeUsers
	store *fakeStore
}

var (
	klaimInspektur = &auth.Klaim{UserID: 20, UnitID: 1, DeviceID: "33333333-3333-4333-8333-333333333333"}
	klaimAsisten   = &auth.Klaim{UserID: 30, UnitID: 1, DeviceID: "44444444-4444-4444-8444-444444444444"}
)

func rakitIpd() rakitanIpd {
	users := &fakeUsers{
		ids:   map[string]int64{"mandor1": 20, "asisten1": 30},
		perms: map[int64][]string{20: {"ipd.buat", "ipd.kirim", "ipd.lihat"}, 30: {"ipd.buat", "ipd.kirim", "ipd.setujui", "ipd.lihat"}, 40: {"ipd.kirim", "ipd.kelola"}},
		aktif: map[int64]bool{20: true, 30: true, 40: true},
		user:  &model.User{Username: "uji"},
	}
	repo := newFakeIpd()
	store := &fakeStore{}
	svc := NewIpdService(repo, &fakeUnits{ids: map[string]int64{"KP_STL": 1}}, NewAksesService(users), store)
	svc.sekarang = func() time.Time { return time.Date(2026, 11, 20, 3, 0, 0, 0, time.UTC) }
	return rakitanIpd{svc: svc, repo: repo, users: users, store: store}
}

func ipdSah() model.IpdInput {
	return model.IpdInput{
		IpdID: idIpd, UnitKode: "KP_STL", TanggalInspeksi: "2026-11-20", DivisiKode: "DIV1", Asm3Oid: 101, BlokKode: "A12",
		PemanenHrmbCode: 5001, AncakNo: "7", MandorUsername: "mandor1",
		Pokok: []model.IpdPokokInput{
			{NoBaris: 11, NoPokok: 1},
			{NoBaris: 11, NoPokok: 2, Temuan: []model.IpdTemuanInput{{Kode: "b1", Jumlah: 5}, {Kode: "S"}}},
		},
	}
}

func TestIpdTerimaMandorMenungguAsistenDivisi(t *testing.T) {
	r := rakitIpd()
	st, err := r.svc.Terima(context.Background(), klaimInspektur, ipdSah(), "1.2.3.4")
	if err != nil || st != StatusDiterima {
		t.Fatalf("status=%q err=%v", st, err)
	}
	p := r.repo.tersimpan[idIpd]
	if !p.Menunggu || r.repo.status[idIpd] != "MENUNGGU" {
		t.Fatalf("inspektur tanpa ipd.setujui harus MENUNGGU: %+v", p)
	}
	if p.UserID != 20 || p.AsistenID == nil || *p.AsistenID != 30 || p.DivisiID != 1 {
		t.Fatalf("inspektur dari token dan asisten dari divisi: %+v", p)
	}
	if p.Mandor.UserID != 20 {
		t.Fatalf("kemandoran salah: %+v", p.Mandor)
	}
	// Kode huruf kecil dinormalkan, jumlah kosong menjadi 1.
	tm := p.Input.Pokok[1].Temuan
	if tm[0].Kode != "B1" || tm[0].Jumlah != 5 || tm[1].Jumlah != 1 {
		t.Fatalf("temuan: %+v", tm)
	}
}

func TestIpdAsistenInspekturLangsungDisetujui(t *testing.T) {
	r := rakitIpd()
	if _, err := r.svc.Terima(context.Background(), klaimAsisten, ipdSah(), ""); err != nil {
		t.Fatal(err)
	}
	if r.repo.tersimpan[idIpd].Menunggu || r.repo.status[idIpd] != "DISETUJUI" {
		t.Fatal("pemegang ipd.setujui yang mengirim harus langsung DISETUJUI")
	}
}

func TestIpdKirimUlangAturanEdit(t *testing.T) {
	r := rakitIpd()
	ctx := context.Background()
	if _, err := r.svc.Terima(ctx, klaimInspektur, ipdSah(), ""); err != nil {
		t.Fatal(err)
	}
	// Percobaan ulang jaringan (tanpa kirim_ulang): tidak mengubah apa pun.
	in := ipdSah()
	in.Catatan = "baru"
	if st, err := r.svc.Terima(ctx, klaimInspektur, in, ""); err != nil || st != StatusSudahAda {
		t.Fatalf("tanpa kirim_ulang: %q %v", st, err)
	}
	if r.repo.tersimpan[idIpd].Input.Catatan != "" {
		t.Fatal("isi berubah tanpa kirim_ulang")
	}
	// Selama MENUNGGU boleh diubah.
	in.KirimUlang = true
	if st, err := r.svc.Terima(ctx, klaimInspektur, in, ""); err != nil || st != StatusDiterima {
		t.Fatalf("ubah saat MENUNGGU: %q %v", st, err)
	}
	if p := r.repo.tersimpan[idIpd]; !p.Ganti || p.Input.Catatan != "baru" || r.repo.status[idIpd] != "MENUNGGU" {
		t.Fatalf("harus diganti dan tetap MENUNGGU: %+v", p)
	}
	// DITOLAK boleh diperbaiki.
	r.repo.status[idIpd] = "DITOLAK"
	if _, err := r.svc.Terima(ctx, klaimInspektur, in, ""); err != nil {
		t.Fatalf("perbaiki DITOLAK: %v", err)
	}
	// Orang lain tanpa ipd.kelola ditolak; pemegang ipd.kelola boleh.
	r.users.perms[50] = []string{"ipd.kirim"}
	r.users.aktif[50] = true
	if _, err := r.svc.Terima(ctx, &auth.Klaim{UserID: 50, UnitID: 1}, in, ""); statusHTTP(err) != http.StatusForbidden {
		t.Fatalf("harapan 403: %v", err)
	}
	if _, err := r.svc.Terima(ctx, &auth.Klaim{UserID: 40, UnitID: 1}, in, ""); err != nil {
		t.Fatalf("ipd.kelola boleh: %v", err)
	}
	// Setelah asisten menyetujui: terkunci (423) dan isi tidak berubah.
	r.repo.status[idIpd] = "DISETUJUI"
	in.Catatan = "terlambat"
	_, err := r.svc.Terima(ctx, klaimInspektur, in, "")
	if statusHTTP(err) != http.StatusLocked || !strings.Contains(err.Error(), "disetujui asisten") {
		t.Fatalf("harapan 423: %v", err)
	}
	if r.repo.tersimpan[idIpd].Input.Catatan == "terlambat" {
		t.Fatal("isi catatan terkunci berubah")
	}
}

func TestIpdMasterDitolak422(t *testing.T) {
	kasus := map[string]func(r rakitanIpd, in *model.IpdInput){
		"divisi": func(r rakitanIpd, _ *model.IpdInput) { r.repo.master.DivisiAda = false },
		"blok":   func(r rakitanIpd, _ *model.IpdInput) { r.repo.master.BlokAda = false },
		"milik divisi": func(r rakitanIpd, _ *model.IpdInput) {
			r.repo.master.BlokDalamDivisi = false
		},
		"pemanen": func(r rakitanIpd, _ *model.IpdInput) { r.repo.master.PemanenAda = false },
		"kode temuan": func(_ rakitanIpd, in *model.IpdInput) {
			in.Pokok[1].Temuan = []model.IpdTemuanInput{{Kode: "Z9"}}
		},
		"mandor": func(_ rakitanIpd, in *model.IpdInput) { in.MandorUsername = "bukanmandor" },
	}
	for nama, ubah := range kasus {
		r := rakitIpd()
		in := ipdSah()
		ubah(r, &in)
		_, err := r.svc.Terima(context.Background(), klaimInspektur, in, "")
		if statusHTTP(err) != http.StatusUnprocessableEntity || !strings.Contains(strings.ToLower(err.Error()), strings.ToLower(nama)) {
			t.Errorf("%s: harapan 422 yang menyebut penyebab, dapat %v", nama, err)
		}
		if len(r.repo.tersimpan) != 0 {
			t.Errorf("%s: tidak boleh tersimpan", nama)
		}
	}
	// Unit berbeda dengan unit akun.
	r := rakitIpd()
	if _, err := r.svc.Terima(context.Background(), &auth.Klaim{UserID: 20, UnitID: 2}, ipdSah(), ""); statusHTTP(err) != http.StatusForbidden {
		t.Fatalf("unit berbeda harus 403: %v", err)
	}
}

func TestIpdValidasiBentuk(t *testing.T) {
	kasus := map[string]func(in *model.IpdInput){
		"ipd_id":        func(in *model.IpdInput) { in.IpdID = "bukan-uuid" },
		"tanggal":       func(in *model.IpdInput) { in.TanggalInspeksi = "20-11-2026" },
		"divisi_kode":   func(in *model.IpdInput) { in.DivisiKode = " " },
		"pemanen":       func(in *model.IpdInput) { in.PemanenHrmbCode = 0 },
		"mandor":        func(in *model.IpdInput) { in.MandorUsername = "" },
		"pokok":         func(in *model.IpdInput) { in.Pokok = nil },
		"pokok ganda":   func(in *model.IpdInput) { in.Pokok[1].NoPokok = 1 },
		"no_baris":      func(in *model.IpdInput) { in.Pokok[0].NoBaris = 0 },
		"kode ganda":    func(in *model.IpdInput) { in.Pokok[1].Temuan[1].Kode = "B1" },
		"jumlah temuan": func(in *model.IpdInput) { in.Pokok[1].Temuan[0].Jumlah = -2 },
	}
	for nama, ubah := range kasus {
		in := ipdSah()
		ubah(&in)
		if err := in.Validasi(); err == nil {
			t.Errorf("%s: harus ditolak", nama)
		}
	}
	in := ipdSah()
	if err := in.Validasi(); err != nil {
		t.Fatalf("catatan sah ditolak: %v", err)
	}
	in.Pokok = make([]model.IpdPokokInput, model.MaksPokokIpd+1)
	if err := in.Validasi(); err == nil {
		t.Error("pokok melebihi batas harus ditolak")
	}
}

func TestIpdPersetujuanGalat(t *testing.T) {
	kasus := map[error]int{
		repository.ErrNotFound:       http.StatusNotFound,
		repository.ErrStatusBerubah:  http.StatusConflict,
		repository.ErrBukanPenyetuju: http.StatusForbidden,
	}
	for galat, kode := range kasus {
		r := rakitIpd()
		r.repo.setujuErr = galat
		if err := r.svc.Setujui(context.Background(), klaimAsisten, idIpd, ""); statusHTTP(err) != kode {
			t.Errorf("%v: harapan %d, dapat %v", galat, kode, err)
		}
	}
	r := rakitIpd()
	if err := r.svc.Setujui(context.Background(), klaimAsisten, "bukan-uuid", ""); statusHTTP(err) != http.StatusUnprocessableEntity {
		t.Fatalf("id bukan UUID harus 422: %v", err)
	}
	if err := r.svc.Tolak(context.Background(), klaimAsisten, idIpd, "  ", ""); statusHTTP(err) != http.StatusUnprocessableEntity {
		t.Fatalf("alasan kosong harus 422: %v", err)
	}
	if err := r.svc.Tolak(context.Background(), klaimAsisten, idIpd, strings.Repeat("x", 301), ""); statusHTTP(err) != http.StatusUnprocessableEntity {
		t.Fatalf("alasan terlalu panjang harus 422: %v", err)
	}
	if err := r.svc.Tolak(context.Background(), klaimAsisten, idIpd, "Blok salah", ""); err != nil || r.repo.status[idIpd] != "DITOLAK" {
		t.Fatalf("tolak: %v %v", err, r.repo.status)
	}
}

func TestIpdInspeksiTerakhirJendelaTujuhHari(t *testing.T) {
	r := rakitIpd()
	o, err := r.svc.InspeksiTerakhir(context.Background(), klaimInspektur, 5001, 101, "", idIpd)
	if err != nil {
		t.Fatal(err)
	}
	// Hari ini 20 Nov 2026 (WIB): jendela 13 sampai 20 Nov; inspeksi yang sedang diedit dikecualikan.
	if r.repo.terakhir != [3]string{"2026-11-13", "2026-11-20", idIpd} {
		t.Fatalf("jendela: %v", r.repo.terakhir)
	}
	if !o.Ada || o.JendelaHari != 7 || len(o.Inspeksi) != 1 {
		t.Fatalf("jawaban: %+v", o)
	}
	if _, err := r.svc.InspeksiTerakhir(context.Background(), klaimInspektur, 0, 101, "", ""); statusHTTP(err) != http.StatusBadRequest {
		t.Fatalf("pemanen kosong harus 400: %v", err)
	}
	if _, err := r.svc.InspeksiTerakhir(context.Background(), klaimInspektur, 5001, 101, "2026-13-45", ""); statusHTTP(err) != http.StatusBadRequest {
		t.Fatalf("tanggal salah harus 400: %v", err)
	}
}

func TestSusunRingkasanDanPersen(t *testing.T) {
	kode := []model.IpdKodeMaster{{Kode: "A", Nama: "a", Kelompok: "BUAH"}, {Kode: "B1", Nama: "b", Kelompok: "BRONDOLAN"}, {Kode: "C", Nama: "c", Kelompok: "PELEPAH"}}
	m := &model.IpdMentah{PokokDiperiksa: 40, PokokBertemuan: 12, PokokLoses: 10, PokokLain: 2,
		PerKode: map[string]model.IpdHitung{"B1": {Pokok: 7, Jumlah: 30}, "A": {Pokok: 3, Jumlah: 3}, "C": {Pokok: 2, Jumlah: 2}}}
	r := susunRingkasan(m, kode)
	// Loses (buah, brondolan) dipisah dari temuan lain (pelepah): 10 pokok loses = 25%, 2 pokok temuan lain = 5%.
	if r.PersenPokokBertemuan != 30 || r.PersenPokokLoses != 25 || r.PersenPokokTemuanLain != 5 || r.TotalLoses != 33 || r.TotalTemuanLain != 2 || r.TotalTemuan != 35 || len(r.PerKode) != 3 {
		t.Fatalf("ringkasan: %+v", r)
	}
	if r.PerKode[0].Kode != "A" || r.PerKode[1].PersenPokok != 17.5 || r.PerKode[0].Jenis != "LOSES" || r.PerKode[2].Jenis != "TEMUAN" {
		t.Fatalf("urutan dan persen per kode: %+v", r.PerKode)
	}
	// Tanpa pokok: tidak membagi dengan nol; kode tetap muncul dengan nol.
	k := susunRingkasan(nil, kode)
	if k.PersenPokokBertemuan != 0 || k.PersenPokokLoses != 0 || len(k.PerKode) != 3 || k.PerKode[0].Jumlah != 0 {
		t.Fatalf("kosong: %+v", k)
	}
}

func TestSusunLaporanMenjumlahkanPerKelompokData(t *testing.T) {
	kode := []model.IpdKodeMaster{{Kode: "A", Nama: "a", Kelompok: "BUAH", Urut: 1}, {Kode: "B1", Nama: "b", Kelompok: "BRONDOLAN", Urut: 2}}
	mk := func(diperiksa, bertemuan int64, a, b model.IpdHitung) model.IpdMentah {
		return model.IpdMentah{PokokDiperiksa: diperiksa, PokokBertemuan: bertemuan, PokokLoses: bertemuan,
			PerKode:     map[string]model.IpdHitung{"A": a, "B1": b},
			PerKelompok: map[string]model.IpdHitung{"BUAH": a, "BRONDOLAN": b}}
	}
	dasar := []model.IpdDasar{
		{ID: "1", Tanggal: "2026-11-13", DivisiKode: "DIV1", BlokKode: "A12", PemanenHrmb: 5001, PemanenNama: "Budi", Mentah: mk(20, 4, model.IpdHitung{Pokok: 1, Jumlah: 1}, model.IpdHitung{Pokok: 3, Jumlah: 9})},
		{ID: "2", Tanggal: "2026-11-13", DivisiKode: "DIV1", BlokKode: "A12", PemanenHrmb: 5002, PemanenNama: "Andi", Mentah: mk(20, 1, model.IpdHitung{}, model.IpdHitung{Pokok: 1, Jumlah: 2})},
		{ID: "3", Tanggal: "2026-11-20", DivisiKode: "DIV2", BlokKode: "B01", PemanenHrmb: 5001, PemanenNama: "Budi", Mentah: mk(10, 0, model.IpdHitung{}, model.IpdHitung{})},
	}
	l := susunLaporan(dasar, kode, "2026-11-01", "2026-11-20")
	if l.Inspeksi != 3 || l.Pemanen != 2 {
		t.Fatalf("jumlah inspeksi/pemanen: %+v", l)
	}
	if l.Ringkasan.PokokDiperiksa != 50 || l.Ringkasan.PokokBertemuan != 5 || l.Ringkasan.PersenPokokBertemuan != 10 || l.Ringkasan.TotalTemuan != 12 {
		t.Fatalf("ringkasan: %+v", l.Ringkasan)
	}
	if l.PerKelompok[0].Kelompok != "BUAH" || l.PerKelompok[1].Kelompok != "BRONDOLAN" || l.PerKelompok[1].Pokok != 4 || l.PerKelompok[1].PersenPokok != 8 || l.PerKelompok[2].Kelompok != "PELEPAH" {
		t.Fatalf("per kelompok: %+v", l.PerKelompok)
	}
	// Pemanen dengan persen loses tertinggi di urutan pertama: Budi 4 dari 30 = 13,3; Andi 1 dari 20 = 5.
	if l.PerPemanen[0].Nama != "Budi" || l.PerPemanen[0].Inspeksi != 2 || l.PerPemanen[0].PersenPokokBertemuan != 13.3 || l.PerPemanen[0].TemuanPerKode["B1"] != 9 {
		t.Fatalf("per pemanen: %+v", l.PerPemanen)
	}
	if len(l.PerBlok) != 2 || l.PerBlok[0].Kunci != "A12" || len(l.PerDivisi) != 2 || l.PerDivisi[0].Kunci != "DIV1" {
		t.Fatalf("per blok/divisi: %+v %+v", l.PerBlok, l.PerDivisi)
	}
	if len(l.PerTanggal) != 2 || l.PerTanggal[0].Kunci != "2026-11-13" || l.PerTanggal[0].PokokDiperiksa != 40 {
		t.Fatalf("per tanggal: %+v", l.PerTanggal)
	}
	// Tanpa data: laporan kosong yang sah.
	if k := susunLaporan(nil, kode, "a", "b"); k.Inspeksi != 0 || k.Ringkasan.PersenPokokBertemuan != 0 || len(k.PerPemanen) != 0 || len(k.PerKelompok) != 3 {
		t.Fatalf("kosong: %+v", k)
	}
}

func TestIpdFotoIdempotenDanDiperiksa(t *testing.T) {
	r := rakitIpd()
	ctx := context.Background()
	if _, err := r.svc.Terima(ctx, klaimInspektur, ipdSah(), ""); err != nil {
		t.Fatal(err)
	}
	jpeg := append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, bytes.Repeat([]byte{0x42}, 100)...)
	h := sha256.Sum256(jpeg)
	req := IpdMediaRequest{IpdID: idIpd, FotoID: "cccccccc-cccc-4ccc-8ccc-cccccccccccc", SHA256: hex.EncodeToString(h[:]), Keterangan: " buah tinggal di baris 11 ", Urut: 2}
	if st, err := r.svc.TerimaFoto(ctx, klaimInspektur, req, bytes.NewReader(jpeg)); err != nil || st != StatusDiterima {
		t.Fatalf("foto pertama: %q %v", st, err)
	}
	if f := r.repo.fotoSimpan[0]; f.Keterangan != "buah tinggal di baris 11" || f.Urut != 2 || !strings.HasPrefix(f.NamaFile, "ipd_"+idIpd) {
		t.Fatalf("foto tersimpan: %+v", f)
	}
	if st, err := r.svc.TerimaFoto(ctx, klaimInspektur, req, bytes.NewReader(jpeg)); err != nil || st != StatusSudahAda {
		t.Fatalf("foto sama harus SUDAH_ADA: %q %v", st, err)
	}
	// Banyak foto per inspeksi diterima (tanpa batas jumlah).
	for i := 0; i < 15; i++ {
		x := req
		x.FotoID = fmt.Sprintf("dddddddd-dddd-4ddd-8ddd-%012x", i)
		if _, err := r.svc.TerimaFoto(ctx, klaimInspektur, x, bytes.NewReader(jpeg)); err != nil {
			t.Fatalf("foto ke-%d: %v", i, err)
		}
	}
	// Checksum salah, bukan JPEG, bukan pemilik, inspeksi belum ada.
	salah := req
	salah.FotoID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
	salah.SHA256 = strings.Repeat("0", 64)
	if _, err := r.svc.TerimaFoto(ctx, klaimInspektur, salah, bytes.NewReader(jpeg)); statusHTTP(err) != http.StatusUnprocessableEntity {
		t.Fatalf("checksum salah harus 422: %v", err)
	}
	png := []byte{0x89, 'P', 'N', 'G', 1, 2, 3}
	hp := sha256.Sum256(png)
	bukanJpeg := salah
	bukanJpeg.SHA256 = hex.EncodeToString(hp[:])
	if _, err := r.svc.TerimaFoto(ctx, klaimInspektur, bukanJpeg, bytes.NewReader(png)); statusHTTP(err) != http.StatusUnsupportedMediaType {
		t.Fatalf("bukan JPEG harus 415: %v", err)
	}
	r.users.perms[50] = []string{"ipd.kirim"}
	r.users.aktif[50] = true
	if _, err := r.svc.TerimaFoto(ctx, &auth.Klaim{UserID: 50, UnitID: 1}, salah, bytes.NewReader(jpeg)); statusHTTP(err) != http.StatusForbidden {
		t.Fatalf("bukan pemilik harus 403: %v", err)
	}
	belum := req
	belum.IpdID = "ffffffff-ffff-4fff-8fff-ffffffffffff"
	if _, err := r.svc.TerimaFoto(ctx, klaimInspektur, belum, bytes.NewReader(jpeg)); statusHTTP(err) != http.StatusNotFound {
		t.Fatalf("inspeksi belum ada harus 404: %v", err)
	}
	panjang := req
	panjang.FotoID = "99999999-9999-4999-8999-999999999999"
	panjang.Keterangan = strings.Repeat("k", 201)
	if _, err := r.svc.TerimaFoto(ctx, klaimInspektur, panjang, bytes.NewReader(jpeg)); statusHTTP(err) != http.StatusBadRequest {
		t.Fatalf("keterangan terlalu panjang harus 400: %v", err)
	}
}
