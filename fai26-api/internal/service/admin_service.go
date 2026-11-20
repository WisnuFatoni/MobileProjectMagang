package service

import (
	"context"
	"errors"
	"fmt"
	"math"
	"regexp"
	"sort"
	"strings"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

// ---- Antarmuka repository ------------------------------------------------------------------------

type AdminMasterRepo interface {
	DaftarDivisi(ctx context.Context, unitID int64) ([]model.BarisMaster, error)
	DaftarBlok(ctx context.Context, unitID int64) ([]model.BarisMaster, error)
	DaftarTph(ctx context.Context, unitID, blokID int64) ([]model.BarisMaster, error)
	DaftarPemanen(ctx context.Context, unitID int64) ([]model.BarisMaster, error)
	BuatDivisi(ctx context.Context, unitID, userID int64, in model.DivisiInput) (int64, error)
	UbahDivisi(ctx context.Context, unitID, userID, id int64, in model.DivisiInput) error
	HapusDivisi(ctx context.Context, unitID, userID, id int64) error
	BuatBlok(ctx context.Context, unitID, userID int64, in model.BlokInput) (int64, error)
	UbahBlok(ctx context.Context, unitID, userID, id int64, in model.BlokInput) (int, error)
	HapusBlok(ctx context.Context, unitID, userID, id int64) error
	BuatTph(ctx context.Context, unitID, userID int64, in model.TphInput) (int64, error)
	BuatTphMassal(ctx context.Context, unitID, userID int64, in model.TphMassalInput) ([]int64, error)
	UbahTph(ctx context.Context, unitID, userID, id int64, in model.TphInput) (bool, error)
	HapusTph(ctx context.Context, unitID, userID, id int64) error
	BuatPemanen(ctx context.Context, unitID, userID int64, in model.PemanenAdminInput) (int64, error)
	UbahPemanen(ctx context.Context, unitID, userID, id int64, in model.PemanenAdminInput) error
	HapusPemanen(ctx context.Context, unitID, userID, id int64) error
}

type AdminRepo interface {
	DaftarRole(ctx context.Context) ([]model.RoleAdmin, error)
	DaftarPengguna(ctx context.Context, unitID int64) ([]model.PenggunaAdmin, error)
	BuatPengguna(ctx context.Context, unitID, aktorID int64, in model.PenggunaInput, hash string) (int64, error)
	UbahPengguna(ctx context.Context, unitID, aktorID, id int64, in model.PenggunaInput) error
	ResetPassword(ctx context.Context, unitID, aktorID, id int64, hash string) error
	DaftarPerangkat(ctx context.Context) ([]model.PerangkatAdmin, error)
	UbahPerangkat(ctx context.Context, aktorID int64, deviceID string, aktif bool) error
	Ringkasan(ctx context.Context, unitID int64, hariIni string) (*model.RingkasanAdmin, error)
	Produktivitas(ctx context.Context, unitID int64, tanggal string) ([]model.ProduktivitasPemanen, error)
	DaftarPanen(ctx context.Context, unitID int64, f model.FilterPanen) (*model.HalamanPanen, error)
	DetailPanen(ctx context.Context, unitID int64, id string) (*model.PanenDetail, error)
	PathFoto(ctx context.Context, unitID int64, fotoID string) (string, error)
	DaftarAudit(ctx context.Context, f model.FilterAudit) (*model.HalamanAudit, error)
}

// ---- Validasi bersama ----------------------------------------------------------------------------

const (
	perHalamanBawaan   = 25
	perHalamanMaks     = 100
	maksTphMassal      = 500
	panjangPasswordMin = 8
)

var (
	reUsername = regexp.MustCompile(`^[a-z0-9._-]{3,50}$`)
	reTanggal  = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)
	statusErp  = map[string]bool{"BARU": true, "DIBACA": true, "DIIMPOR": true, "DITOLAK": true}
	wib        = time.FixedZone("WIB", 7*3600)
)

// kodeSah: tidak kosong, panjang dibatasi, dan tanpa karakter '|' (dipakai sebagai pemisah di isi QR).
func kodeSah(nama, nilai string, maks int) error {
	if strings.TrimSpace(nilai) == "" {
		return fmt.Errorf("%s wajib diisi", nama)
	}
	if len(nilai) > maks {
		return fmt.Errorf("%s maksimal %d karakter", nama, maks)
	}
	if strings.ContainsAny(nilai, "|\r\n") {
		return fmt.Errorf("%s tidak boleh mengandung karakter | atau baris baru", nama)
	}
	if nilai != strings.TrimSpace(nilai) {
		return fmt.Errorf("%s tidak boleh diawali atau diakhiri spasi", nama)
	}
	return nil
}

func teksSah(nama, nilai string, maks int) error {
	if strings.TrimSpace(nilai) == "" {
		return fmt.Errorf("%s wajib diisi", nama)
	}
	if len(nilai) > maks {
		return fmt.Errorf("%s maksimal %d karakter", nama, maks)
	}
	return nil
}

// galatRepo menerjemahkan galat repository menjadi galat aplikasi.
func galatRepo(err error, apaIni string) error {
	switch {
	case err == nil:
		return nil
	case errors.Is(err, repository.ErrNotFound):
		return apperror.NotFound(apaIni + " tidak ditemukan")
	case errors.Is(err, repository.ErrDuplikat):
		return apperror.Conflict(apaIni + " dengan kode atau nomor yang sama sudah ada")
	case errors.Is(err, repository.ErrMasihDipakai):
		return apperror.Conflict(apaIni + " masih dipakai data lain. Hapus atau pindahkan isinya dulu")
	case errors.Is(err, repository.ErrReferensi):
		return apperror.Unprocessable("Data yang dirujuk (blok, divisi, atau role) tidak ada")
	default:
		return apperror.Internal("Gagal memproses "+apaIni, err)
	}
}

func tidakValid(err error) error {
	if err == nil {
		return nil
	}
	return apperror.Unprocessable(err.Error())
}

// ---- Master --------------------------------------------------------------------------------------

type AdminMasterService struct{ repo AdminMasterRepo }

func NewAdminMasterService(repo AdminMasterRepo) *AdminMasterService {
	return &AdminMasterService{repo: repo}
}

func daftar(out []model.BarisMaster, err error, nama string) ([]model.BarisMaster, error) {
	if err != nil {
		return nil, apperror.Internal("Gagal membaca "+nama, err)
	}
	if out == nil {
		out = []model.BarisMaster{}
	}
	return out, nil
}

func (s *AdminMasterService) DaftarDivisi(ctx context.Context, k *auth.Klaim) ([]model.BarisMaster, error) {
	o, err := s.repo.DaftarDivisi(ctx, k.UnitID)
	return daftar(o, err, "divisi")
}

func (s *AdminMasterService) DaftarBlok(ctx context.Context, k *auth.Klaim) ([]model.BarisMaster, error) {
	o, err := s.repo.DaftarBlok(ctx, k.UnitID)
	return daftar(o, err, "blok")
}

func (s *AdminMasterService) DaftarTph(ctx context.Context, k *auth.Klaim, blokID int64) ([]model.BarisMaster, error) {
	o, err := s.repo.DaftarTph(ctx, k.UnitID, blokID)
	return daftar(o, err, "TPH")
}

func (s *AdminMasterService) DaftarPemanen(ctx context.Context, k *auth.Klaim) ([]model.BarisMaster, error) {
	o, err := s.repo.DaftarPemanen(ctx, k.UnitID)
	return daftar(o, err, "pemanen")
}

func validasiDivisi(in model.DivisiInput) error {
	if err := kodeSah("kode", in.Kode, 20); err != nil {
		return err
	}
	return teksSah("nama", in.Nama, 100)
}

func (s *AdminMasterService) BuatDivisi(ctx context.Context, k *auth.Klaim, in model.DivisiInput) (int64, error) {
	if err := validasiDivisi(in); err != nil {
		return 0, tidakValid(err)
	}
	id, err := s.repo.BuatDivisi(ctx, k.UnitID, k.UserID, in)
	if errors.Is(err, repository.ErrAsistenTidakSah) {
		return 0, apperror.Unprocessable(err.Error())
	}
	return id, galatRepo(err, "Divisi")
}

func (s *AdminMasterService) UbahDivisi(ctx context.Context, k *auth.Klaim, id int64, in model.DivisiInput) error {
	if err := validasiDivisi(in); err != nil {
		return tidakValid(err)
	}
	err := s.repo.UbahDivisi(ctx, k.UnitID, k.UserID, id, in)
	if errors.Is(err, repository.ErrAsistenTidakSah) {
		return apperror.Unprocessable(err.Error())
	}
	return galatRepo(err, "Divisi")
}

func (s *AdminMasterService) HapusDivisi(ctx context.Context, k *auth.Klaim, id int64) error {
	return galatRepo(s.repo.HapusDivisi(ctx, k.UnitID, k.UserID, id), "Divisi")
}

func validasiBlok(in model.BlokInput) error {
	if err := kodeSah("kode", in.Kode, 20); err != nil {
		return err
	}
	if in.ErpOid <= 0 {
		return errors.New("erp_oid (OID blok di ERP) wajib diisi")
	}
	if in.Nama != nil && len(*in.Nama) > 100 {
		return errors.New("nama maksimal 100 karakter")
	}
	if in.LuasHa != nil && *in.LuasHa < 0 {
		return errors.New("luas_ha tidak boleh negatif")
	}
	if in.Sph != nil && *in.Sph < 0 {
		return errors.New("sph tidak boleh negatif")
	}
	if in.TahunTanam != nil && (*in.TahunTanam < 1900 || *in.TahunTanam > 2100) {
		return errors.New("tahun_tanam di luar rentang 1900 sampai 2100")
	}
	return nil
}

func (s *AdminMasterService) BuatBlok(ctx context.Context, k *auth.Klaim, in model.BlokInput) (int64, error) {
	if err := validasiBlok(in); err != nil {
		return 0, tidakValid(err)
	}
	id, err := s.repo.BuatBlok(ctx, k.UnitID, k.UserID, in)
	return id, galatRepo(err, "Blok")
}

// UbahBlok mengembalikan jumlah QR TPH yang berubah (kode blok ikut membentuk isi QR).
func (s *AdminMasterService) UbahBlok(ctx context.Context, k *auth.Klaim, id int64, in model.BlokInput) (int, error) {
	if err := validasiBlok(in); err != nil {
		return 0, tidakValid(err)
	}
	n, err := s.repo.UbahBlok(ctx, k.UnitID, k.UserID, id, in)
	return n, galatRepo(err, "Blok")
}

func (s *AdminMasterService) HapusBlok(ctx context.Context, k *auth.Klaim, id int64) error {
	return galatRepo(s.repo.HapusBlok(ctx, k.UnitID, k.UserID, id), "Blok")
}

func validasiTph(in model.TphInput) error {
	if in.BlokID <= 0 {
		return errors.New("blok_id wajib diisi")
	}
	if err := kodeSah("no_tph", in.NoTph, 10); err != nil {
		return err
	}
	if (in.Lat == nil) != (in.Lon == nil) {
		return errors.New("lat dan lon harus diisi bersamaan atau dikosongkan bersamaan")
	}
	if in.Lat != nil && (*in.Lat < -90 || *in.Lat > 90) {
		return errors.New("lat di luar rentang -90 sampai 90")
	}
	if in.Lon != nil && (*in.Lon < -180 || *in.Lon > 180) {
		return errors.New("lon di luar rentang -180 sampai 180")
	}
	return nil
}

func (s *AdminMasterService) BuatTph(ctx context.Context, k *auth.Klaim, in model.TphInput) (int64, error) {
	if err := validasiTph(in); err != nil {
		return 0, tidakValid(err)
	}
	id, err := s.repo.BuatTph(ctx, k.UnitID, k.UserID, in)
	return id, galatRepo(err, "TPH")
}

func (s *AdminMasterService) BuatTphMassal(ctx context.Context, k *auth.Klaim, in model.TphMassalInput) ([]int64, error) {
	if in.BlokID <= 0 {
		return nil, tidakValid(errors.New("blok_id wajib diisi"))
	}
	if in.Dari < 1 || in.Sampai < in.Dari {
		return nil, tidakValid(errors.New("rentang nomor tidak valid (dari minimal 1 dan sampai tidak kurang dari dari)"))
	}
	if in.Sampai-in.Dari+1 > maksTphMassal {
		return nil, tidakValid(fmt.Errorf("maksimal %d TPH sekali buat", maksTphMassal))
	}
	if in.Lebar < 1 || in.Lebar > 4 {
		return nil, tidakValid(errors.New("lebar nomor harus 1 sampai 4"))
	}
	if len(fmt.Sprint(in.Sampai)) > in.Lebar {
		return nil, tidakValid(errors.New("lebar nomor terlalu kecil untuk nomor terbesar"))
	}
	ids, err := s.repo.BuatTphMassal(ctx, k.UnitID, k.UserID, in)
	return ids, galatRepo(err, "TPH")
}

func (s *AdminMasterService) UbahTph(ctx context.Context, k *auth.Klaim, id int64, in model.TphInput) (bool, error) {
	if err := validasiTph(in); err != nil {
		return false, tidakValid(err)
	}
	qr, err := s.repo.UbahTph(ctx, k.UnitID, k.UserID, id, in)
	return qr, galatRepo(err, "TPH")
}

func (s *AdminMasterService) HapusTph(ctx context.Context, k *auth.Klaim, id int64) error {
	return galatRepo(s.repo.HapusTph(ctx, k.UnitID, k.UserID, id), "TPH")
}

func validasiPemanen(in model.PemanenAdminInput) error {
	if in.ErpHrmbCode <= 0 {
		return errors.New("erp_hrmb_code (kode pemanen di ERP) wajib diisi")
	}
	if in.Kode != nil && len(*in.Kode) > 20 {
		return errors.New("kode maksimal 20 karakter")
	}
	return teksSah("nama", in.Nama, 100)
}

func (s *AdminMasterService) BuatPemanen(ctx context.Context, k *auth.Klaim, in model.PemanenAdminInput) (int64, error) {
	if err := validasiPemanen(in); err != nil {
		return 0, tidakValid(err)
	}
	id, err := s.repo.BuatPemanen(ctx, k.UnitID, k.UserID, in)
	return id, galatRepo(err, "Pemanen")
}

func (s *AdminMasterService) UbahPemanen(ctx context.Context, k *auth.Klaim, id int64, in model.PemanenAdminInput) error {
	if err := validasiPemanen(in); err != nil {
		return tidakValid(err)
	}
	return galatRepo(s.repo.UbahPemanen(ctx, k.UnitID, k.UserID, id, in), "Pemanen")
}

func (s *AdminMasterService) HapusPemanen(ctx context.Context, k *auth.Klaim, id int64) error {
	return galatRepo(s.repo.HapusPemanen(ctx, k.UnitID, k.UserID, id), "Pemanen")
}

// ---- Pengguna, perangkat, laporan ----------------------------------------------------------------

type AdminService struct {
	repo     AdminRepo
	hash     func(string) (string, error)
	sekarang func() time.Time
}

func NewAdminService(repo AdminRepo) *AdminService {
	return &AdminService{repo: repo, hash: auth.HashPassword, sekarang: time.Now}
}

func (s *AdminService) DaftarRole(ctx context.Context) ([]model.RoleAdmin, error) {
	o, err := s.repo.DaftarRole(ctx)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca role", err)
	}
	return o, nil
}

func (s *AdminService) DaftarPengguna(ctx context.Context, k *auth.Klaim) ([]model.PenggunaAdmin, error) {
	o, err := s.repo.DaftarPengguna(ctx, k.UnitID)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca pengguna", err)
	}
	return o, nil
}

func validasiPengguna(in model.PenggunaInput, baru bool) error {
	if baru && !reUsername.MatchString(in.Username) {
		return errors.New("username 3 sampai 50 karakter: huruf kecil, angka, titik, garis bawah, atau strip")
	}
	if err := teksSah("nama", in.Nama, 100); err != nil {
		return err
	}
	if len(in.Role) == 0 {
		return errors.New("pilih minimal satu role")
	}
	if len(in.Role) > 5 {
		return errors.New("maksimal 5 role")
	}
	if in.MandorHrmbCode != nil && *in.MandorHrmbCode <= 0 {
		return errors.New("mandor_hrmb_code harus lebih dari 0")
	}
	if in.AfdelingKode != nil {
		if err := teksSah("afdeling_kode", *in.AfdelingKode, 20); err != nil && *in.AfdelingKode != "" {
			return err
		}
	}
	return nil
}

func validasiPassword(p string) error {
	if len(p) < panjangPasswordMin {
		return fmt.Errorf("password minimal %d karakter", panjangPasswordMin)
	}
	if len(p) > 72 {
		return errors.New("password maksimal 72 karakter")
	}
	return nil
}

func (s *AdminService) BuatPengguna(ctx context.Context, k *auth.Klaim, in model.PenggunaInput) (int64, error) {
	in.Username = strings.ToLower(strings.TrimSpace(in.Username))
	if err := validasiPengguna(in, true); err != nil {
		return 0, tidakValid(err)
	}
	if err := validasiPassword(in.Password); err != nil {
		return 0, tidakValid(err)
	}
	h, err := s.hash(in.Password)
	if err != nil {
		return 0, apperror.Internal("Gagal mengamankan password", err)
	}
	id, err := s.repo.BuatPengguna(ctx, k.UnitID, k.UserID, in, h)
	if errors.Is(err, repository.ErrDuplikat) {
		return 0, apperror.Conflict("Username sudah dipakai")
	}
	return id, galatRepo(err, "Pengguna")
}

func (s *AdminService) UbahPengguna(ctx context.Context, k *auth.Klaim, id int64, in model.PenggunaInput) error {
	if err := validasiPengguna(in, false); err != nil {
		return tidakValid(err)
	}
	if id == k.UserID && in.Aktif != nil && !*in.Aktif {
		return apperror.Unprocessable("Anda tidak bisa menonaktifkan akun sendiri")
	}
	return galatRepo(s.repo.UbahPengguna(ctx, k.UnitID, k.UserID, id, in), "Pengguna")
}

func (s *AdminService) ResetPassword(ctx context.Context, k *auth.Klaim, id int64, in model.ResetPasswordInput) error {
	if err := validasiPassword(in.Password); err != nil {
		return tidakValid(err)
	}
	h, err := s.hash(in.Password)
	if err != nil {
		return apperror.Internal("Gagal mengamankan password", err)
	}
	return galatRepo(s.repo.ResetPassword(ctx, k.UnitID, k.UserID, id, h), "Pengguna")
}

func (s *AdminService) DaftarPerangkat(ctx context.Context) ([]model.PerangkatAdmin, error) {
	o, err := s.repo.DaftarPerangkat(ctx)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca perangkat", err)
	}
	return o, nil
}

func (s *AdminService) UbahPerangkat(ctx context.Context, k *auth.Klaim, deviceID string, in model.PerangkatUbahInput) error {
	if !model.ReUUID.MatchString(deviceID) {
		return tidakValid(errors.New("device_id harus UUID"))
	}
	if strings.EqualFold(deviceID, k.DeviceID) && !in.Aktif {
		return apperror.Unprocessable("Anda tidak bisa menonaktifkan perangkat yang sedang Anda pakai")
	}
	return galatRepo(s.repo.UbahPerangkat(ctx, k.UserID, strings.ToLower(deviceID), in.Aktif), "Perangkat")
}

// Ringkasan; tanggal kosong berarti hari ini menurut WIB.
func (s *AdminService) Ringkasan(ctx context.Context, k *auth.Klaim, tanggal string) (*model.RingkasanAdmin, error) {
	if tanggal == "" {
		tanggal = s.sekarang().In(wib).Format("2006-01-02")
	} else if _, err := time.Parse("2006-01-02", tanggal); err != nil {
		return nil, tidakValid(errors.New("tanggal harus YYYY-MM-DD"))
	}
	o, err := s.repo.Ringkasan(ctx, k.UnitID, tanggal)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca ringkasan", err)
	}
	return o, nil
}

func halaman(h, per int) (int, int) {
	if h < 1 {
		h = 1
	}
	if per < 1 {
		per = perHalamanBawaan
	}
	if per > perHalamanMaks {
		per = perHalamanMaks
	}
	return h, per
}

func (s *AdminService) DaftarPanen(ctx context.Context, k *auth.Klaim, f model.FilterPanen) (*model.HalamanPanen, error) {
	for _, t := range []string{f.Dari, f.Sampai} {
		if t != "" && !reTanggal.MatchString(t) {
			return nil, tidakValid(errors.New("tanggal harus YYYY-MM-DD"))
		}
	}
	if f.StatusEr != "" && !statusErp[f.StatusEr] {
		return nil, tidakValid(errors.New("status_erp harus BARU, DIBACA, DIIMPOR, atau DITOLAK"))
	}
	f.Halaman, f.PerHal = halaman(f.Halaman, f.PerHal)
	o, err := s.repo.DaftarPanen(ctx, k.UnitID, f)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca catatan panen", err)
	}
	return o, nil
}

func (s *AdminService) DetailPanen(ctx context.Context, k *auth.Klaim, id string) (*model.PanenDetail, error) {
	if !model.ReUUID.MatchString(id) {
		return nil, tidakValid(errors.New("id harus UUID"))
	}
	o, err := s.repo.DetailPanen(ctx, k.UnitID, strings.ToLower(id))
	return o, galatRepo(err, "Catatan panen")
}

// PathFoto mengembalikan path berkas foto catatan di unit pengguna.
func (s *AdminService) PathFoto(ctx context.Context, k *auth.Klaim, fotoID string) (string, error) {
	if !model.ReUUID.MatchString(fotoID) {
		return "", tidakValid(errors.New("id foto harus UUID"))
	}
	p, err := s.repo.PathFoto(ctx, k.UnitID, strings.ToLower(fotoID))
	return p, galatRepo(err, "Foto")
}

func (s *AdminService) DaftarAudit(ctx context.Context, f model.FilterAudit) (*model.HalamanAudit, error) {
	for _, t := range []string{f.Dari, f.Sampai} {
		if t != "" && !reTanggal.MatchString(t) {
			return nil, tidakValid(errors.New("tanggal harus YYYY-MM-DD"))
		}
	}
	f.Halaman, f.PerHal = halaman(f.Halaman, f.PerHal)
	o, err := s.repo.DaftarAudit(ctx, f)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca audit", err)
	}
	return o, nil
}

// BerkasHilang: baris foto ada di database tetapi berkasnya tidak ada di penyimpanan.
func BerkasHilang() error { return apperror.NotFound("Berkas foto tidak ditemukan di penyimpanan") }

// Produktivitas menyusun ranking produktivitas pemanen satu hari; tanggal kosong berarti hari ini menurut WIB.
// Hanya membaca catatan yang sudah ada: tidak menghitung premi.
func (s *AdminService) Produktivitas(ctx context.Context, k *auth.Klaim, tanggal string) (*model.ProduktivitasHarian, error) {
	if tanggal == "" {
		tanggal = s.sekarang().In(wib).Format("2006-01-02")
	} else if _, err := time.Parse("2006-01-02", tanggal); err != nil {
		return nil, tidakValid(errors.New("tanggal harus YYYY-MM-DD"))
	}
	baris, err := s.repo.Produktivitas(ctx, k.UnitID, tanggal)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca produktivitas pemanen", err)
	}
	return susunProduktivitas(tanggal, baris), nil
}

// susunProduktivitas mengurutkan (janjang terbanyak, lalu nama), memberi peringkat (janjang sama berarti peringkat
// sama), dan menghitung total, rata-rata, serta persentase matang yang dibulatkan satu desimal.
func susunProduktivitas(tanggal string, baris []model.ProduktivitasPemanen) *model.ProduktivitasHarian {
	if baris == nil {
		baris = []model.ProduktivitasPemanen{}
	}
	sort.SliceStable(baris, func(i, j int) bool {
		if baris[i].Janjang != baris[j].Janjang {
			return baris[i].Janjang > baris[j].Janjang
		}
		if baris[i].Nama != baris[j].Nama {
			return baris[i].Nama < baris[j].Nama
		}
		return baris[i].HrmbCode < baris[j].HrmbCode
	})
	out := &model.ProduktivitasHarian{Tanggal: tanggal, JumlahPemanen: int64(len(baris)), Pemanen: baris}
	for i := range baris {
		b := &baris[i]
		out.TotalJanjang += b.Janjang
		if i > 0 && b.Janjang == baris[i-1].Janjang {
			b.Peringkat = baris[i-1].Peringkat
		} else {
			b.Peringkat = i + 1
		}
		if b.Janjang > 0 {
			b.PersenMatang = bulat1(float64(b.JanjangMatang) * 100 / float64(b.Janjang))
		}
		b.BrondolanKg = bulat1(b.BrondolanKg)
	}
	if len(baris) > 0 {
		out.RataJanjang = bulat1(float64(out.TotalJanjang) / float64(len(baris)))
	}
	return out
}

func bulat1(v float64) float64 { return math.Round(v*10) / 10 }
