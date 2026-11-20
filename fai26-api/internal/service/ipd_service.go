package service

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"sort"
	"strconv"
	"strings"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

const (
	permIpdSetujui = "ipd.setujui"
	permIpdKelola  = "ipd.kelola"

	pesanIpdTerkunci = "Inspeksi sudah disetujui asisten, tidak bisa diubah lagi"
)

// IpdRepo adalah akses data modul IPD (Inspeksi Panen Detail).
type IpdRepo interface {
	Cari(ctx context.Context, id string) (*model.IpdAda, error)
	CekMaster(ctx context.Context, unitID int64, divisiKode string, asm3Oid, hrmb int64) (model.IpdMaster, error)
	KodeAktif(ctx context.Context) (map[string]int64, error)
	KodeMaster(ctx context.Context) ([]model.IpdKodeMaster, error)
	Mandor(ctx context.Context, unitID int64, username string) (model.IpdOrang, error)
	Simpan(ctx context.Context, p model.IpdSimpan) error
	DaftarStatus(ctx context.Context, unitID, userID int64, sejak time.Time) ([]model.IpdStatus, error)
	DaftarMenunggu(ctx context.Context, unitID, userID int64) ([]model.IpdBaris, error)
	DaftarAdmin(ctx context.Context, unitID int64, f model.FilterIpd) (*model.HalamanIpd, error)
	Baris(ctx context.Context, unitID int64, id string) (*model.IpdBaris, error)
	Pokok(ctx context.Context, id string) ([]model.IpdPokokOut, error)
	Mentah(ctx context.Context, ids []string) (map[string]*model.IpdMentah, error)
	Foto(ctx context.Context, ids []string) (map[string][]model.IpdFotoRef, error)
	Setujui(ctx context.Context, id string, unitID, userID int64, deviceID, ip string) (string, error)
	Tolak(ctx context.Context, id string, unitID, userID int64, alasan, deviceID, ip string) (string, error)
	Terakhir(ctx context.Context, unitID, hrmb, asm3Oid int64, dari, sampai, kecuali string) ([]model.IpdTerakhir, error)
	DasarLoses(ctx context.Context, unitID int64, f model.FilterLosesIpd) ([]model.IpdDasar, error)
	FotoAda(ctx context.Context, fotoID string) (bool, error)
	SimpanFoto(ctx context.Context, f model.IpdFoto) error
	PathFoto(ctx context.Context, unitID int64, fotoID string) (string, error)
	PemilikID(ctx context.Context, id string) (int64, error)
}

type IpdService struct {
	repo     IpdRepo
	units    UnitRepo
	akses    *AksesService
	store    FileStore
	sekarang func() time.Time
}

func NewIpdService(repo IpdRepo, units UnitRepo, akses *AksesService, store FileStore) *IpdService {
	return &IpdService{repo: repo, units: units, akses: akses, store: store, sekarang: time.Now}
}

// Terima menyimpan satu catatan IPD dari HP. Mengembalikan StatusDiterima untuk catatan baru atau yang diperbarui, dan
// StatusSudahAda bila ipd_id sudah ada dan kirim_ulang tidak diminta (aman untuk percobaan ulang jaringan).
//
// Aturan edit: kirim_ulang=true berlaku untuk catatan MENUNGGU (diubah sebelum asisten menyetujui) dan DITOLAK (diperbaiki).
// Catatan yang sudah DISETUJUI terkunci (423). Pengirim yang memegang ipd.setujui (asisten) langsung DISETUJUI.
func (s *IpdService) Terima(ctx context.Context, k *auth.Klaim, in model.IpdInput, ip string) (string, error) {
	in.IpdID = strings.ToLower(in.IpdID)
	in.MandorBlokUsername = strings.TrimSpace(in.MandorBlokUsername)
	in.MandorAncakUsername = strings.TrimSpace(in.MandorAncakUsername)
	if err := in.Validasi(); err != nil {
		return "", apperror.Unprocessable(err.Error())
	}
	lama, err := s.repo.Cari(ctx, in.IpdID)
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa inspeksi", err)
	}
	ganti := false
	if lama != nil {
		if !in.KirimUlang {
			return StatusSudahAda, nil
		}
		if lama.Status != "MENUNGGU" && lama.Status != "DITOLAK" {
			return "", apperror.Locked(pesanIpdTerkunci)
		}
		if lama.PemilikID != k.UserID {
			boleh, err := s.akses.Punya(ctx, k.UserID, permIpdKelola)
			if err != nil {
				return "", apperror.Internal("Gagal memeriksa hak akses", err)
			}
			if !boleh {
				return "", apperror.Forbidden("Tidak berhak mengubah inspeksi milik orang lain")
			}
		}
		ganti = true
	}

	unitID, err := s.units.IDDariKode(ctx, in.UnitKode)
	if errors.Is(err, repository.ErrNotFound) {
		return "", apperror.Unprocessable("unit_kode tidak dikenal: " + in.UnitKode)
	}
	if err != nil {
		return "", apperror.Internal("Gagal membaca unit", err)
	}
	if unitID != k.UnitID {
		return "", apperror.Forbidden("Unit inspeksi berbeda dengan unit akun")
	}

	m, err := s.repo.CekMaster(ctx, unitID, in.DivisiKode, in.Asm3Oid, int64(in.PemanenHrmbCode))
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa master", err)
	}
	switch {
	case !m.DivisiAda:
		return "", apperror.Unprocessable("Divisi tidak ada di master: " + in.DivisiKode)
	case !m.BlokAda:
		return "", apperror.Unprocessable("Blok tidak ada di master (asm3_oid " + strconv.FormatInt(in.Asm3Oid, 10) + ")")
	case !m.BlokDalamDivisi:
		return "", apperror.Unprocessable("Blok bukan milik divisi " + in.DivisiKode)
	case !m.PemanenAda:
		return "", apperror.Unprocessable("Pemanen " + strconv.Itoa(in.PemanenHrmbCode) + " tidak ada di master")
	}

	kode, err := s.repo.KodeAktif(ctx)
	if err != nil {
		return "", apperror.Internal("Gagal membaca kode temuan", err)
	}
	for _, pk := range in.Pokok {
		for _, t := range pk.Temuan {
			if _, ok := kode[t.Kode]; !ok {
				return "", apperror.Unprocessable("Kode temuan tidak dikenal: " + t.Kode)
			}
		}
	}

	mandorBlok, err := s.repo.Mandor(ctx, unitID, in.MandorBlokUsername)
	if errors.Is(err, repository.ErrNotFound) {
		return "", apperror.Unprocessable("mandor_blok_username bukan akun aktif berperan MANDOR di unit ini: " + in.MandorBlokUsername)
	}
	if err != nil {
		return "", apperror.Internal("Gagal membaca mandor", err)
	}
	mandorAncak, err := s.repo.Mandor(ctx, unitID, in.MandorAncakUsername)
	if errors.Is(err, repository.ErrNotFound) {
		return "", apperror.Unprocessable("mandor_ancak_username bukan akun aktif berperan MANDOR di unit ini: " + in.MandorAncakUsername)
	}
	if err != nil {
		return "", apperror.Internal("Gagal membaca mandor", err)
	}

	setujuSendiri, err := s.akses.Punya(ctx, k.UserID, permIpdSetujui)
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa hak penyetuju", err)
	}

	rec := model.IpdSimpan{Input: in, UnitID: unitID, DeviceID: k.DeviceID, UserID: k.UserID, DivisiID: m.DivisiID, AsistenID: m.AsistenID,
		MandorBlok: mandorBlok, MandorAncak: mandorAncak, KodeID: kode, Menunggu: !setujuSendiri, Ganti: ganti, IP: ip,
		DibuatPada: s.sekarang().UTC()}
	if in.DibuatPada != "" {
		rec.DibuatPada, _ = model.ParseWaktu(in.DibuatPada) // sudah divalidasi
	}
	if err := s.repo.Simpan(ctx, rec); err != nil {
		if errors.Is(err, repository.ErrDuplikat) {
			return "", apperror.Conflict("Nomor baris dan pokok ganda pada inspeksi ini")
		}
		if ganti && errors.Is(err, repository.ErrNotFound) { // asisten menyetujui tepat saat perubahan dikirim
			return "", apperror.Locked(pesanIpdTerkunci)
		}
		return "", apperror.Internal("Gagal menyimpan inspeksi", err)
	}
	return StatusDiterima, nil
}

// DaftarStatus mengembalikan status inspeksi milik pengguna sejak waktu tertentu.
func (s *IpdService) DaftarStatus(ctx context.Context, k *auth.Klaim, sejakTeks string) ([]model.IpdStatus, error) {
	sejak := time.Unix(0, 0).UTC()
	if sejakTeks != "" {
		t, err := model.ParseWaktu(sejakTeks)
		if err != nil {
			return nil, apperror.BadRequest("sejak: " + err.Error())
		}
		sejak = t
	}
	out, err := s.repo.DaftarStatus(ctx, k.UnitID, k.UserID, sejak)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca status inspeksi", err)
	}
	return out, nil
}

// ---- Ringkasan dan hitungan ----------------------------------------------------------------------

func persen(bagian, total int64) float64 {
	if total <= 0 {
		return 0
	}
	return bulat1(float64(bagian) * 100 / float64(total))
}

// susunRingkasan menghitung ringkasan satu inspeksi dari hitungan dasar. Semua kode master ikut muncul (nol bila tidak ada
// temuan) berurutan seperti di form.
func susunRingkasan(m *model.IpdMentah, kode []model.IpdKodeMaster) model.IpdRingkasan {
	r := model.IpdRingkasan{PerKode: []model.IpdKodeAgg{}}
	if m == nil {
		m = &model.IpdMentah{}
	}
	r.PokokDiperiksa, r.PokokBertemuan = m.PokokDiperiksa, m.PokokBertemuan
	r.PersenPokokBertemuan = persen(m.PokokBertemuan, m.PokokDiperiksa)
	for _, k := range kode {
		h := m.PerKode[k.Kode]
		r.TotalTemuan += h.Jumlah
		r.PerKode = append(r.PerKode, model.IpdKodeAgg{Kode: k.Kode, Nama: k.Nama, Kelompok: k.Kelompok,
			Pokok: h.Pokok, Jumlah: h.Jumlah, PersenPokok: persen(h.Pokok, m.PokokDiperiksa)})
	}
	return r
}

// isi melengkapi baris dengan ringkasan dan foto.
func (s *IpdService) isi(ctx context.Context, baris []model.IpdBaris) error {
	if len(baris) == 0 {
		return nil
	}
	ids := make([]string, len(baris))
	for i := range baris {
		ids[i] = baris[i].ID
	}
	mentah, err := s.repo.Mentah(ctx, ids)
	if err != nil {
		return err
	}
	foto, err := s.repo.Foto(ctx, ids)
	if err != nil {
		return err
	}
	kode, err := s.repo.KodeMaster(ctx)
	if err != nil {
		return err
	}
	for i := range baris {
		baris[i].Ringkasan = susunRingkasan(mentah[baris[i].ID], kode)
		baris[i].Foto = foto[baris[i].ID]
		if baris[i].Foto == nil {
			baris[i].Foto = []model.IpdFotoRef{}
		}
	}
	return nil
}

// Menunggu mengembalikan inspeksi yang menunggu persetujuan pengguna (asisten yang tercatat, atau semua pemegang
// ipd.setujui bila divisinya belum punya asisten).
func (s *IpdService) Menunggu(ctx context.Context, k *auth.Klaim) ([]model.IpdBaris, error) {
	out, err := s.repo.DaftarMenunggu(ctx, k.UnitID, k.UserID)
	if err == nil {
		err = s.isi(ctx, out)
	}
	if err != nil {
		return nil, apperror.Internal("Gagal membaca daftar persetujuan", err)
	}
	return out, nil
}

func (s *IpdService) pindah(ctx context.Context, k *auth.Klaim, id, alasan, ip string, tolak bool) error {
	id = strings.ToLower(id)
	if !model.ReUUID.MatchString(id) {
		return apperror.Unprocessable("id harus UUID")
	}
	var status string
	var err error
	if tolak {
		status, err = s.repo.Tolak(ctx, id, k.UnitID, k.UserID, alasan, k.DeviceID, ip)
	} else {
		status, err = s.repo.Setujui(ctx, id, k.UnitID, k.UserID, k.DeviceID, ip)
	}
	switch {
	case errors.Is(err, repository.ErrNotFound):
		return apperror.NotFound("Inspeksi tidak ditemukan")
	case errors.Is(err, repository.ErrStatusBerubah):
		return apperror.Conflict("Inspeksi sudah diproses (status " + status + ")")
	case errors.Is(err, repository.ErrBukanPenyetuju):
		return apperror.Forbidden("Inspeksi ini ditujukan ke asisten lain")
	case err != nil:
		return apperror.Internal("Gagal memproses persetujuan", err)
	}
	return nil
}

// Setujui menyetujui inspeksi MENUNGGU yang ditujukan ke pengguna; setelah itu inspeksi terkunci.
func (s *IpdService) Setujui(ctx context.Context, k *auth.Klaim, id, ip string) error {
	return s.pindah(ctx, k, id, "", ip, false)
}

// Tolak mengembalikan inspeksi ke inspektur dengan alasan (tampil di HP-nya); inspeksi bisa diperbaiki dan dikirim ulang.
func (s *IpdService) Tolak(ctx context.Context, k *auth.Klaim, id, alasan, ip string) error {
	alasan = strings.TrimSpace(alasan)
	if alasan == "" {
		return apperror.Unprocessable("alasan wajib diisi")
	}
	if len([]rune(alasan)) > 300 {
		return apperror.Unprocessable("alasan maksimal 300 karakter")
	}
	return s.pindah(ctx, k, id, alasan, ip, true)
}

// InspeksiTerakhir adalah peringatan inspeksi ulang: inspeksi pemanen + blok yang sama dalam 7 hari terakhir (selain yang
// DITOLAK). Hanya peringatan; inspeksi ulang tidak dilarang. tanggal kosong berarti hari ini (WIB); kecuali = id inspeksi
// yang sedang diedit.
func (s *IpdService) InspeksiTerakhir(ctx context.Context, k *auth.Klaim, hrmb, asm3Oid int64, tanggal, kecuali string) (*model.IpdInspeksiTerakhir, error) {
	if hrmb <= 0 || asm3Oid <= 0 {
		return nil, apperror.BadRequest("pemanen_hrmb_code dan asm3_oid wajib diisi")
	}
	if tanggal == "" {
		tanggal = s.sekarang().In(wib).Format("2006-01-02")
	}
	t, err := time.Parse("2006-01-02", tanggal)
	if err != nil {
		return nil, apperror.BadRequest("tanggal harus YYYY-MM-DD")
	}
	kecuali = strings.ToLower(kecuali)
	if kecuali != "" && !model.ReUUID.MatchString(kecuali) {
		return nil, apperror.BadRequest("kecuali harus UUID")
	}
	dari := t.AddDate(0, 0, -model.JendelaIpdHari).Format("2006-01-02")
	daftar, err := s.repo.Terakhir(ctx, k.UnitID, hrmb, asm3Oid, dari, tanggal, kecuali)
	if err != nil {
		return nil, apperror.Internal("Gagal memeriksa inspeksi sebelumnya", err)
	}
	return &model.IpdInspeksiTerakhir{JendelaHari: model.JendelaIpdHari, Ada: len(daftar) > 0, Inspeksi: daftar}, nil
}

// ---- Web: daftar, rincian, laporan -----------------------------------------------------------------

var statusIpd = map[string]bool{"MENUNGGU": true, "DISETUJUI": true, "DITOLAK": true}

func (s *IpdService) DaftarAdmin(ctx context.Context, k *auth.Klaim, f model.FilterIpd) (*model.HalamanIpd, error) {
	for _, t := range []string{f.Dari, f.Sampai} {
		if t != "" && !reTanggal.MatchString(t) {
			return nil, tidakValid(errors.New("tanggal harus YYYY-MM-DD"))
		}
	}
	if f.Status != "" && !statusIpd[f.Status] {
		return nil, tidakValid(errors.New("status harus MENUNGGU, DISETUJUI, atau DITOLAK"))
	}
	f.Halaman, f.PerHal = halaman(f.Halaman, f.PerHal)
	o, err := s.repo.DaftarAdmin(ctx, k.UnitID, f)
	if err == nil {
		err = s.isi(ctx, o.Data)
	}
	if err != nil {
		return nil, apperror.Internal("Gagal membaca daftar inspeksi", err)
	}
	return o, nil
}

func (s *IpdService) Detail(ctx context.Context, k *auth.Klaim, id string) (*model.IpdDetail, error) {
	if !model.ReUUID.MatchString(id) {
		return nil, tidakValid(errors.New("id harus UUID"))
	}
	id = strings.ToLower(id)
	b, err := s.repo.Baris(ctx, k.UnitID, id)
	if err != nil {
		return nil, galatRepo(err, "Inspeksi")
	}
	pokok, err := s.repo.Pokok(ctx, id)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca pokok inspeksi", err)
	}
	baris := []model.IpdBaris{*b}
	if err := s.isi(ctx, baris); err != nil {
		return nil, apperror.Internal("Gagal membaca ringkasan inspeksi", err)
	}
	return &model.IpdDetail{IpdBaris: baris[0], Pokok: pokok}, nil
}

// Laporan menyusun loses dari inspeksi yang sudah DISETUJUI. Rentang kosong berarti 30 hari terakhir (WIB).
func (s *IpdService) Laporan(ctx context.Context, k *auth.Klaim, f model.FilterLosesIpd) (*model.IpdLaporan, error) {
	for _, t := range []string{f.Dari, f.Sampai} {
		if t != "" && !reTanggal.MatchString(t) {
			return nil, tidakValid(errors.New("tanggal harus YYYY-MM-DD"))
		}
	}
	if f.Sampai == "" {
		f.Sampai = s.sekarang().In(wib).Format("2006-01-02")
	}
	if f.Dari == "" {
		t, _ := time.Parse("2006-01-02", f.Sampai)
		f.Dari = t.AddDate(0, 0, -29).Format("2006-01-02")
	}
	if f.Dari > f.Sampai {
		return nil, tidakValid(errors.New("dari tidak boleh sesudah sampai"))
	}
	dasar, err := s.repo.DasarLoses(ctx, k.UnitID, f)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca data laporan", err)
	}
	kode, err := s.repo.KodeMaster(ctx)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca kode temuan", err)
	}
	return susunLaporan(dasar, kode, f.Dari, f.Sampai), nil
}

var urutKelompok = []string{"BUAH", "BRONDOLAN", "PELEPAH"}

// susunLaporan menjumlahkan hitungan dasar menjadi laporan loses: ringkasan, per kelompok, per pemanen, per blok,
// per divisi, dan per tanggal. Persen = pokok bertemuan (atau pokok yang memuat kode) dibagi pokok diperiksa.
func susunLaporan(dasar []model.IpdDasar, kode []model.IpdKodeMaster, dari, sampai string) *model.IpdLaporan {
	total := model.IpdMentah{PerKode: map[string]model.IpdHitung{}, PerKelompok: map[string]model.IpdHitung{}}
	grup := func() map[string]*model.IpdGrupLoses { return map[string]*model.IpdGrupLoses{} }
	pemanen, blok, divisi, tanggal := grup(), grup(), grup(), grup()
	pemanenUnik := map[int64]bool{}

	tambahGrup := func(g map[string]*model.IpdGrupLoses, kunci, nama string, d model.IpdDasar) {
		x := g[kunci]
		if x == nil {
			x = &model.IpdGrupLoses{Kunci: kunci, Nama: nama, TemuanPerKode: map[string]int64{}}
			g[kunci] = x
		}
		x.Inspeksi++
		x.PokokDiperiksa += d.Mentah.PokokDiperiksa
		x.PokokBertemuan += d.Mentah.PokokBertemuan
		for kd, h := range d.Mentah.PerKode {
			x.TotalTemuan += h.Jumlah
			x.TemuanPerKode[kd] += h.Jumlah
		}
	}
	for _, d := range dasar {
		pemanenUnik[d.PemanenHrmb] = true
		total.PokokDiperiksa += d.Mentah.PokokDiperiksa
		total.PokokBertemuan += d.Mentah.PokokBertemuan
		for kd, h := range d.Mentah.PerKode {
			t := total.PerKode[kd]
			t.Pokok += h.Pokok
			t.Jumlah += h.Jumlah
			total.PerKode[kd] = t
		}
		for kl, h := range d.Mentah.PerKelompok {
			t := total.PerKelompok[kl]
			t.Pokok += h.Pokok
			t.Jumlah += h.Jumlah
			total.PerKelompok[kl] = t
		}
		nama := d.PemanenNama
		if nama == "" {
			nama = strconv.FormatInt(d.PemanenHrmb, 10)
		}
		tambahGrup(pemanen, strconv.FormatInt(d.PemanenHrmb, 10), nama, d)
		tambahGrup(blok, d.BlokKode, d.BlokKode, d)
		tambahGrup(divisi, d.DivisiKode, d.DivisiKode, d)
		tambahGrup(tanggal, d.Tanggal, d.Tanggal, d)
	}

	out := &model.IpdLaporan{Dari: dari, Sampai: sampai, Inspeksi: int64(len(dasar)), Pemanen: int64(len(pemanenUnik)),
		Ringkasan: susunRingkasan(&total, kode), PerKelompok: []model.IpdKelompokAgg{}}
	for _, kl := range urutKelompok {
		h := total.PerKelompok[kl]
		out.PerKelompok = append(out.PerKelompok, model.IpdKelompokAgg{Kelompok: kl, Pokok: h.Pokok, Jumlah: h.Jumlah, PersenPokok: persen(h.Pokok, total.PokokDiperiksa)})
	}
	hasil := func(g map[string]*model.IpdGrupLoses, urutPersen bool) []model.IpdGrupLoses {
		l := make([]model.IpdGrupLoses, 0, len(g))
		for _, x := range g {
			x.PersenPokokBertemuan = persen(x.PokokBertemuan, x.PokokDiperiksa)
			l = append(l, *x)
		}
		sort.Slice(l, func(i, j int) bool {
			if urutPersen && l[i].PersenPokokBertemuan != l[j].PersenPokokBertemuan {
				return l[i].PersenPokokBertemuan > l[j].PersenPokokBertemuan // paling banyak loses dulu
			}
			return l[i].Nama < l[j].Nama
		})
		return l
	}
	out.PerPemanen, out.PerBlok, out.PerDivisi, out.PerTanggal = hasil(pemanen, true), hasil(blok, true), hasil(divisi, false), hasil(tanggal, false)
	return out
}

// ---- Foto -----------------------------------------------------------------------------------------

// IpdMediaRequest adalah satu foto bukti inspeksi. Keterangan dan Urut dikirim HP lewat query.
type IpdMediaRequest struct {
	IpdID      string
	FotoID     string
	SHA256     string
	Keterangan string
	Urut       int
}

// TerimaFoto menyimpan satu foto JPEG bukti inspeksi (tanpa batas jumlah per inspeksi). Idempoten menurut FotoID.
func (s *IpdService) TerimaFoto(ctx context.Context, k *auth.Klaim, req IpdMediaRequest, isi io.Reader) (string, error) {
	req.IpdID = strings.ToLower(req.IpdID)
	req.FotoID = strings.ToLower(req.FotoID)
	req.SHA256 = strings.ToLower(req.SHA256)
	req.Keterangan = strings.TrimSpace(req.Keterangan)
	if !model.ReUUID.MatchString(req.IpdID) || !model.ReUUID.MatchString(req.FotoID) || len(req.SHA256) != 64 {
		return "", apperror.BadRequest("id inspeksi, X-Foto-Id (UUID), dan X-Sha256 (64 hex) wajib benar")
	}
	if len([]rune(req.Keterangan)) > 200 {
		return "", apperror.BadRequest("keterangan foto maksimal 200 karakter")
	}
	if req.Urut < 0 || req.Urut > 9999 {
		return "", apperror.BadRequest("urut harus 0 sampai 9999")
	}
	pemilik, err := s.repo.PemilikID(ctx, req.IpdID)
	if errors.Is(err, repository.ErrNotFound) {
		return "", apperror.NotFound("Inspeksi belum ada. Kirim inspeksinya dulu")
	}
	if err != nil {
		return "", apperror.Internal("Gagal membaca inspeksi", err)
	}
	if pemilik != k.UserID {
		boleh, err := s.akses.Punya(ctx, k.UserID, permIpdKelola)
		if err != nil {
			return "", apperror.Internal("Gagal memeriksa hak akses", err)
		}
		if !boleh {
			return "", apperror.Forbidden("Bukan inspeksi Anda")
		}
	}
	sudah, err := s.repo.FotoAda(ctx, req.FotoID)
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa foto", err)
	}
	if sudah {
		return StatusSudahAda, nil
	}
	data, err := io.ReadAll(io.LimitReader(isi, MaksFotoByte+1))
	if err != nil || len(data) == 0 {
		return "", apperror.BadRequest("Isi foto kosong atau gagal dibaca")
	}
	if len(data) > MaksFotoByte {
		return "", apperror.TooLarge("Foto melebihi 8 MB")
	}
	h := sha256.Sum256(data)
	if hex.EncodeToString(h[:]) != req.SHA256 {
		return "", apperror.Unprocessable("Checksum tidak cocok. Kirim ulang")
	}
	if len(data) < 3 || data[0] != 0xFF || data[1] != 0xD8 {
		return "", apperror.UnsupportedMedia("Hanya JPEG yang diterima")
	}
	path, err := s.store.Simpan("ipd_"+req.IpdID, req.FotoID+".jpg", data)
	if err != nil {
		return "", apperror.Internal("Gagal menyimpan foto", err)
	}
	if err := s.repo.SimpanFoto(ctx, model.IpdFoto{ID: req.FotoID, IpdID: req.IpdID, Keterangan: req.Keterangan, Urut: req.Urut,
		NamaFile: path, SHA256: req.SHA256, UserID: k.UserID}); err != nil {
		_ = s.store.Hapus(path)
		return "", apperror.Internal("Gagal mencatat foto", err)
	}
	return StatusDiterima, nil
}

// PathFoto mengembalikan path berkas foto inspeksi di unit pengguna.
func (s *IpdService) PathFoto(ctx context.Context, k *auth.Klaim, fotoID string) (string, error) {
	if !model.ReUUID.MatchString(fotoID) {
		return "", tidakValid(errors.New("id foto harus UUID"))
	}
	p, err := s.repo.PathFoto(ctx, k.UnitID, strings.ToLower(fotoID))
	return p, galatRepo(err, "Foto")
}
