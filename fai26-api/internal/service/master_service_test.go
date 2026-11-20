package service

import (
	"context"
	"testing"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
)

type fakeMaster struct {
	versi      string
	isi        *model.IsiMaster
	diambil    int
	unitDibaca int64
	errVersi   error
	errAmbil   error
	lingkup    model.LingkupMaster
}

func (f *fakeMaster) Lingkup(context.Context, int64) (model.LingkupMaster, error) {
	return f.lingkup, nil
}
func (f *fakeMaster) Versi(_ context.Context, unitID int64, _ model.LingkupMaster) (string, error) {
	f.unitDibaca = unitID
	return f.versi, f.errVersi
}
func (f *fakeMaster) Ambil(_ context.Context, unitID int64, _ model.LingkupMaster) (*model.IsiMaster, error) {
	f.diambil++
	f.unitDibaca = unitID
	return f.isi, f.errAmbil
}

func masterUji() *fakeMaster {
	return &fakeMaster{
		versi: "m-abc",
		isi: &model.IsiMaster{
			Unit: model.BarisMaster{"unit_id": int64(1), "kode": "KP_STL"},
			Blok: []model.BarisMaster{{"blok_id": int64(1), "kode": "A12"}},
			Tph:  []model.BarisMaster{{"tph_id": int64(1), "qr_kode": "FAI26|TPH|1|KP_STL|A12|01"}},
			User: []model.MasterUser{{UserID: 1, Username: "krani1", Role: []string{"KRANI"}}},
			RolePermission: []model.RoleIzin{
				{Role: "KRANI", Permission: []string{"panen.buat", "panen.kirim"}},
				{Role: "MANDOR", Permission: []string{"panen.setujui"}},
			},
		},
	}
}

func TestMasterBelumPunyaVersiMengunduhSemua(t *testing.T) {
	f := masterUji()
	m, err := NewMasterService(f).Ambil(context.Background(), klaimKrani, "")
	if err != nil {
		t.Fatal(err)
	}
	if !m.Berubah || m.Versi != "m-abc" || len(m.Blok) != 1 || len(m.User) != 1 || m.Unit["kode"] != "KP_STL" {
		t.Fatalf("%+v", m)
	}
	if len(m.RolePermission) != 2 || m.RolePermission[0].Role != "KRANI" || len(m.RolePermission[0].Permission) != 2 {
		t.Fatalf("hak role harus ikut diteruskan: %+v", m.RolePermission)
	}
	if f.unitDibaca != klaimKrani.UnitID {
		t.Fatalf("harus membaca unit milik akun (%d), dapat %d", klaimKrani.UnitID, f.unitDibaca)
	}
}

func TestMasterVersiSamaTidakMengirimHakRole(t *testing.T) {
	m, err := NewMasterService(masterUji()).Ambil(context.Background(), klaimKrani, "m-abc")
	if err != nil {
		t.Fatal(err)
	}
	if m.Berubah || m.RolePermission != nil {
		t.Fatalf("versi sama: tidak ada isi yang dikirim: %+v", m)
	}
}

func TestMasterVersiSamaTidakMengunduhUlang(t *testing.T) {
	f := masterUji()
	m, err := NewMasterService(f).Ambil(context.Background(), klaimKrani, "m-abc")
	if err != nil {
		t.Fatal(err)
	}
	if m.Berubah || m.Versi != "m-abc" || len(m.Blok) != 0 || len(m.User) != 0 {
		t.Fatalf("seharusnya hanya versi: %+v", m)
	}
	if f.diambil != 0 {
		t.Fatal("isi master tidak boleh dibaca bila versi sama")
	}
}

func TestMasterVersiLamaMengunduhIsiBaru(t *testing.T) {
	f := masterUji()
	m, err := NewMasterService(f).Ambil(context.Background(), klaimKrani, "m-lama")
	if err != nil || !m.Berubah || f.diambil != 1 {
		t.Fatalf("berubah=%v diambil=%d err=%v", m != nil && m.Berubah, f.diambil, err)
	}
}

func TestMasterGalatDatabase(t *testing.T) {
	f := masterUji()
	f.errVersi = errAneh
	if _, err := NewMasterService(f).Ambil(context.Background(), klaimKrani, ""); statusHTTP(err) != 500 {
		t.Fatalf("galat versi: %v", err)
	}
	f = masterUji()
	f.errAmbil = errAneh
	if _, err := NewMasterService(f).Ambil(context.Background(), klaimKrani, ""); statusHTTP(err) != 500 {
		t.Fatalf("galat ambil: %v", err)
	}
	_ = auth.Klaim{}
}
