package controller

import (
	"io"
	"mime"
	"net/http"
	"path/filepath"
	"strconv"

	"fai26-api/internal/auth"
	"fai26-api/internal/model"
	"fai26-api/internal/response"
	"fai26-api/internal/service"
)

// ---- Master --------------------------------------------------------------------------------------

type AdminMasterController struct{ svc *service.AdminMasterService }

func NewAdminMasterController(svc *service.AdminMasterService) *AdminMasterController {
	return &AdminMasterController{svc: svc}
}

func idPath(r *http.Request) int64 {
	id, _ := strconv.ParseInt(r.PathValue("id"), 10, 64)
	return id
}

func klaim(r *http.Request) *auth.Klaim { return auth.KlaimDari(r.Context()) }

func kirimDaftar(w http.ResponseWriter, r *http.Request, v []model.BarisMaster, err error) {
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

func dibuat(w http.ResponseWriter, r *http.Request, id int64, err error) {
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusCreated, map[string]int64{"id": id})
}

func selesai(w http.ResponseWriter, r *http.Request, err error) {
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "OK"})
}

func (c *AdminMasterController) DaftarDivisi(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.DaftarDivisi(r.Context(), klaim(r))
	kirimDaftar(w, r, v, err)
}

func (c *AdminMasterController) BuatDivisi(w http.ResponseWriter, r *http.Request) {
	var in model.DivisiInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	id, err := c.svc.BuatDivisi(r.Context(), klaim(r), in)
	dibuat(w, r, id, err)
}

func (c *AdminMasterController) UbahDivisi(w http.ResponseWriter, r *http.Request) {
	var in model.DivisiInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	selesai(w, r, c.svc.UbahDivisi(r.Context(), klaim(r), idPath(r), in))
}

func (c *AdminMasterController) HapusDivisi(w http.ResponseWriter, r *http.Request) {
	selesai(w, r, c.svc.HapusDivisi(r.Context(), klaim(r), idPath(r)))
}

func (c *AdminMasterController) DaftarBlok(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.DaftarBlok(r.Context(), klaim(r))
	kirimDaftar(w, r, v, err)
}

func (c *AdminMasterController) BuatBlok(w http.ResponseWriter, r *http.Request) {
	var in model.BlokInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	id, err := c.svc.BuatBlok(r.Context(), klaim(r), in)
	dibuat(w, r, id, err)
}

func (c *AdminMasterController) UbahBlok(w http.ResponseWriter, r *http.Request) {
	var in model.BlokInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	n, err := c.svc.UbahBlok(r.Context(), klaim(r), idPath(r), in)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]any{"status": "OK", "qr_berubah": n})
}

func (c *AdminMasterController) HapusBlok(w http.ResponseWriter, r *http.Request) {
	selesai(w, r, c.svc.HapusBlok(r.Context(), klaim(r), idPath(r)))
}

func (c *AdminMasterController) DaftarTph(w http.ResponseWriter, r *http.Request) {
	blok, _ := strconv.ParseInt(r.URL.Query().Get("blok_id"), 10, 64)
	v, err := c.svc.DaftarTph(r.Context(), klaim(r), blok)
	kirimDaftar(w, r, v, err)
}

func (c *AdminMasterController) BuatTph(w http.ResponseWriter, r *http.Request) {
	var in model.TphInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	id, err := c.svc.BuatTph(r.Context(), klaim(r), in)
	dibuat(w, r, id, err)
}

func (c *AdminMasterController) BuatTphMassal(w http.ResponseWriter, r *http.Request) {
	var in model.TphMassalInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	ids, err := c.svc.BuatTphMassal(r.Context(), klaim(r), in)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusCreated, map[string]any{"dibuat": len(ids), "id": ids})
}

func (c *AdminMasterController) UbahTph(w http.ResponseWriter, r *http.Request) {
	var in model.TphInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	qr, err := c.svc.UbahTph(r.Context(), klaim(r), idPath(r), in)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]any{"status": "OK", "qr_berubah": qr})
}

func (c *AdminMasterController) HapusTph(w http.ResponseWriter, r *http.Request) {
	selesai(w, r, c.svc.HapusTph(r.Context(), klaim(r), idPath(r)))
}

func (c *AdminMasterController) DaftarPemanen(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.DaftarPemanen(r.Context(), klaim(r))
	kirimDaftar(w, r, v, err)
}

func (c *AdminMasterController) BuatPemanen(w http.ResponseWriter, r *http.Request) {
	var in model.PemanenAdminInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	id, err := c.svc.BuatPemanen(r.Context(), klaim(r), in)
	dibuat(w, r, id, err)
}

func (c *AdminMasterController) UbahPemanen(w http.ResponseWriter, r *http.Request) {
	var in model.PemanenAdminInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	selesai(w, r, c.svc.UbahPemanen(r.Context(), klaim(r), idPath(r), in))
}

func (c *AdminMasterController) HapusPemanen(w http.ResponseWriter, r *http.Request) {
	selesai(w, r, c.svc.HapusPemanen(r.Context(), klaim(r), idPath(r)))
}

// ---- Pengguna, perangkat, laporan ----------------------------------------------------------------

// Pembuka berkas foto, dipisah supaya controller tidak bergantung pada lapisan storage.
type PembukaFoto interface {
	Buka(path string) (io.ReadCloser, error)
}

type AdminController struct {
	svc  *service.AdminService
	foto PembukaFoto
}

func NewAdminController(svc *service.AdminService, foto PembukaFoto) *AdminController {
	return &AdminController{svc: svc, foto: foto}
}

func angka(r *http.Request, kunci string) int {
	n, _ := strconv.Atoi(r.URL.Query().Get(kunci))
	return n
}

func (c *AdminController) DaftarRole(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.DaftarRole(r.Context())
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

func (c *AdminController) DaftarPengguna(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.DaftarPengguna(r.Context(), klaim(r))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

func (c *AdminController) BuatPengguna(w http.ResponseWriter, r *http.Request) {
	var in model.PenggunaInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	id, err := c.svc.BuatPengguna(r.Context(), klaim(r), in)
	dibuat(w, r, id, err)
}

func (c *AdminController) UbahPengguna(w http.ResponseWriter, r *http.Request) {
	var in model.PenggunaInput
	if err := decodeJSON(w, r, 1<<16, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	selesai(w, r, c.svc.UbahPengguna(r.Context(), klaim(r), idPath(r), in))
}

func (c *AdminController) ResetPassword(w http.ResponseWriter, r *http.Request) {
	var in model.ResetPasswordInput
	if err := decodeJSON(w, r, 1<<14, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	selesai(w, r, c.svc.ResetPassword(r.Context(), klaim(r), idPath(r), in))
}

func (c *AdminController) DaftarPerangkat(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.DaftarPerangkat(r.Context())
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

func (c *AdminController) UbahPerangkat(w http.ResponseWriter, r *http.Request) {
	var in model.PerangkatUbahInput
	if err := decodeJSON(w, r, 1<<14, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	selesai(w, r, c.svc.UbahPerangkat(r.Context(), klaim(r), r.PathValue("id"), in))
}

func (c *AdminController) Ringkasan(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.Ringkasan(r.Context(), klaim(r), r.URL.Query().Get("tanggal"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

// Produktivitas: GET /api/admin/produktivitas?tanggal= (ranking produktivitas pemanen satu hari).
func (c *AdminController) Produktivitas(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.Produktivitas(r.Context(), klaim(r), r.URL.Query().Get("tanggal"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

func (c *AdminController) DaftarPanen(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	v, err := c.svc.DaftarPanen(r.Context(), klaim(r), model.FilterPanen{
		Dari: q.Get("dari"), Sampai: q.Get("sampai"), StatusEr: q.Get("status_erp"), Cari: q.Get("cari"),
		Halaman: angka(r, "halaman"), PerHal: angka(r, "per_halaman"),
	})
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

func (c *AdminController) DetailPanen(w http.ResponseWriter, r *http.Request) {
	v, err := c.svc.DetailPanen(r.Context(), klaim(r), r.PathValue("id"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}

// Foto menyajikan berkas foto. Hanya JPEG yang pernah diterima server; tipe dipastikan dari ekstensi yang kita tulis sendiri.
func (c *AdminController) Foto(w http.ResponseWriter, r *http.Request) {
	path, err := c.svc.PathFoto(r.Context(), klaim(r), r.PathValue("id"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	f, err := c.foto.Buka(path)
	if err != nil {
		response.Error(w, r, service.BerkasHilang())
		return
	}
	defer f.Close()
	tipe := mime.TypeByExtension(filepath.Ext(path))
	if tipe == "" {
		tipe = "image/jpeg"
	}
	w.Header().Set("Content-Type", tipe)
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Cache-Control", "private, max-age=3600")
	_, _ = io.Copy(w, f)
}

func (c *AdminController) DaftarAudit(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	v, err := c.svc.DaftarAudit(r.Context(), model.FilterAudit{
		Tabel: q.Get("tabel"), Aksi: q.Get("aksi"), Dari: q.Get("dari"), Sampai: q.Get("sampai"), Cari: q.Get("cari"),
		Halaman: angka(r, "halaman"), PerHal: angka(r, "per_halaman"),
	})
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, v)
}
