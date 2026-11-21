package service

import (
	"bytes"
	"context"
	"errors"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"fai26-api/internal/auth"
	"fai26-api/internal/repository"
)

type fakeFotoRepo struct {
	versi    *int64
	ada      bool
	errAtur  error
	diatur   int
	hapusTes bool
}

func (f *fakeFotoRepo) Versi(context.Context, int64) (*int64, error) {
	if !f.ada {
		return nil, repository.ErrNotFound
	}
	return f.versi, nil
}

func (f *fakeFotoRepo) Atur(_ context.Context, _ int64, v *int64) error {
	f.diatur++
	if f.errAtur != nil {
		return f.errAtur
	}
	f.versi = v
	f.hapusTes = v == nil
	return nil
}

type fakeFotoStore struct {
	isi    map[string][]byte
	hapus  []string
	errSim error
}

func (f *fakeFotoStore) Simpan(folder, nama string, isi []byte) (string, error) {
	if f.errSim != nil {
		return "", f.errSim
	}
	if f.isi == nil {
		f.isi = map[string][]byte{}
	}
	f.isi[folder+"/"+nama] = isi
	return folder + "/" + nama, nil
}
func (f *fakeFotoStore) Hapus(p string) error { f.hapus = append(f.hapus, p); return nil }
func (f *fakeFotoStore) Buka(p string) (io.ReadCloser, error) {
	b, ok := f.isi[p]
	if !ok {
		return nil, errors.New("tidak ada")
	}
	return io.NopCloser(bytes.NewReader(b)), nil
}

// gambar membuat gambar uji w x h: separuh kiri merah, separuh kanan biru.
func gambar(w, h int) image.Image {
	im := image.NewRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			if x < w/2 {
				im.Set(x, y, color.RGBA{220, 20, 20, 255})
			} else {
				im.Set(x, y, color.RGBA{20, 20, 220, 255})
			}
		}
	}
	return im
}

func jpegBytes(t *testing.T, w, h int) []byte {
	t.Helper()
	var b bytes.Buffer
	if err := jpeg.Encode(&b, gambar(w, h), &jpeg.Options{Quality: 90}); err != nil {
		t.Fatal(err)
	}
	return b.Bytes()
}

func TestProsesFotoProfilMemotongPersegiDariTengahDanMengecilkan(t *testing.T) {
	// Lanskap 1200 x 600: persegi tengah 600 x 600 (kiri merah, kanan biru), hasil 384 x 384.
	out, err := ProsesFotoProfil(jpegBytes(t, 1200, 600))
	if err != nil {
		t.Fatal(err)
	}
	im, err := jpeg.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatal(err)
	}
	if b := im.Bounds(); b.Dx() != SisiFotoProfil || b.Dy() != SisiFotoProfil {
		t.Fatalf("ukuran hasil: %v", b)
	}
	kr, _, kb, _ := im.At(40, 192).RGBA()
	if kr>>8 < 150 || kb>>8 > 100 {
		t.Fatalf("sisi kiri harus merah: r=%d b=%d", kr>>8, kb>>8)
	}
	_, _, br, _ := im.At(340, 192).RGBA()
	if br>>8 < 150 {
		t.Fatalf("sisi kanan harus biru: b=%d", br>>8)
	}
	if len(out) > 120*1024 {
		t.Fatalf("hasil terlalu besar: %d byte", len(out))
	}
}

func TestProsesFotoProfilMenerimaPngDanMenolakYangBukanGambarAtauKecil(t *testing.T) {
	var p bytes.Buffer
	if err := png.Encode(&p, gambar(300, 300)); err != nil {
		t.Fatal(err)
	}
	if _, err := ProsesFotoProfil(p.Bytes()); err != nil {
		t.Fatalf("PNG harus diterima: %v", err)
	}
	if _, err := ProsesFotoProfil([]byte("ini bukan gambar")); err == nil {
		t.Fatal("berkas bukan gambar harus ditolak")
	}
	if _, err := ProsesFotoProfil(jpegBytes(t, 40, 40)); err == nil || !strings.Contains(err.Error(), "terlalu kecil") {
		t.Fatalf("gambar kecil harus ditolak: %v", err)
	}
	// Berkas dipotong di tengah: kepala valid tetapi isi rusak.
	rusak := jpegBytes(t, 500, 500)
	if _, err := ProsesFotoProfil(rusak[:len(rusak)/3]); err == nil {
		t.Fatal("gambar rusak harus ditolak")
	}
}

func TestFotoProfilSimpanBacaHapus(t *testing.T) {
	repo := &fakeFotoRepo{ada: true}
	store := &fakeFotoStore{}
	s := NewFotoProfilService(repo, store)
	s.sekarang = func() time.Time { return time.UnixMilli(1_700_000_000_123) }
	k := &auth.Klaim{UserID: 7}

	versi, err := s.Simpan(context.Background(), k, bytes.NewReader(jpegBytes(t, 800, 800)))
	if err != nil || versi != 1_700_000_000_123 {
		t.Fatalf("simpan: %d %v", versi, err)
	}
	if _, ok := store.isi["profil/7.jpg"]; !ok || repo.versi == nil || *repo.versi != versi {
		t.Fatalf("berkas dan versi harus tersimpan: %v %v", store.isi, repo.versi)
	}
	f, v, err := s.Baca(context.Background(), 7)
	if err != nil || v != versi {
		t.Fatalf("baca: %v %d", err, v)
	}
	f.Close()

	if err := s.Hapus(context.Background(), k); err != nil {
		t.Fatal(err)
	}
	if !repo.hapusTes || len(store.hapus) != 1 || store.hapus[0] != "profil/7.jpg" {
		t.Fatalf("hapus: %+v %v", repo, store.hapus)
	}
	if _, _, err := s.Baca(context.Background(), 7); statusGalat(t, err) != http.StatusNotFound {
		t.Fatalf("setelah dihapus harus 404: %v", err)
	}
}

func TestFotoProfilInputTidakSahDitolakTanpaMenyentuhPenyimpanan(t *testing.T) {
	repo := &fakeFotoRepo{ada: true}
	store := &fakeFotoStore{}
	s := NewFotoProfilService(repo, store)
	k := &auth.Klaim{UserID: 7}
	kasus := map[string]io.Reader{
		"kosong":        bytes.NewReader(nil),
		"bukan gambar":  strings.NewReader("halo"),
		"terlalu besar": io.LimitReader(zeroReader{}, MaksFotoProfilByte+10),
	}
	for nama, r := range kasus {
		if _, err := s.Simpan(context.Background(), k, r); statusGalat(t, err) != http.StatusUnprocessableEntity {
			t.Errorf("%s: harus 422, dapat %v", nama, err)
		}
	}
	if repo.diatur != 0 || len(store.isi) != 0 {
		t.Fatalf("tidak boleh ada yang tersimpan: %+v %v", repo, store.isi)
	}
}

type zeroReader struct{}

func (zeroReader) Read(p []byte) (int, error) {
	for i := range p {
		p[i] = 0
	}
	return len(p), nil
}
