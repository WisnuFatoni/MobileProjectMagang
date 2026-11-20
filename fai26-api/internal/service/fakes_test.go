package service

import (
	"context"
	"errors"
	"time"

	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

// Tiruan di memori untuk menguji aturan bisnis tanpa database.

type fakeUsers struct {
	user     *model.User // akun yang dikembalikan CariByUsername (nil = tidak ada)
	ids      map[string]int64
	perms    map[int64][]string
	aktif    map[int64]bool
	gagal    []int // hitungan gagal yang dicatat
	kunci    []int // durasi kunci yang dicatat
	sukses   int
	gantiPw  []string // "hash|deviceID" yang dicatat GantiPassword
	errGanti error
	errCari  error
	errPerms error
	profil   map[int64]model.Profil // profil per user_id (mandor, afdeling); bila tak ada dipakai profil dasar
}

func (f *fakeUsers) CariByUsername(_ context.Context, _ string) (*model.User, error) {
	if f.errCari != nil {
		return nil, f.errCari
	}
	if f.user == nil {
		return nil, repository.ErrNotFound
	}
	u := *f.user
	return &u, nil
}
func (f *fakeUsers) Profil(_ context.Context, id int64) (*model.Profil, error) {
	if f.user == nil {
		return nil, repository.ErrNotFound
	}
	if p, ok := f.profil[id]; ok {
		return &p, nil
	}
	return &model.Profil{UserID: id, Username: f.user.Username}, nil
}
func (f *fakeUsers) Aktif(_ context.Context, id int64) (bool, error) { return f.aktif[id], nil }
func (f *fakeUsers) IDDariUsername(_ context.Context, u string) (int64, error) {
	if id, ok := f.ids[u]; ok {
		return id, nil
	}
	return 0, repository.ErrNotFound
}
func (f *fakeUsers) CatatGagal(_ context.Context, _ int64, gagal, kunci int) error {
	f.gagal = append(f.gagal, gagal)
	f.kunci = append(f.kunci, kunci)
	return nil
}
func (f *fakeUsers) CatatLoginSukses(context.Context, int64) error { f.sukses++; return nil }
func (f *fakeUsers) Permission(_ context.Context, id int64) ([]string, error) {
	return f.perms[id], f.errPerms
}
func (f *fakeUsers) Role(context.Context, int64) ([]string, error) { return []string{"KRANI"}, nil }
func (f *fakeUsers) GantiPassword(_ context.Context, _ int64, hash, dev string) error {
	f.gantiPw = append(f.gantiPw, hash+"|"+dev)
	return f.errGanti
}

type fakeDevices struct{ nonaktif bool }

func (f *fakeDevices) Daftarkan(context.Context, model.Perangkat, int64) error {
	if f.nonaktif {
		return repository.ErrPerangkatNonaktif
	}
	return nil
}

type fakeSessions struct {
	dibuat  []string // hash refresh yang tersimpan
	sesi    *model.Sesi
	rotasi  []string
	dicabut int
}

func (f *fakeSessions) Buat(_ context.Context, _ int64, _ string, hash string, _ time.Duration, _ string) error {
	f.dibuat = append(f.dibuat, hash)
	return nil
}
func (f *fakeSessions) CariAktif(context.Context, string) (*model.Sesi, error) {
	if f.sesi == nil {
		return nil, repository.ErrNotFound
	}
	return f.sesi, nil
}
func (f *fakeSessions) Rotasi(_ context.Context, _ string, hash string, _ time.Duration) error {
	f.rotasi = append(f.rotasi, hash)
	return nil
}
func (f *fakeSessions) Cabut(context.Context, int64, string) error { f.dicabut++; return nil }

type fakeAudit struct {
	baris []model.AuditBaris
	batch []model.AuditSimpan
	err   error
}

func (f *fakeAudit) Tulis(_ context.Context, a model.AuditBaris) error {
	f.baris = append(f.baris, a)
	return nil
}
func (f *fakeAudit) TulisBatch(_ context.Context, _ int64, _, _ string, items []model.AuditSimpan) (int, error) {
	if f.err != nil {
		return 0, f.err
	}
	n := 0
	for _, it := range items {
		dobel := false
		for _, b := range f.batch {
			if b.Input.AuditID == it.Input.AuditID {
				dobel = true
			}
		}
		if !dobel {
			f.batch = append(f.batch, it)
			n++
		}
	}
	return n, nil
}

type fakePanen struct {
	tersimpan     map[string]model.PanenSimpan
	pemilik       map[string]int64
	errSimpan     error
	semuaTerakhir bool
	status        map[string]string  // status_erp per panen_id (bawaan BARU)
	master        *model.MasterPanen // nil = blok, TPH, pemanen ada dan pemanen tanpa mandor
	dobel         bool
}

func newFakePanen() *fakePanen {
	return &fakePanen{tersimpan: map[string]model.PanenSimpan{}, pemilik: map[string]int64{}, status: map[string]string{}}
}
func (f *fakePanen) Ada(_ context.Context, id string) (bool, error) {
	_, ada := f.tersimpan[id]
	return ada, nil
}
func (f *fakePanen) Cari(_ context.Context, id string) (*model.CatatanAda, error) {
	p, ada := f.tersimpan[id]
	if !ada {
		return nil, nil
	}
	st := "BARU"
	if s, ok := f.status[id]; ok {
		st = s
	}
	return &model.CatatanAda{StatusErp: st, PemilikID: p.PencatatID}, nil
}
func (f *fakePanen) CekMaster(context.Context, int64, int64, string, int64) (model.MasterPanen, error) {
	if f.master != nil {
		return *f.master, nil
	}
	return model.MasterPanen{BlokAda: true, TphAda: true, PemanenAda: true}, nil
}
func (f *fakePanen) AdaKombinasi(context.Context, string, int64, int64, string, int64, string) (bool, error) {
	return f.dobel, nil
}
func (f *fakePanen) DaftarMenunggu(context.Context, int64) ([]model.PanenMenunggu, error) {
	out := []model.PanenMenunggu{}
	for id, p := range f.tersimpan {
		if f.status[id] == "MENUNGGU" {
			out = append(out, model.PanenMenunggu{ID: id, TanggalPanen: p.Input.TanggalPanen})
		}
	}
	return out, nil
}
func (f *fakePanen) Setujui(_ context.Context, id string, _, _ int64, _, _ string) (string, error) {
	return f.pindah(id, "BARU")
}
func (f *fakePanen) Tolak(_ context.Context, id string, _, _ int64, _, _, _ string) (string, error) {
	return f.pindah(id, "DITOLAK")
}
func (f *fakePanen) pindah(id, baru string) (string, error) {
	if _, ada := f.tersimpan[id]; !ada {
		return "", repository.ErrNotFound
	}
	if f.status[id] != "MENUNGGU" {
		return f.status[id], repository.ErrStatusBerubah
	}
	f.status[id] = baru
	return baru, nil
}
func (f *fakePanen) PemilikID(_ context.Context, id string) (int64, error) {
	if p, ok := f.pemilik[id]; ok {
		return p, nil
	}
	return 0, repository.ErrNotFound
}
func (f *fakePanen) Simpan(_ context.Context, p model.PanenSimpan) error {
	if f.errSimpan != nil {
		return f.errSimpan
	}
	f.tersimpan[p.Input.PanenID] = p
	if p.Menunggu {
		f.status[p.Input.PanenID] = "MENUNGGU"
	} else if p.Ganti {
		f.status[p.Input.PanenID] = "BARU"
	}
	return nil
}
func (f *fakePanen) DaftarStatus(context.Context, int64, int64, bool, time.Time) ([]model.StatusPanen, error) {
	return nil, nil
}

func (f *fakePanen) Ringkasan(_ context.Context, _, _ int64, semua bool, hariIni string) (*model.RingkasanPanen, error) {
	f.semuaTerakhir = semua
	return &model.RingkasanPanen{Tanggal: hariIni}, nil
}

type fakeUnits struct{ ids map[string]int64 }

func (f *fakeUnits) IDDariKode(_ context.Context, k string) (int64, error) {
	if id, ok := f.ids[k]; ok {
		return id, nil
	}
	return 0, repository.ErrNotFound
}

type fakeFoto struct {
	ada       map[string]bool
	tersimpan []model.Foto
	errSimpan error
}

func (f *fakeFoto) Ada(_ context.Context, id string) (bool, error) { return f.ada[id], nil }
func (f *fakeFoto) Simpan(_ context.Context, x model.Foto) error {
	if f.errSimpan != nil {
		return f.errSimpan
	}
	f.tersimpan = append(f.tersimpan, x)
	return nil
}

type fakeStore struct {
	berkas  map[string][]byte
	dihapus []string
}

func (f *fakeStore) Simpan(folder, nama string, isi []byte) (string, error) {
	if f.berkas == nil {
		f.berkas = map[string][]byte{}
	}
	p := folder + "/" + nama
	f.berkas[p] = isi
	return p, nil
}
func (f *fakeStore) Hapus(p string) error {
	f.dihapus = append(f.dihapus, p)
	delete(f.berkas, p)
	return nil
}

var errAneh = errors.New("tabel fai_panen rusak: detail teknis rahasia")
