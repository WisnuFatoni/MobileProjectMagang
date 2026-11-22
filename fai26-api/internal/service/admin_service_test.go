package service

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

var admin = &auth.Klaim{UserID: 30, UnitID: 1, DeviceID: "99999999-9999-4999-8999-999999999999"}

// ---- tiruan --------------------------------------------------------------------------------------

type fakeAdminMaster struct {
	err       error // dikembalikan semua operasi tulis
	dipanggil []string
	massal    model.TphMassalInput
	blokQr    int
	tphQr     bool
}

func (f *fakeAdminMaster) catat(n string) error { f.dipanggil = append(f.dipanggil, n); return f.err }

func (f *fakeAdminMaster) DaftarDivisi(context.Context, int64) ([]model.BarisMaster, error) {
	return nil, f.err
}
func (f *fakeAdminMaster) DaftarBlok(context.Context, int64) ([]model.BarisMaster, error) {
	return nil, f.err
}
func (f *fakeAdminMaster) DaftarTph(context.Context, int64, int64) ([]model.BarisMaster, error) {
	return nil, f.err
}
func (f *fakeAdminMaster) DaftarPemanen(context.Context, int64) ([]model.BarisMaster, error) {
	return nil, f.err
}
func (f *fakeAdminMaster) BuatDivisi(context.Context, int64, int64, model.DivisiInput) (int64, error) {
	return 7, f.catat("BuatDivisi")
}
func (f *fakeAdminMaster) UbahDivisi(context.Context, int64, int64, int64, model.DivisiInput) error {
	return f.catat("UbahDivisi")
}
func (f *fakeAdminMaster) HapusDivisi(context.Context, int64, int64, int64) error {
	return f.catat("HapusDivisi")
}
func (f *fakeAdminMaster) BuatBlok(context.Context, int64, int64, model.BlokInput) (int64, error) {
	return 8, f.catat("BuatBlok")
}
func (f *fakeAdminMaster) UbahBlok(context.Context, int64, int64, int64, model.BlokInput) (int, error) {
	return f.blokQr, f.catat("UbahBlok")
}
func (f *fakeAdminMaster) HapusBlok(context.Context, int64, int64, int64) error {
	return f.catat("HapusBlok")
}
func (f *fakeAdminMaster) BuatTph(context.Context, int64, int64, model.TphInput) (int64, error) {
	return 9, f.catat("BuatTph")
}
func (f *fakeAdminMaster) BuatTphMassal(_ context.Context, _, _ int64, in model.TphMassalInput) ([]int64, error) {
	f.massal = in
	return []int64{1, 2, 3}, f.catat("BuatTphMassal")
}
func (f *fakeAdminMaster) UbahTph(context.Context, int64, int64, int64, model.TphInput) (bool, error) {
	return f.tphQr, f.catat("UbahTph")
}
func (f *fakeAdminMaster) HapusTph(context.Context, int64, int64, int64) error {
	return f.catat("HapusTph")
}
func (f *fakeAdminMaster) BuatPemanen(context.Context, int64, int64, model.PemanenAdminInput) (int64, error) {
	return 10, f.catat("BuatPemanen")
}
func (f *fakeAdminMaster) UbahPemanen(context.Context, int64, int64, int64, model.PemanenAdminInput) error {
	return f.catat("UbahPemanen")
}
func (f *fakeAdminMaster) HapusPemanen(context.Context, int64, int64, int64) error {
	return f.catat("HapusPemanen")
}

type fakeAdmin struct {
	produk       []model.ProduktivitasPemanen
	dipanggil    []string
	err          error
	filterPanen  model.FilterPanen
	filterAudit  model.FilterAudit
	tanggal      string
	hashDiterima string
}

func (f *fakeAdmin) catat(n string) error                                  { f.dipanggil = append(f.dipanggil, n); return f.err }
func (f *fakeAdmin) DaftarRole(context.Context) ([]model.RoleAdmin, error) { return nil, f.err }
func (f *fakeAdmin) DaftarPengguna(context.Context, int64) ([]model.PenggunaAdmin, error) {
	return nil, f.err
}
func (f *fakeAdmin) BuatPengguna(_ context.Context, _, _ int64, _ model.PenggunaInput, hash string) (int64, error) {
	f.hashDiterima = hash
	return 5, f.catat("BuatPengguna")
}
func (f *fakeAdmin) UbahPengguna(context.Context, int64, int64, int64, model.PenggunaInput) error {
	return f.catat("UbahPengguna")
}
func (f *fakeAdmin) ResetPassword(_ context.Context, _, _, _ int64, hash string) error {
	f.hashDiterima = hash
	return f.catat("ResetPassword")
}
func (f *fakeAdmin) DaftarPerangkat(context.Context) ([]model.PerangkatAdmin, error) {
	return nil, f.err
}
func (f *fakeAdmin) UbahPerangkat(context.Context, int64, string, bool) error {
	return f.catat("UbahPerangkat")
}
func (f *fakeAdmin) Ringkasan(_ context.Context, _ int64, hari string) (*model.RingkasanAdmin, error) {
	f.tanggal = hari
	return &model.RingkasanAdmin{}, f.err
}
func (f *fakeAdmin) Produktivitas(_ context.Context, _ int64, tanggal string) ([]model.ProduktivitasPemanen, error) {
	f.tanggal = tanggal
	return f.produk, f.err
}
func (f *fakeAdmin) DaftarPanen(_ context.Context, _ int64, fl model.FilterPanen) (*model.HalamanPanen, error) {
	f.filterPanen = fl
	return &model.HalamanPanen{}, f.err
}
func (f *fakeAdmin) DetailPanen(context.Context, int64, string) (*model.PanenDetail, error) {
	return &model.PanenDetail{}, f.err
}
func (f *fakeAdmin) PathFoto(context.Context, int64, string) (string, error) { return "a/b.jpg", f.err }
func (f *fakeAdmin) DaftarAudit(_ context.Context, fl model.FilterAudit) (*model.HalamanAudit, error) {
	f.filterAudit = fl
	return &model.HalamanAudit{}, f.err
}

func ptrF(v float64) *float64 { return &v }
func ptrI(v int64) *int64     { return &v }
func ptrB(v bool) *bool       { return &v }

// ---- master --------------------------------------------------------------------------------------

func TestMasterValidasiMenolakSebelumKeRepository(t *testing.T) {
	kasus := []struct {
		nama string
		aksi func(s *AdminMasterService) error
	}{
		{"divisi tanpa kode", func(s *AdminMasterService) error {
			_, e := s.BuatDivisi(context.Background(), admin, model.DivisiInput{Nama: "x"})
			return e
		}},
		{"divisi kode berisi |", func(s *AdminMasterService) error {
			_, e := s.BuatDivisi(context.Background(), admin, model.DivisiInput{Kode: "A|B", Nama: "x"})
			return e
		}},
		{"divisi kode berspasi di ujung", func(s *AdminMasterService) error {
			_, e := s.BuatDivisi(context.Background(), admin, model.DivisiInput{Kode: "A ", Nama: "x"})
			return e
		}},
		{"blok tanpa erp_oid", func(s *AdminMasterService) error {
			_, e := s.BuatBlok(context.Background(), admin, model.BlokInput{Kode: "A12"})
			return e
		}},
		{"blok luas negatif", func(s *AdminMasterService) error {
			_, e := s.BuatBlok(context.Background(), admin, model.BlokInput{Kode: "A12", ErpOid: 1, LuasHa: ptrF(-1)})
			return e
		}},
		{"blok tahun tanam aneh", func(s *AdminMasterService) error {
			_, e := s.BuatBlok(context.Background(), admin, model.BlokInput{Kode: "A12", ErpOid: 1, TahunTanam: ptrI(1800)})
			return e
		}},
		{"tph tanpa blok", func(s *AdminMasterService) error {
			_, e := s.BuatTph(context.Background(), admin, model.TphInput{NoTph: "01"})
			return e
		}},
		{"tph nomor berisi |", func(s *AdminMasterService) error {
			_, e := s.BuatTph(context.Background(), admin, model.TphInput{BlokID: 1, NoTph: "0|1"})
			return e
		}},
		{"tph hanya lat", func(s *AdminMasterService) error {
			_, e := s.BuatTph(context.Background(), admin, model.TphInput{BlokID: 1, NoTph: "01", Lat: ptrF(-2)})
			return e
		}},
		{"tph lat di luar rentang", func(s *AdminMasterService) error {
			_, e := s.BuatTph(context.Background(), admin, model.TphInput{BlokID: 1, NoTph: "01", Lat: ptrF(91), Lon: ptrF(0)})
			return e
		}},
		{"tph lon di luar rentang", func(s *AdminMasterService) error {
			_, e := s.UbahTph(context.Background(), admin, 1, model.TphInput{BlokID: 1, NoTph: "01", Lat: ptrF(0), Lon: ptrF(181)})
			return e
		}},
		{"massal dari nol", func(s *AdminMasterService) error {
			_, e := s.BuatTphMassal(context.Background(), admin, model.TphMassalInput{BlokID: 1, Dari: 0, Sampai: 5, Lebar: 2})
			return e
		}},
		{"massal terbalik", func(s *AdminMasterService) error {
			_, e := s.BuatTphMassal(context.Background(), admin, model.TphMassalInput{BlokID: 1, Dari: 5, Sampai: 2, Lebar: 2})
			return e
		}},
		{"massal terlalu banyak", func(s *AdminMasterService) error {
			_, e := s.BuatTphMassal(context.Background(), admin, model.TphMassalInput{BlokID: 1, Dari: 1, Sampai: 501, Lebar: 4})
			return e
		}},
		{"massal lebar kecil", func(s *AdminMasterService) error {
			_, e := s.BuatTphMassal(context.Background(), admin, model.TphMassalInput{BlokID: 1, Dari: 1, Sampai: 120, Lebar: 2})
			return e
		}},
		{"massal tanpa blok", func(s *AdminMasterService) error {
			_, e := s.BuatTphMassal(context.Background(), admin, model.TphMassalInput{Dari: 1, Sampai: 2, Lebar: 2})
			return e
		}},
		{"pemanen tanpa kode ERP", func(s *AdminMasterService) error {
			_, e := s.BuatPemanen(context.Background(), admin, model.PemanenAdminInput{Nama: "x"})
			return e
		}},
		{"pemanen tanpa nama", func(s *AdminMasterService) error {
			e := s.UbahPemanen(context.Background(), admin, 1, model.PemanenAdminInput{ErpHrmbCode: 1})
			return e
		}},
	}
	for _, k := range kasus {
		t.Run(k.nama, func(t *testing.T) {
			f := &fakeAdminMaster{}
			err := k.aksi(NewAdminMasterService(f))
			if statusHTTP(err) != 422 {
				t.Fatalf("harapan 422, dapat %v", err)
			}
			if len(f.dipanggil) != 0 {
				t.Fatalf("repository tidak boleh dipanggil: %v", f.dipanggil)
			}
		})
	}
}

func TestMasterSahDiteruskanDanIdDikembalikan(t *testing.T) {
	f := &fakeAdminMaster{}
	s := NewAdminMasterService(f)
	ctx := context.Background()
	if id, err := s.BuatBlok(ctx, admin, model.BlokInput{Kode: "A12", ErpOid: 1012}); err != nil || id != 8 {
		t.Fatalf("id=%d err=%v", id, err)
	}
	if id, err := s.BuatTph(ctx, admin, model.TphInput{BlokID: 1, NoTph: "01", Lat: ptrF(-2), Lon: ptrF(113)}); err != nil || id != 9 {
		t.Fatalf("id=%d err=%v", id, err)
	}
	ids, err := s.BuatTphMassal(ctx, admin, model.TphMassalInput{BlokID: 1, Dari: 1, Sampai: 20, Lebar: 2})
	if err != nil || len(ids) != 3 || f.massal.Sampai != 20 {
		t.Fatalf("massal: %v %v", ids, err)
	}
}

func TestMasterGalatRepositoryDipetakanKeKodeHttp(t *testing.T) {
	kasus := []struct {
		err  error
		kode int
	}{
		{repository.ErrNotFound, 404},
		{repository.ErrDuplikat, 409},
		{repository.ErrMasihDipakai, 409},
		{repository.ErrReferensi, 422},
		{errors.New("detail SQL rahasia"), 500},
	}
	for _, k := range kasus {
		f := &fakeAdminMaster{err: k.err}
		_, err := NewAdminMasterService(f).BuatBlok(context.Background(), admin, model.BlokInput{Kode: "A12", ErpOid: 1})
		if statusHTTP(err) != k.kode {
			t.Errorf("%v: harapan %d, dapat %v", k.err, k.kode, err)
		}
		if strings.Contains(err.Error(), "rahasia") && k.kode != 500 {
			t.Errorf("pesan tidak boleh membawa detail teknis")
		}
	}
	// Pesan 500 tidak membocorkan detail ke klien.
	_, err := NewAdminMasterService(&fakeAdminMaster{err: errors.New("detail SQL rahasia")}).BuatBlok(context.Background(), admin, model.BlokInput{Kode: "A", ErpOid: 1})
	if strings.Contains(statusPesan(err), "rahasia") {
		t.Fatal("detail teknis bocor ke klien")
	}
}

func statusPesan(err error) string { return apperror.Dari(err).Message }

// ---- pengguna dan lain-lain ----------------------------------------------------------------------

func TestPenggunaValidasiDanHash(t *testing.T) {
	f := &fakeAdmin{}
	s := NewAdminService(f)
	s.hash = func(p string) (string, error) { return "HASH(" + p + ")", nil }
	ctx := context.Background()

	buruk := []model.PenggunaInput{
		{Username: "AB", Nama: "x", Password: "password123", Role: []string{"KRANI"}},         // terlalu pendek
		{Username: "krani baru", Nama: "x", Password: "password123", Role: []string{"KRANI"}}, // spasi
		{Username: "krani2", Nama: "", Password: "password123", Role: []string{"KRANI"}},      // tanpa nama
		{Username: "krani2", Nama: "x", Password: "pendek", Role: []string{"KRANI"}},          // password pendek
		{Username: "krani2", Nama: "x", Password: "password123"},                              // tanpa role
	}
	for i, in := range buruk {
		if _, err := s.BuatPengguna(ctx, admin, in); statusHTTP(err) != 422 {
			t.Errorf("kasus %d: harapan 422, dapat %v", i, err)
		}
	}
	if len(f.dipanggil) != 0 {
		t.Fatal("repository tidak boleh dipanggil untuk masukan tidak sah")
	}
	id, err := s.BuatPengguna(ctx, admin, model.PenggunaInput{Username: " Krani2 ", Nama: "Krani Dua", Password: "password123", Role: []string{"KRANI"}})
	if err != nil || id != 5 {
		t.Fatalf("id=%d err=%v", id, err)
	}
	if f.hashDiterima != "HASH(password123)" {
		t.Fatalf("password harus di-hash sebelum ke repository, dapat %q", f.hashDiterima)
	}
}

func TestPenggunaUsernameGandaMenjadi409(t *testing.T) {
	f := &fakeAdmin{err: repository.ErrDuplikat}
	s := NewAdminService(f)
	_, err := s.BuatPengguna(context.Background(), admin, model.PenggunaInput{Username: "krani1", Nama: "x", Password: "password123", Role: []string{"KRANI"}})
	if statusHTTP(err) != 409 {
		t.Fatalf("harapan 409, dapat %v", err)
	}
}

func TestTidakBolehMenonaktifkanDiriSendiri(t *testing.T) {
	f := &fakeAdmin{}
	s := NewAdminService(f)
	err := s.UbahPengguna(context.Background(), admin, admin.UserID, model.PenggunaInput{Nama: "x", Aktif: ptrB(false), Role: []string{"ADMIN"}})
	if statusHTTP(err) != 422 {
		t.Fatalf("harapan 422, dapat %v", err)
	}
	if err := s.UbahPengguna(context.Background(), admin, 99, model.PenggunaInput{Nama: "x", Aktif: ptrB(false), Role: []string{"KRANI"}}); err != nil {
		t.Fatalf("menonaktifkan orang lain boleh: %v", err)
	}
	// Perangkat yang sedang dipakai juga tidak boleh dimatikan.
	if err := s.UbahPerangkat(context.Background(), admin, strings.ToUpper(admin.DeviceID), model.PerangkatUbahInput{Aktif: false}); statusHTTP(err) != 422 {
		t.Fatalf("perangkat sendiri: %v", err)
	}
	if err := s.UbahPerangkat(context.Background(), admin, "bukan-uuid", model.PerangkatUbahInput{Aktif: true}); statusHTTP(err) != 422 {
		t.Fatalf("uuid buruk: %v", err)
	}
}

func TestResetPasswordMemvalidasiDanMenghash(t *testing.T) {
	f := &fakeAdmin{}
	s := NewAdminService(f)
	s.hash = func(p string) (string, error) { return "H:" + p, nil }
	if err := s.ResetPassword(context.Background(), admin, 4, model.ResetPasswordInput{Password: "pendek"}); statusHTTP(err) != 422 {
		t.Fatalf("password pendek: %v", err)
	}
	if err := s.ResetPassword(context.Background(), admin, 4, model.ResetPasswordInput{Password: strings.Repeat("a", 73)}); statusHTTP(err) != 422 {
		t.Fatalf("password kepanjangan (batas bcrypt 72): %v", err)
	}
	if len(f.dipanggil) != 0 {
		t.Fatal("tidak boleh sampai repository")
	}
	if err := s.ResetPassword(context.Background(), admin, 4, model.ResetPasswordInput{Password: "sandiBaru123"}); err != nil {
		t.Fatal(err)
	}
	if f.hashDiterima != "H:sandiBaru123" {
		t.Fatalf("hash: %q", f.hashDiterima)
	}
}

func TestFilterPanenDanAuditDinormalisasi(t *testing.T) {
	f := &fakeAdmin{}
	s := NewAdminService(f)
	ctx := context.Background()

	if _, err := s.DaftarPanen(ctx, admin, model.FilterPanen{}); err != nil {
		t.Fatal(err)
	}
	if f.filterPanen.Halaman != 1 || f.filterPanen.PerHal != 25 {
		t.Fatalf("bawaan: %+v", f.filterPanen)
	}
	if _, err := s.DaftarPanen(ctx, admin, model.FilterPanen{Halaman: -3, PerHal: 5000}); err != nil {
		t.Fatal(err)
	}
	if f.filterPanen.Halaman != 1 || f.filterPanen.PerHal != 100 {
		t.Fatalf("batas: %+v", f.filterPanen)
	}
	// MENUNGGU (belum disetujui krani) harus bisa disaring: dasbor Persetujuan Tertunda memakainya.
	for _, baik := range []string{"MENUNGGU", "BARU", "DIBACA", "DIIMPOR", "DITOLAK"} {
		if _, err := s.DaftarPanen(ctx, admin, model.FilterPanen{StatusEr: baik}); err != nil {
			t.Errorf("status_erp %s ditolak: %v", baik, err)
		}
	}
	for _, buruk := range []model.FilterPanen{{Dari: "10/11/2026"}, {Sampai: "kemarin"}, {StatusEr: "SELESAI"}} {
		if _, err := s.DaftarPanen(ctx, admin, buruk); statusHTTP(err) != 422 {
			t.Errorf("%+v: %v", buruk, err)
		}
	}
	if _, err := s.DaftarAudit(ctx, model.FilterAudit{Dari: "2026-13-45x"}); statusHTTP(err) != 422 {
		t.Fatalf("tanggal audit buruk: %v", err)
	}
	if _, err := s.DaftarAudit(ctx, model.FilterAudit{PerHal: 10, Halaman: 2}); err != nil || f.filterAudit.Halaman != 2 {
		t.Fatalf("audit: %+v %v", f.filterAudit, err)
	}
}

func TestDetailDanFotoMenolakIdBukanUuid(t *testing.T) {
	s := NewAdminService(&fakeAdmin{})
	if _, err := s.DetailPanen(context.Background(), admin, "../../etc/passwd"); statusHTTP(err) != 422 {
		t.Fatalf("detail: %v", err)
	}
	if _, err := s.PathFoto(context.Background(), admin, "../x"); statusHTTP(err) != 422 {
		t.Fatalf("foto: %v", err)
	}
	if p, err := s.PathFoto(context.Background(), admin, "EEEEEEEE-EEEE-4EEE-8EEE-EEEEEEEEEEEE"); err != nil || p != "a/b.jpg" {
		t.Fatalf("foto sah: %q %v", p, err)
	}
}

func TestRingkasanTanggalBawaanMemakaiWib(t *testing.T) {
	f := &fakeAdmin{}
	s := NewAdminService(f)
	// 10 Nov 2026 20:00 UTC sudah 11 Nov 03:00 di WIB.
	s.sekarang = func() time.Time { return time.Date(2026, 11, 10, 20, 0, 0, 0, time.UTC) }
	if _, err := s.Ringkasan(context.Background(), admin, ""); err != nil {
		t.Fatal(err)
	}
	if f.tanggal != "2026-11-11" {
		t.Fatalf("harapan 2026-11-11 (WIB), dapat %s", f.tanggal)
	}
	if _, err := s.Ringkasan(context.Background(), admin, "2026-11-01"); err != nil || f.tanggal != "2026-11-01" {
		t.Fatalf("tanggal eksplisit: %s %v", f.tanggal, err)
	}
	if _, err := s.Ringkasan(context.Background(), admin, "kemarin"); statusHTTP(err) != 422 {
		t.Fatalf("tanggal buruk: %v", err)
	}
}

func TestProduktivitasTanggalBawaanMemakaiWib(t *testing.T) {
	f := &fakeAdmin{}
	s := NewAdminService(f)
	s.sekarang = func() time.Time { return time.Date(2026, 11, 10, 20, 0, 0, 0, time.UTC) }
	if _, err := s.Produktivitas(context.Background(), admin, ""); err != nil || f.tanggal != "2026-11-11" {
		t.Fatalf("bawaan harus 2026-11-11 (WIB): %s %v", f.tanggal, err)
	}
	if _, err := s.Produktivitas(context.Background(), admin, "2026-11-01"); err != nil || f.tanggal != "2026-11-01" {
		t.Fatalf("tanggal eksplisit: %s %v", f.tanggal, err)
	}
	if _, err := s.Produktivitas(context.Background(), admin, "kemarin"); statusHTTP(err) != 422 {
		t.Fatalf("tanggal buruk harus 422: %v", err)
	}
}

func TestSusunProduktivitasPeringkatDanAngka(t *testing.T) {
	// Kosong: daftar [] (bukan null) dan semua angka nol.
	k := susunProduktivitas("2026-11-10", nil)
	if k.Pemanen == nil || len(k.Pemanen) != 0 || k.JumlahPemanen != 0 || k.TotalJanjang != 0 || k.RataJanjang != 0 {
		t.Fatalf("kosong: %+v", k)
	}

	// Diurutkan janjang terbanyak; janjang sama berarti peringkat sama; peringkat berikutnya melompat.
	in := []model.ProduktivitasPemanen{
		{HrmbCode: 3, Nama: "CECEP", Janjang: 20, JanjangMatang: 10, BrondolanKg: 1.26, Catatan: 2},
		{HrmbCode: 1, Nama: "ALPA", Janjang: 30, JanjangMatang: 27, Catatan: 3},
		{HrmbCode: 2, Nama: "BAYU", Janjang: 30, JanjangMatang: 15, Catatan: 3},
		{HrmbCode: 4, Nama: "DEDI", Janjang: 0, Catatan: 1},
	}
	o := susunProduktivitas("2026-11-10", in)
	if o.JumlahPemanen != 4 || o.TotalJanjang != 80 || o.RataJanjang != 20 {
		t.Fatalf("total: %+v", o)
	}
	urut := []string{"ALPA", "BAYU", "CECEP", "DEDI"}
	peringkat := []int{1, 1, 3, 4}
	for i, p := range o.Pemanen {
		if p.Nama != urut[i] || p.Peringkat != peringkat[i] {
			t.Fatalf("baris %d: %s peringkat %d (harapan %s %d)", i, p.Nama, p.Peringkat, urut[i], peringkat[i])
		}
	}
	if o.Pemanen[0].PersenMatang != 90 || o.Pemanen[1].PersenMatang != 50 || o.Pemanen[2].PersenMatang != 50 {
		t.Fatalf("persen matang: %+v", o.Pemanen)
	}
	if o.Pemanen[3].PersenMatang != 0 {
		t.Fatalf("janjang nol tidak boleh membagi nol: %+v", o.Pemanen[3])
	}
	if o.Pemanen[2].BrondolanKg != 1.3 {
		t.Fatalf("brondolan dibulatkan satu desimal: %v", o.Pemanen[2].BrondolanKg)
	}
}
