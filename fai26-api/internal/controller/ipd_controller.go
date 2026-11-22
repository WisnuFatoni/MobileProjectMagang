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

// IpdController melayani modul IPD (Inspeksi Panen Detail): HP (kirim, status, persetujuan, peringatan inspeksi ulang,
// foto) dan web (daftar, rincian, laporan loses).
type IpdController struct {
	svc  *service.IpdService
	foto PembukaFoto
}

func NewIpdController(svc *service.IpdService, foto PembukaFoto) *IpdController {
	return &IpdController{svc: svc, foto: foto}
}

// Terima: POST /api/ipd
func (c *IpdController) Terima(w http.ResponseWriter, r *http.Request) {
	var in model.IpdInput
	if err := decodeJSON(w, r, 4<<20, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	status, err := c.svc.Terima(r.Context(), auth.KlaimDari(r.Context()), in, ipDari(r))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	kode := http.StatusCreated
	if status == service.StatusSudahAda {
		kode = http.StatusOK
	}
	response.JSON(w, kode, map[string]string{"status": status, "id": in.IpdID})
}

// Media: POST /api/ipd/{id}/media?keterangan=&urut= (isi mentah JPEG; header X-Foto-Id dan X-Sha256)
func (c *IpdController) Media(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	urut, _ := strconv.Atoi(q.Get("urut"))
	fotoID := r.Header.Get("X-Foto-Id")
	status, err := c.svc.TerimaFoto(r.Context(), auth.KlaimDari(r.Context()), service.IpdMediaRequest{
		IpdID: r.PathValue("id"), FotoID: fotoID, SHA256: r.Header.Get("X-Sha256"), Keterangan: q.Get("keterangan"), Urut: urut,
	}, r.Body)
	if err != nil {
		response.Error(w, r, err)
		return
	}
	kode := http.StatusCreated
	if status == service.StatusSudahAda {
		kode = http.StatusOK
	}
	response.JSON(w, kode, map[string]string{"status": status, "id": fotoID})
}

// DaftarStatus: GET /api/ipd?sejak=
func (c *IpdController) DaftarStatus(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.DaftarStatus(r.Context(), auth.KlaimDari(r.Context()), r.URL.Query().Get("sejak"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Menunggu: GET /api/ipd/menunggu
func (c *IpdController) Menunggu(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.Menunggu(r.Context(), auth.KlaimDari(r.Context()))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Setujui: POST /api/ipd/{id}/setujui
func (c *IpdController) Setujui(w http.ResponseWriter, r *http.Request) {
	if err := c.svc.Setujui(r.Context(), auth.KlaimDari(r.Context()), r.PathValue("id"), ipDari(r)); err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "DISETUJUI"})
}

// Tolak: POST /api/ipd/{id}/tolak
func (c *IpdController) Tolak(w http.ResponseWriter, r *http.Request) {
	var in model.TolakInput
	if err := decodeJSON(w, r, 1<<14, &in); err != nil {
		response.Error(w, r, err)
		return
	}
	if err := c.svc.Tolak(r.Context(), auth.KlaimDari(r.Context()), r.PathValue("id"), in.Alasan, ipDari(r)); err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, map[string]string{"status": "DITOLAK"})
}

// InspeksiTerakhir: GET /api/ipd/inspeksi-terakhir?pemanen_hrmb_code=&asm3_oid=&tanggal=&kecuali=
func (c *IpdController) InspeksiTerakhir(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	hrmb, _ := strconv.ParseInt(q.Get("pemanen_hrmb_code"), 10, 64)
	oid, _ := strconv.ParseInt(q.Get("asm3_oid"), 10, 64)
	out, err := c.svc.InspeksiTerakhir(r.Context(), auth.KlaimDari(r.Context()), hrmb, oid, q.Get("tanggal"), q.Get("kecuali"))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Foto: GET /api/ipd/foto/{id} menyajikan berkas foto bukti inspeksi.
func (c *IpdController) Foto(w http.ResponseWriter, r *http.Request) {
	path, err := c.svc.PathFoto(r.Context(), auth.KlaimDari(r.Context()), r.PathValue("id"))
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

// DaftarAdmin: GET /api/admin/ipd
func (c *IpdController) DaftarAdmin(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	hrmb, _ := strconv.ParseInt(q.Get("pemanen_hrmb_code"), 10, 64)
	out, err := c.svc.DaftarAdmin(r.Context(), auth.KlaimDari(r.Context()), model.FilterIpd{
		Dari: q.Get("dari"), Sampai: q.Get("sampai"), Status: q.Get("status"), DivisiKode: q.Get("divisi_kode"), BlokKode: q.Get("blok_kode"),
		PemanenHrmb: hrmb, Halaman: angka(r, "halaman"), PerHal: angka(r, "per_halaman"),
	})
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Detail: GET /api/admin/ipd/{id}
func (c *IpdController) Detail(w http.ResponseWriter, r *http.Request) {
	out, err := c.svc.Detail(r.Context(), auth.KlaimDari(r.Context()), r.PathValue("id"), ipDari(r))
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}

// Laporan: GET /api/admin/ipd/laporan
func (c *IpdController) Laporan(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	hrmb, _ := strconv.ParseInt(q.Get("pemanen_hrmb_code"), 10, 64)
	out, err := c.svc.Laporan(r.Context(), auth.KlaimDari(r.Context()), model.FilterLosesIpd{
		Dari: q.Get("dari"), Sampai: q.Get("sampai"), DivisiKode: q.Get("divisi_kode"), BlokKode: q.Get("blok_kode"), PemanenHrmb: hrmb,
	})
	if err != nil {
		response.Error(w, r, err)
		return
	}
	response.JSON(w, http.StatusOK, out)
}
