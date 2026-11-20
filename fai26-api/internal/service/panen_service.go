package service

import (
	"context"
	"errors"
	"strconv"
	"strings"
	"time"

	"fai26-api/internal/apperror"
	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/repository"
)

const (
	StatusDiterima = "DITERIMA"
	StatusSudahAda = "SUDAH_ADA"
	permSetujui    = "panen.setujui"
	permKoreksi    = "panen.koreksi"

	pesanTerkunci = "Catatan sudah disetujui krani, tidak bisa diubah lagi"
)

type PanenService struct {
	panen PanenRepo
	units UnitRepo
	users UserRepo
	akses *AksesService
}

func NewPanenService(panen PanenRepo, units UnitRepo, users UserRepo, akses *AksesService) *PanenService {
	return &PanenService{panen: panen, units: units, users: users, akses: akses}
}

// Terima menyimpan satu catatan panen dari HP. Mengembalikan StatusDiterima untuk catatan baru dan
// StatusSudahAda bila UUID-nya sudah pernah diterima (idempoten: HP boleh mengirim ulang tanpa menggandakan data).
func (s *PanenService) Terima(ctx context.Context, k *auth.Klaim, in model.PanenInput, ip string) (string, error) {
	in.PanenID = strings.ToLower(in.PanenID)
	if err := in.Validasi(); err != nil {
		return "", apperror.Unprocessable(err.Error())
	}
	lama, err := s.panen.Cari(ctx, in.PanenID)
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa catatan", err)
	}
	// Catatan yang sudah ada tidak diubah (aman untuk percobaan ulang), kecuali kirim_ulang=true: mandor memperbaiki catatan
	// yang DITOLAK krani, atau mengubah catatan yang masih MENUNGGU persetujuan krani. Isinya diganti. Setelah krani
	// menyetujui (BARU dan seterusnya) catatan terkunci dan tidak bisa diubah lagi (423).
	ganti := false
	if lama != nil {
		if !in.KirimUlang {
			return StatusSudahAda, nil
		}
		if lama.StatusErp != "DITOLAK" && lama.StatusErp != "MENUNGGU" {
			return "", apperror.Locked(pesanTerkunci)
		}
		if lama.PemilikID != k.UserID {
			boleh, err := s.akses.Punya(ctx, k.UserID, permKoreksi)
			if err != nil {
				return "", apperror.Internal("Gagal memeriksa hak akses", err)
			}
			if !boleh {
				return "", apperror.Forbidden("Tidak berhak mengirim ulang catatan milik orang lain")
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
		return "", apperror.Forbidden("Unit catatan berbeda dengan unit akun")
	}

	// Penyetuju. Mandor yang menginput menyetujui sendiri: tanpa disetujui_username, pengirim yang memegang panen.setujui
	// dianggap penyetuju pada saat ini. Pengirim tanpa hak itu (krani) tetap harus membawa persetujuan mandor.
	var penyetujuID int64
	disetujuiPada := time.Now().UTC()
	menunggu := false
	if in.DisetujuiUsername == "" {
		boleh, err := s.akses.Punya(ctx, k.UserID, permSetujui)
		if err != nil {
			return "", apperror.Internal("Gagal memeriksa hak penyetuju", err)
		}
		if boleh {
			penyetujuID = k.UserID
		} else {
			// Pengirim tanpa hak setuju (mandor): catatan disimpan MENUNGGU, krani menyetujuinya dari HP-nya sendiri.
			menunggu = true
		}
	} else {
		penyetujuID, err = s.users.IDDariUsername(ctx, in.DisetujuiUsername)
		if errors.Is(err, repository.ErrNotFound) {
			return "", apperror.Unprocessable("Penyetuju tidak dikenal: " + in.DisetujuiUsername)
		}
		if err != nil {
			return "", apperror.Internal("Gagal membaca penyetuju", err)
		}
		// Penyetuju harus benar-benar memegang hak menyetujui di server, bukan sekadar klaim HP.
		boleh, err := s.akses.Punya(ctx, penyetujuID, permSetujui)
		if err != nil {
			return "", apperror.Internal("Gagal memeriksa hak penyetuju", err)
		}
		if !boleh {
			return "", apperror.Unprocessable("Penyetuju tidak berhak menyetujui")
		}
		disetujuiPada, _ = model.ParseWaktu(in.DisetujuiPada) // sudah divalidasi
	}

	// Pencatat: pengirim sendiri. Catatan boleh dikirim atas nama orang lain (misalnya mandor yang login di HP
	// krani) hanya oleh pemegang panen.koreksi; pencatat sebenarnya tetap tercatat sebagai pemilik catatan.
	pencatatID := k.UserID
	if in.PencatatUsername != "" {
		id, err := s.users.IDDariUsername(ctx, in.PencatatUsername)
		if errors.Is(err, repository.ErrNotFound) {
			return "", apperror.Unprocessable("Pencatat tidak dikenal: " + in.PencatatUsername)
		}
		if err != nil {
			return "", apperror.Internal("Gagal membaca pencatat", err)
		}
		if id != k.UserID {
			boleh, err := s.akses.Punya(ctx, k.UserID, permKoreksi)
			if err != nil {
				return "", apperror.Internal("Gagal memeriksa hak akses", err)
			}
			if !boleh {
				return "", apperror.Forbidden("Tidak berhak mengirim catatan milik orang lain")
			}
			pencatatID = id
		}
	}

	// Mandor dan afdeling berasal dari akun pencatat (bukan dari isi kiriman, supaya tidak bisa dipalsukan).
	pencatat, err := s.users.Profil(ctx, pencatatID)
	if err != nil {
		return "", apperror.Internal("Gagal membaca profil pencatat", err)
	}

	// Blok, TPH, dan pemanen harus ada di master; pemanen harus milik mandor pencatat (bila akunnya akun mandor).
	hrmb := int64(in.Pemanen[0].HrmbCode)
	m, err := s.panen.CekMaster(ctx, unitID, in.Asm3Oid, in.TphKode, hrmb)
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa master", err)
	}
	switch {
	case !m.BlokAda:
		return "", apperror.Unprocessable("Blok tidak ada di master (asm3_oid " + strconv.FormatInt(in.Asm3Oid, 10) + ")")
	case !m.TphAda:
		return "", apperror.Unprocessable("TPH " + in.TphKode + " tidak ada di master blok itu")
	case !m.PemanenAda:
		return "", apperror.Unprocessable("Pemanen " + strconv.FormatInt(hrmb, 10) + " tidak ada di master")
	}
	if pencatat.MandorHrmbCode != nil && (m.PemanenMandor == nil || *m.PemanenMandor != *pencatat.MandorHrmbCode) {
		return "", apperror.Unprocessable("Pemanen " + strconv.FormatInt(hrmb, 10) + " bukan pemanen milik mandor yang login")
	}

	// Anti ganda: satu catatan per tanggal + unit + blok + TPH + pemanen.
	dobel, err := s.panen.AdaKombinasi(ctx, in.TanggalPanen, unitID, in.Asm3Oid, in.TphKode, hrmb, in.PanenID)
	if err != nil {
		return "", apperror.Internal("Gagal memeriksa duplikasi", err)
	}
	if dobel {
		return "", apperror.Conflict("TPH ini sudah dicatat untuk pemanen dan tanggal yang sama")
	}

	rec := model.PanenSimpan{Input: in, UnitID: unitID, DeviceID: k.DeviceID, UserID: k.UserID, PencatatID: pencatatID,
		PenyetujuID: penyetujuID, IP: ip, Ganti: ganti, MandorHrmbCode: pencatat.MandorHrmbCode, Menunggu: menunggu}
	if pencatat.AfdelingKode != nil {
		rec.AfdelingKode = *pencatat.AfdelingKode
	}
	rec.DisetujuiPada = disetujuiPada
	rec.DibuatPada = time.Now().UTC()
	if in.DibuatPada != "" {
		rec.DibuatPada, _ = model.ParseWaktu(in.DibuatPada)
	}
	if in.GpsWaktu != nil && *in.GpsWaktu != "" {
		t, _ := model.ParseWaktu(*in.GpsWaktu)
		rec.GpsWaktu = &t
	}
	if err := s.panen.Simpan(ctx, rec); err != nil {
		if errors.Is(err, repository.ErrDuplikat) { // balapan dengan kiriman lain yang sama
			return "", apperror.Conflict("TPH ini sudah dicatat untuk pemanen dan tanggal yang sama")
		}
		if ganti && errors.Is(err, repository.ErrNotFound) { // krani menyetujui tepat saat perubahan dikirim
			return "", apperror.Locked(pesanTerkunci)
		}
		return "", apperror.Internal("Gagal menyimpan catatan", err)
	}
	return StatusDiterima, nil
}

// Menunggu mengembalikan catatan di unit pengguna yang menunggu persetujuan (untuk pemegang panen.setujui).
func (s *PanenService) Menunggu(ctx context.Context, k *auth.Klaim) ([]model.PanenMenunggu, error) {
	out, err := s.panen.DaftarMenunggu(ctx, k.UnitID)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca daftar persetujuan", err)
	}
	return out, nil
}

func (s *PanenService) pindahMenunggu(ctx context.Context, k *auth.Klaim, id, ip, alasan string, tolak bool) (string, error) {
	id = strings.ToLower(id)
	if !model.ReUUID.MatchString(id) {
		return "", apperror.Unprocessable("id harus UUID")
	}
	var status string
	var err error
	if tolak {
		status, err = s.panen.Tolak(ctx, id, k.UnitID, k.UserID, alasan, k.DeviceID, ip)
	} else {
		status, err = s.panen.Setujui(ctx, id, k.UnitID, k.UserID, k.DeviceID, ip)
	}
	if errors.Is(err, repository.ErrNotFound) {
		return "", apperror.NotFound("Catatan tidak ditemukan")
	}
	if errors.Is(err, repository.ErrStatusBerubah) {
		return "", apperror.Conflict("Catatan sudah diproses (status " + status + ")")
	}
	if err != nil {
		return "", apperror.Internal("Gagal memproses persetujuan", err)
	}
	return status, nil
}

// Setujui menyetujui catatan MENUNGGU: statusnya menjadi BARU (siap ditarik ERP), atas nama pemegang panen.setujui.
func (s *PanenService) Setujui(ctx context.Context, k *auth.Klaim, id, ip string) error {
	_, err := s.pindahMenunggu(ctx, k, id, ip, "", false)
	return err
}

// Tolak mengembalikan catatan MENUNGGU ke pencatat dengan alasan; alasan tampil di HP pencatat dan catatannya bisa diperbaiki.
func (s *PanenService) Tolak(ctx context.Context, k *auth.Klaim, id, alasan, ip string) error {
	alasan = strings.TrimSpace(alasan)
	if alasan == "" {
		return apperror.Unprocessable("alasan wajib diisi")
	}
	if len([]rune(alasan)) > 300 {
		return apperror.Unprocessable("alasan maksimal 300 karakter")
	}
	_, err := s.pindahMenunggu(ctx, k, id, ip, "Ditolak krani: "+alasan, true)
	return err
}

// DaftarStatus mengembalikan status catatan milik pengguna (semua catatan unit bila memegang panen.koreksi).
func (s *PanenService) DaftarStatus(ctx context.Context, k *auth.Klaim, sejakTeks string) ([]model.StatusPanen, error) {
	sejak := time.Unix(0, 0).UTC()
	if sejakTeks != "" {
		t, err := model.ParseWaktu(sejakTeks)
		if err != nil {
			return nil, apperror.BadRequest("sejak: " + err.Error())
		}
		sejak = t
	}
	semua, err := s.akses.Punya(ctx, k.UserID, permKoreksi)
	if err != nil {
		return nil, apperror.Internal("Gagal memeriksa hak akses", err)
	}
	out, err := s.panen.DaftarStatus(ctx, k.UnitID, k.UserID, semua, sejak)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca status", err)
	}
	return out, nil
}

// Ringkasan menyusun data dashboard HP. Tanggal kosong berarti hari ini menurut WIB. Pengguna biasa melihat catatannya
// sendiri; pemegang panen.koreksi (mandor, asisten) dan panen.setujui (krani, yang memeriksa catatan mandor) melihat
// semua catatan di unitnya.
func (s *PanenService) Ringkasan(ctx context.Context, k *auth.Klaim, tanggal string) (*model.RingkasanPanen, error) {
	if tanggal == "" {
		tanggal = time.Now().In(wib).Format("2006-01-02")
	} else if _, err := time.Parse("2006-01-02", tanggal); err != nil {
		return nil, apperror.BadRequest("tanggal harus YYYY-MM-DD")
	}
	semua, err := s.akses.Punya(ctx, k.UserID, permKoreksi)
	if err == nil && !semua {
		semua, err = s.akses.Punya(ctx, k.UserID, permSetujui)
	}
	if err != nil {
		return nil, apperror.Internal("Gagal memeriksa hak akses", err)
	}
	out, err := s.panen.Ringkasan(ctx, k.UnitID, k.UserID, semua, tanggal)
	if err != nil {
		return nil, apperror.Internal("Gagal membaca ringkasan", err)
	}
	out.Lingkup = "SENDIRI"
	if semua {
		out.Lingkup = "UNIT"
	}
	return out, nil
}
