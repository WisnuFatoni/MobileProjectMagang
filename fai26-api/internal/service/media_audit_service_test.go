package service

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"testing"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

const idFoto = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"

type rakitanMedia struct {
	svc   *MediaService
	panen *fakePanen
	foto  *fakeFoto
	store *fakeStore
	users *fakeUsers
}

func rakitMedia() rakitanMedia {
	users := &fakeUsers{perms: map[int64][]string{10: {"panen.kirim"}, 20: {"panen.koreksi"}}, aktif: map[int64]bool{10: true, 20: true}}
	panen := newFakePanen()
	panen.pemilik[idPanen] = 10
	foto, store := &fakeFoto{ada: map[string]bool{}}, &fakeStore{}
	return rakitanMedia{NewMediaService(panen, foto, store, NewAksesService(users)), panen, foto, store, users}
}

func jpegUji() ([]byte, string) {
	b := append([]byte{0xFF, 0xD8, 0xFF, 0xE0}, bytes.Repeat([]byte{0x42}, 500)...)
	h := sha256.Sum256(b)
	return b, hex.EncodeToString(h[:])
}

func TestMediaTersimpanDanIdempoten(t *testing.T) {
	r := rakitMedia()
	isi, sha := jpegUji()
	req := MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha}
	status, err := r.svc.Terima(context.Background(), klaimKrani, req, bytes.NewReader(isi))
	if err != nil || status != StatusDiterima {
		t.Fatalf("status=%q err=%v", status, err)
	}
	if len(r.store.berkas) != 1 || len(r.foto.tersimpan) != 1 || r.foto.tersimpan[0].Jenis != "DETEKSI" {
		t.Fatalf("berkas/baris foto salah: %+v", r.foto.tersimpan)
	}
	r.foto.ada[idFoto] = true
	status, err = r.svc.Terima(context.Background(), klaimKrani, req, bytes.NewReader(isi))
	if err != nil || status != StatusSudahAda || len(r.foto.tersimpan) != 1 {
		t.Fatalf("kirim ulang: status=%q err=%v", status, err)
	}
}

func TestMediaDitolak(t *testing.T) {
	isi, sha := jpegUji()
	teks := []byte("bukan gambar")
	hTeks := sha256.Sum256(teks)
	besar := append([]byte{0xFF, 0xD8, 0xFF}, bytes.Repeat([]byte{1}, MaksFotoByte)...)
	hBesar := sha256.Sum256(besar)

	kasus := []struct {
		nama  string
		ubah  func(r rakitanMedia)
		req   MediaRequest
		isi   []byte
		klaim *auth.Klaim
		kode  int
	}{
		{"checksum salah", nil, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha[:63] + "0"}, isi, klaimKrani, 422},
		{"bukan JPEG", nil, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: hex.EncodeToString(hTeks[:])}, teks, klaimKrani, 415},
		{"terlalu besar", nil, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: hex.EncodeToString(hBesar[:])}, besar, klaimKrani, 413},
		{"kosong", nil, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha}, nil, klaimKrani, 400},
		{"id buruk", nil, MediaRequest{PanenID: "x", FotoID: idFoto, SHA256: sha}, isi, klaimKrani, 400},
		{"jenis aneh", nil, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha, Jenis: "VIDEO"}, isi, klaimKrani, 400},
		{"catatan belum ada", func(r rakitanMedia) { delete(r.panen.pemilik, idPanen) }, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha}, isi, klaimKrani, 404},
		{"bukan milik sendiri", func(r rakitanMedia) { r.panen.pemilik[idPanen] = 99 }, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha}, isi, klaimKrani, 403},
	}
	for _, k := range kasus {
		t.Run(k.nama, func(t *testing.T) {
			r := rakitMedia()
			if k.ubah != nil {
				k.ubah(r)
			}
			_, err := r.svc.Terima(context.Background(), k.klaim, k.req, bytes.NewReader(k.isi))
			if statusHTTP(err) != k.kode {
				t.Fatalf("harapan %d, dapat %v", k.kode, err)
			}
			if len(r.store.berkas) != 0 {
				t.Fatal("berkas tidak boleh tersimpan")
			}
		})
	}
}

func TestMediaMilikOrangLainBolehBagiYangPunyaKoreksi(t *testing.T) {
	r := rakitMedia()
	r.panen.pemilik[idPanen] = 10
	isi, sha := jpegUji()
	mandor := &auth.Klaim{UserID: 20, UnitID: 1, DeviceID: devID}
	if _, err := r.svc.Terima(context.Background(), mandor, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha}, bytes.NewReader(isi)); err != nil {
		t.Fatal(err)
	}
}

func TestMediaBerkasDihapusBilaCatatDatabaseGagal(t *testing.T) {
	r := rakitMedia()
	r.foto.errSimpan = errAneh
	isi, sha := jpegUji()
	_, err := r.svc.Terima(context.Background(), klaimKrani, MediaRequest{PanenID: idPanen, FotoID: idFoto, SHA256: sha}, bytes.NewReader(isi))
	if statusHTTP(err) != 500 {
		t.Fatalf("harapan 500, dapat %v", err)
	}
	if len(r.store.berkas) != 0 || len(r.store.dihapus) != 1 {
		t.Fatal("berkas yatim harus dihapus")
	}
}

func TestAuditBatch(t *testing.T) {
	a := &fakeAudit{}
	svc := NewAuditService(a)
	satu := func(id string) model.AuditInput {
		return model.AuditInput{AuditID: id, Tabel: "panen_header", BarisID: idPanen, Aksi: "BUAT", Waktu: "2026-11-10T01:00:00Z"}
	}
	id1, id2 := "ffffffff-ffff-4fff-8fff-ffffffffff01", "ffffffff-ffff-4fff-8fff-ffffffffff02"

	n, lewat, err := svc.TerimaBatch(context.Background(), klaimKrani, []model.AuditInput{satu(id1), satu(id2)}, "")
	if err != nil || n != 2 || lewat != 0 {
		t.Fatalf("n=%d lewat=%d err=%v", n, lewat, err)
	}
	n, lewat, err = svc.TerimaBatch(context.Background(), klaimKrani, []model.AuditInput{satu(id1), satu(id2)}, "")
	if err != nil || n != 0 || lewat != 2 {
		t.Fatalf("kirim ulang: n=%d lewat=%d err=%v", n, lewat, err)
	}
	if _, _, err := svc.TerimaBatch(context.Background(), klaimKrani, []model.AuditInput{satu("bukan-uuid")}, ""); statusHTTP(err) != 422 {
		t.Fatalf("uuid buruk: %v", err)
	}
	buruk := satu(id1)
	buruk.Waktu = "kemarin"
	if _, _, err := svc.TerimaBatch(context.Background(), klaimKrani, []model.AuditInput{buruk}, ""); statusHTTP(err) != 422 {
		t.Fatalf("waktu buruk: %v", err)
	}
	banyak := make([]model.AuditInput, MaksBatchAudit+1)
	for i := range banyak {
		banyak[i] = satu(fmt.Sprintf("ffffffff-ffff-4fff-8fff-%012d", i))
	}
	if _, _, err := svc.TerimaBatch(context.Background(), klaimKrani, banyak, ""); statusHTTP(err) != 413 {
		t.Fatalf("terlalu banyak: %v", err)
	}
}
