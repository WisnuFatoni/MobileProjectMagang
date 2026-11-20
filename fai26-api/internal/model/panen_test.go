package model

import (
	"testing"
	"time"
)

func TestParseWaktuMengembalikanUTC(t *testing.T) {
	for _, s := range []string{"2026-11-10T03:00:00Z", "2026-11-10T10:00:00+07:00", "2026-11-10T03:00:00.123Z", "2026-11-10T03:00:00"} {
		got, err := ParseWaktu(s)
		if err != nil {
			t.Fatalf("%s: %v", s, err)
		}
		if got.Location() != time.UTC || got.Hour() != 3 {
			t.Errorf("%s -> %v", s, got)
		}
	}
	if _, err := ParseWaktu("10 November"); err == nil {
		t.Fatal("format asing seharusnya ditolak")
	}
}

func TestStatusUntukHP(t *testing.T) {
	for erp, want := range map[string]string{"BARU": "TERKIRIM", "DIBACA": "TERKIRIM", "DIIMPOR": "DITERIMA", "DITOLAK": "DITOLAK"} {
		if got := StatusUntukHP(erp); got != want {
			t.Errorf("%s: harapan %s, dapat %s", erp, want, got)
		}
	}
}

func TestValidasiPanenSah(t *testing.T) {
	p := PanenInput{
		PanenID: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", UnitKode: "KP_STL", TanggalPanen: "2026-11-10", Asm3Oid: 1, TphKode: "01",
		StatusLokasi: "TANPA_GPS", DisetujuiUsername: "m", DisetujuiPada: "2026-11-10T03:00:00Z",
		Grading: []GradingInput{{KelasKode: "ripe", JmlFinal: 3}},
		Pemanen: []PemanenInput{{HrmbCode: 5001, JjgAlokasi: 3}},
	}
	if err := p.Validasi(); err != nil {
		t.Fatal(err)
	}
	p.GpsWaktu = ptr("kemarin")
	if err := p.Validasi(); err == nil {
		t.Fatal("gps_waktu buruk seharusnya ditolak")
	}
}

// Keputusan 18 Nov: satu TPH hanya untuk satu pemanen. Catatan harus membawa tepat satu pemanen, yang dikreditkan
// seluruh janjangnya.
func TestValidasiPanenSatuPemanen(t *testing.T) {
	dasar := func() PanenInput {
		return PanenInput{
			PanenID: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", UnitKode: "KP_STL", TanggalPanen: "2026-11-10", Asm3Oid: 1, TphKode: "01",
			StatusLokasi: "TANPA_GPS", DisetujuiUsername: "m", DisetujuiPada: "2026-11-10T03:00:00Z",
			Grading: []GradingInput{{KelasKode: "ripe", JmlFinal: 7}, {KelasKode: "unripe", JmlFinal: 3}},
			Pemanen: []PemanenInput{{HrmbCode: 5001, JjgAlokasi: 10}},
		}
	}
	p := dasar()
	if err := p.Validasi(); err != nil {
		t.Fatalf("satu pemanen dengan seluruh janjang harus sah: %v", err)
	}
	kasus := map[string]func(*PanenInput){
		"tanpa pemanen":         func(p *PanenInput) { p.Pemanen = nil },
		"daftar pemanen kosong": func(p *PanenInput) { p.Pemanen = []PemanenInput{} },
		"dua pemanen":           func(p *PanenInput) { p.Pemanen = []PemanenInput{{5001, 5}, {5002, 5}} },
		"hrmb_code nol":         func(p *PanenInput) { p.Pemanen[0].HrmbCode = 0 },
		"hrmb_code negatif":     func(p *PanenInput) { p.Pemanen[0].HrmbCode = -3 },
		"alokasi kurang":        func(p *PanenInput) { p.Pemanen[0].JjgAlokasi = 9 },
		"alokasi lebih":         func(p *PanenInput) { p.Pemanen[0].JjgAlokasi = 11 },
		"alokasi nol":           func(p *PanenInput) { p.Pemanen[0].JjgAlokasi = 0 },
		"alokasi negatif":       func(p *PanenInput) { p.Pemanen[0].JjgAlokasi = -10 },
	}
	for nama, ubah := range kasus {
		q := dasar()
		ubah(&q)
		if err := q.Validasi(); err == nil {
			t.Errorf("%s: seharusnya ditolak", nama)
		}
	}
}

func ptr[T any](v T) *T { return &v }
