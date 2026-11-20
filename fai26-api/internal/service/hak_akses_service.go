package service

import (
	"context"
	"errors"
	"regexp"
	"strings"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

type HakAksesRepo interface {
	Daftar(ctx context.Context) (*model.HakAkses, error)
	Ubah(ctx context.Context, aktorID, roleID int64, kode []string) error
	Buat(ctx context.Context, aktorID int64, in model.RoleInput) (int64, error)
	UbahProfil(ctx context.Context, aktorID, roleID int64, nama, deskripsi *string) error
	Hapus(ctx context.Context, aktorID, roleID int64) error
}

type HakAksesService struct{ repo HakAksesRepo }

func NewHakAksesService(repo HakAksesRepo) *HakAksesService { return &HakAksesService{repo: repo} }

const maksIzinPerRole = 200

// reKodeRole: huruf besar, angka, garis bawah; diawali huruf (mis. KEPALA_KEBUN).
var reKodeRole = regexp.MustCompile(`^[A-Z][A-Z0-9_]{1,29}$`)

// bersihkanIzin membuang spasi dan kode ganda; menolak kode kosong atau terlalu banyak.
func bersihkanIzin(in []string) ([]string, error) {
	if in == nil {
		return nil, apperror.Unprocessable("permission wajib diisi (boleh daftar kosong)")
	}
	if len(in) > maksIzinPerRole {
		return nil, apperror.Unprocessable("terlalu banyak permission")
	}
	lihat := map[string]bool{}
	bersih := make([]string, 0, len(in))
	for _, p := range in {
		p = strings.TrimSpace(p)
		if p == "" {
			return nil, apperror.Unprocessable("kode permission tidak boleh kosong")
		}
		if !lihat[p] {
			lihat[p] = true
			bersih = append(bersih, p)
		}
	}
	return bersih, nil
}

func petaErrorRole(err error) error {
	switch {
	case err == nil:
		return nil
	case errors.Is(err, repository.ErrNotFound):
		return apperror.NotFound("Role tidak ditemukan")
	case errors.Is(err, repository.ErrReferensi):
		return apperror.Unprocessable("Ada kode permission yang tidak dikenal")
	case errors.Is(err, repository.ErrDuplikat):
		return apperror.Conflict("Kode role sudah dipakai")
	case errors.Is(err, repository.ErrMasihDipakai):
		return apperror.Conflict("Role masih dipegang akun aktif: pindahkan akun itu ke role lain dulu")
	case errors.Is(err, repository.ErrDilindungi):
		return apperror.Conflict("Perubahan ini dilindungi: role bawaan tidak bisa dihapus, dan ADMIN tidak boleh kehilangan role.kelola")
	default:
		return apperror.Internal("Gagal menyimpan role", err)
	}
}

// Buat membuat role baru beserta hak awalnya. Kode diubah ke huruf besar.
func (s *HakAksesService) Buat(ctx context.Context, k *auth.Klaim, in model.RoleInput) (int64, error) {
	in.Kode = strings.ToUpper(strings.TrimSpace(in.Kode))
	in.Nama = strings.TrimSpace(in.Nama)
	in.Deskripsi = strings.TrimSpace(in.Deskripsi)
	if !reKodeRole.MatchString(in.Kode) {
		return 0, apperror.Unprocessable("kode role: 2-30 karakter, huruf besar, angka, atau garis bawah, diawali huruf")
	}
	if n := len([]rune(in.Nama)); n < 2 || n > 60 {
		return 0, apperror.Unprocessable("nama role 2-60 karakter")
	}
	if len([]rune(in.Deskripsi)) > 200 {
		return 0, apperror.Unprocessable("deskripsi maksimal 200 karakter")
	}
	if in.Permission == nil {
		in.Permission = []string{}
	}
	izin, err := bersihkanIzin(in.Permission)
	if err != nil {
		return 0, err
	}
	in.Permission = izin
	id, err := s.repo.Buat(ctx, k.UserID, in)
	if err != nil {
		return 0, petaErrorRole(err)
	}
	return id, nil
}

// Hapus menghapus role buatan admin yang sudah tidak dipegang akun aktif mana pun.
func (s *HakAksesService) Hapus(ctx context.Context, k *auth.Klaim, roleID int64) error {
	if roleID <= 0 {
		return apperror.BadRequest("id role tidak valid")
	}
	return petaErrorRole(s.repo.Hapus(ctx, k.UserID, roleID))
}

func (s *HakAksesService) Daftar(ctx context.Context) (*model.HakAkses, error) {
	o, err := s.repo.Daftar(ctx)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca hak akses", err)
	}
	return o, nil
}

// Ubah mengganti seluruh hak satu role. Daftar boleh kosong (role tanpa hak). Kode ganda dilebur, spasi dibuang.
func (s *HakAksesService) Ubah(ctx context.Context, k *auth.Klaim, roleID int64, in model.HakAksesInput) error {
	if roleID <= 0 {
		return apperror.BadRequest("id role tidak valid")
	}
	bersih, err := bersihkanIzin(in.Permission)
	if err != nil {
		return err
	}
	var nama, desk *string
	if in.Nama != nil {
		n := strings.TrimSpace(*in.Nama)
		if l := len([]rune(n)); l < 2 || l > 60 {
			return apperror.Unprocessable("nama role 2-60 karakter")
		}
		nama = &n
	}
	if in.Deskripsi != nil {
		d := strings.TrimSpace(*in.Deskripsi)
		if len([]rune(d)) > 200 {
			return apperror.Unprocessable("deskripsi maksimal 200 karakter")
		}
		desk = &d
	}
	if err := s.repo.Ubah(ctx, k.UserID, roleID, bersih); err != nil {
		return petaErrorRole(err)
	}
	if nama != nil || desk != nil {
		return petaErrorRole(s.repo.UbahProfil(ctx, k.UserID, roleID, nama, desk))
	}
	return nil
}
