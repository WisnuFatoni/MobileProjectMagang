package service

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/jpeg"
	_ "image/png" // mendaftarkan decoder PNG (foto dari galeri bisa PNG)
	"io"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/repository"
)

const (
	// MaksFotoProfilByte membatasi ukuran unggahan (HP sudah mengecilkan; ini hanya pagar).
	MaksFotoProfilByte = 6 << 20
	// SisiFotoProfil adalah sisi persegi hasil akhir (piksel).
	SisiFotoProfil = 384
	// maksPikselSumber menolak gambar raksasa sebelum didekode (mencegah memori habis).
	maksPikselSumber = 40_000_000
)

type FotoProfilRepo interface {
	Versi(ctx context.Context, userID int64) (*int64, error)
	Atur(ctx context.Context, userID int64, versi *int64) error
}

type FotoProfilStore interface {
	Simpan(folder, nama string, isi []byte) (string, error)
	Hapus(path string) error
	Buka(path string) (io.ReadCloser, error)
}

// FotoProfilService mengelola foto profil milik akun sendiri. Foto selalu diproses di server: dipotong persegi dari tengah,
// dikecilkan ke 384 px, dan disimpan ulang sebagai JPEG sehingga metadata (lokasi, perangkat) dari foto asli tidak ikut tersimpan.
type FotoProfilService struct {
	repo     FotoProfilRepo
	store    FotoProfilStore
	sekarang func() time.Time
}

func NewFotoProfilService(repo FotoProfilRepo, store FotoProfilStore) *FotoProfilService {
	return &FotoProfilService{repo: repo, store: store, sekarang: time.Now}
}

func pathFoto(userID int64) (folder, nama string) { return "profil", fmt.Sprintf("%d.jpg", userID) }

// ProsesFotoProfil mendekode JPEG atau PNG, memotong persegi dari tengah, mengecilkan ke SisiFotoProfil, dan mengodekan JPEG.
func ProsesFotoProfil(isi []byte) ([]byte, error) {
	cfg, _, err := image.DecodeConfig(bytes.NewReader(isi))
	if err != nil {
		return nil, errors.New("berkas bukan gambar JPEG atau PNG yang valid")
	}
	if cfg.Width < 64 || cfg.Height < 64 {
		return nil, errors.New("gambar terlalu kecil (minimal 64 x 64 piksel)")
	}
	if cfg.Width*cfg.Height > maksPikselSumber {
		return nil, errors.New("gambar terlalu besar")
	}
	src, _, err := image.Decode(bytes.NewReader(isi))
	if err != nil {
		return nil, errors.New("gambar rusak atau tidak bisa dibaca")
	}
	b := src.Bounds()
	sisi := min(b.Dx(), b.Dy())
	x0 := b.Min.X + (b.Dx()-sisi)/2
	y0 := b.Min.Y + (b.Dy()-sisi)/2

	dst := image.NewRGBA(image.Rect(0, 0, SisiFotoProfil, SisiFotoProfil))
	skala := float64(sisi) / float64(SisiFotoProfil)
	for dy := 0; dy < SisiFotoProfil; dy++ {
		ya := y0 + int(float64(dy)*skala)
		yb := max(y0+int(float64(dy+1)*skala), ya+1)
		for dx := 0; dx < SisiFotoProfil; dx++ {
			xa := x0 + int(float64(dx)*skala)
			xb := max(x0+int(float64(dx+1)*skala), xa+1)
			// Rata-rata luas (box filter): tajam dan bebas gerigi saat dikecilkan.
			var r, g, bl, n uint64
			for y := ya; y < yb; y++ {
				for x := xa; x < xb; x++ {
					cr, cg, cb, _ := color.NRGBAModel.Convert(src.At(x, y)).RGBA()
					r += uint64(cr >> 8)
					g += uint64(cg >> 8)
					bl += uint64(cb >> 8)
					n++
				}
			}
			dst.SetRGBA(dx, dy, color.RGBA{R: uint8(r / n), G: uint8(g / n), B: uint8(bl / n), A: 255})
		}
	}
	var out bytes.Buffer
	if err := jpeg.Encode(&out, dst, &jpeg.Options{Quality: 86}); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

// Simpan memproses dan menyimpan foto milik pengguna yang login. Mengembalikan versi baru (milidetik).
func (s *FotoProfilService) Simpan(ctx context.Context, k *auth.Klaim, isi io.Reader) (int64, error) {
	data, err := io.ReadAll(io.LimitReader(isi, MaksFotoProfilByte+1))
	if err != nil {
		return 0, apperror.BadRequest("Gagal membaca foto")
	}
	if len(data) == 0 {
		return 0, apperror.Unprocessable("Foto kosong")
	}
	if len(data) > MaksFotoProfilByte {
		return 0, apperror.Unprocessable("Foto terlalu besar (maksimal 6 MB)")
	}
	jadi, err := ProsesFotoProfil(data)
	if err != nil {
		return 0, apperror.Unprocessable(err.Error())
	}
	folder, nama := pathFoto(k.UserID)
	if _, err := s.store.Simpan(folder, nama, jadi); err != nil {
		return 0, apperror.Internal("Gagal menyimpan foto", err)
	}
	versi := s.sekarang().UnixMilli()
	if err := s.repo.Atur(ctx, k.UserID, &versi); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return 0, apperror.NotFound("Akun tidak ditemukan")
		}
		return 0, apperror.Internal("Gagal mencatat foto", err)
	}
	return versi, nil
}

// Baca membuka foto milik akun userID. NotFound bila belum punya foto.
func (s *FotoProfilService) Baca(ctx context.Context, userID int64) (io.ReadCloser, int64, error) {
	v, err := s.repo.Versi(ctx, userID)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return nil, 0, apperror.NotFound("Akun tidak ditemukan")
		}
		return nil, 0, apperror.Internal("Gagal membaca foto", err)
	}
	if v == nil {
		return nil, 0, apperror.NotFound("Belum ada foto profil")
	}
	folder, nama := pathFoto(userID)
	f, err := s.store.Buka(folder + "/" + nama)
	if err != nil {
		return nil, 0, apperror.NotFound("Berkas foto tidak ditemukan")
	}
	return f, *v, nil
}

// Hapus menghapus foto milik pengguna yang login (aman bila belum ada).
func (s *FotoProfilService) Hapus(ctx context.Context, k *auth.Klaim) error {
	if err := s.repo.Atur(ctx, k.UserID, nil); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return apperror.NotFound("Akun tidak ditemukan")
		}
		return apperror.Internal("Gagal menghapus foto", err)
	}
	folder, nama := pathFoto(k.UserID)
	_ = s.store.Hapus(folder + "/" + nama) // berkas yang sudah tidak ada bukan galat
	return nil
}
