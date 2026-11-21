package integration

import (
	"bytes"
	"encoding/json"
	"image"
	"image/color"
	"image/jpeg"
	"net/http"
	"testing"
)

// Foto profil: unggah (diproses jadi 384 x 384), dibaca kembali dengan ETag, versinya muncul di /auth/me, lalu dihapus.
func TestFotoProfilAlurPenuh(t *testing.T) {
	e := siapkanUji(t)
	_, kr := e.login("krani1", "krani123", devKrani)
	_, lain := e.login("mandor1", "mandor123", devIpdMandor)

	im := image.NewRGBA(image.Rect(0, 0, 900, 600))
	for y := 0; y < 600; y++ {
		for x := 0; x < 900; x++ {
			im.Set(x, y, color.RGBA{uint8(x % 255), uint8(y % 255), 90, 255})
		}
	}
	var asli bytes.Buffer
	if err := jpeg.Encode(&asli, im, &jpeg.Options{Quality: 90}); err != nil {
		t.Fatal(err)
	}

	// Bersihkan sisa uji lama.
	_, _ = e.db.Exec(`UPDATE dbo.app_user SET foto_profil_versi=NULL WHERE username IN ('krani1','mandor1')`)

	if kode, _ := e.kirim("GET", "/api/auth/foto", kr.AccessToken, nil, nil); kode != 404 {
		t.Fatalf("belum punya foto harus 404: %d", kode)
	}
	if kode, _ := e.kirim("PUT", "/api/auth/foto", "", asli.Bytes(), nil); kode != 401 {
		t.Fatalf("tanpa login harus 401: %d", kode)
	}
	if kode, _ := e.kirim("PUT", "/api/auth/foto", kr.AccessToken, []byte("bukan gambar"), nil); kode != 422 {
		t.Fatalf("bukan gambar harus 422: %d", kode)
	}

	kode, b := e.kirim("PUT", "/api/auth/foto", kr.AccessToken, asli.Bytes(), nil)
	if kode != 200 {
		t.Fatalf("unggah: %d %s", kode, b)
	}
	var hasil struct {
		FotoVersi int64 `json:"foto_versi"`
	}
	if err := json.Unmarshal(b, &hasil); err != nil || hasil.FotoVersi == 0 {
		t.Fatalf("versi: %s", b)
	}

	// Dibaca kembali: JPEG persegi 384 px.
	req, _ := http.NewRequest("GET", e.srv.URL+"/api/auth/foto", nil)
	req.Header.Set("Authorization", "Bearer "+kr.AccessToken)
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer res.Body.Close()
	if res.StatusCode != 200 || res.Header.Get("Content-Type") != "image/jpeg" || res.Header.Get("ETag") == "" {
		t.Fatalf("baca: %d %v", res.StatusCode, res.Header)
	}
	dec, err := jpeg.Decode(res.Body)
	if err != nil {
		t.Fatal(err)
	}
	if bd := dec.Bounds(); bd.Dx() != 384 || bd.Dy() != 384 {
		t.Fatalf("ukuran hasil: %v", bd)
	}
	// Dengan ETag yang sama: 304 tanpa isi.
	req2, _ := http.NewRequest("GET", e.srv.URL+"/api/auth/foto", nil)
	req2.Header.Set("Authorization", "Bearer "+kr.AccessToken)
	req2.Header.Set("If-None-Match", res.Header.Get("ETag"))
	res2, err := http.DefaultClient.Do(req2)
	if err != nil {
		t.Fatal(err)
	}
	res2.Body.Close()
	if res2.StatusCode != 304 {
		t.Fatalf("ETag sama harus 304: %d", res2.StatusCode)
	}

	// Versi muncul di profil; akun lain tidak melihat foto ini (tiap akun hanya membaca fotonya sendiri).
	_, mb := e.kirim("GET", "/api/auth/me", kr.AccessToken, nil, nil)
	var me struct {
		FotoVersi *int64 `json:"foto_versi"`
	}
	if err := json.Unmarshal(mb, &me); err != nil || me.FotoVersi == nil || *me.FotoVersi != hasil.FotoVersi {
		t.Fatalf("me: %s", mb)
	}
	if kode, _ := e.kirim("GET", "/api/auth/foto", lain.AccessToken, nil, nil); kode != 404 {
		t.Fatalf("akun lain belum punya foto, harus 404: %d", kode)
	}

	// Hapus: foto hilang dan versi kosong lagi; menghapus dua kali tetap aman.
	if kode, _ := e.kirim("DELETE", "/api/auth/foto", kr.AccessToken, nil, nil); kode != 200 {
		t.Fatalf("hapus: %d", kode)
	}
	if kode, _ := e.kirim("GET", "/api/auth/foto", kr.AccessToken, nil, nil); kode != 404 {
		t.Fatalf("setelah dihapus harus 404: %d", kode)
	}
	if kode, _ := e.kirim("DELETE", "/api/auth/foto", kr.AccessToken, nil, nil); kode != 200 {
		t.Fatalf("hapus ulang harus aman: %d", kode)
	}
	_, mb = e.kirim("GET", "/api/auth/me", kr.AccessToken, nil, nil)
	me.FotoVersi = new(int64)
	if err := json.Unmarshal(mb, &me); err != nil || me.FotoVersi != nil {
		t.Fatalf("versi harus kosong lagi: %s", mb)
	}
}
