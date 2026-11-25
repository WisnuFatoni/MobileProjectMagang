# BACKUP_SQL: salinan database fai26 untuk dipasang di server

Dibuat 22 November 2026 dari database pengembangan `fai26_dev` (API 0.21.0, migrasi sampai 012). **Folder ini berisi data asli kebun dan hash kata sandi akun: jangan di-commit, jangan dibagikan, dan simpan di tempat aman.**

## Isi

| Berkas | Fungsi | Wajib? | Sensitif? |
|---|---|---|---|
| `01_skema_lengkap.sql` | Seluruh skema untuk **database kosong**: skema dasar + migrasi 002, 003, 005 sampai 012 (24 tabel, 2 view, 4 prosedur, role `fai26_hvdm`, permission, role, 11 kode temuan IPD) | Ya | Tidak |
| `02_data_master.sql` | Data master: 1 unit, 2 divisi, **118 blok, 2.487 TPH (QR), 61 pemanen**, dengan ID yang sama seperti di HP | Ya | Data kebun (nama pemanen) |
| `03_hak_role.sql` | Menyamakan hak role dengan pengembangan. **Penting:** skema dasar memberi MANDOR hak `panen.setujui`; keputusan 19 Nov: yang menyetujui adalah KRANI, jadi hak itu dicabut dari MANDOR. Tanpa file ini, mandor menyetujui sendiri dan krani dilewati | **Ya** | Tidak |
| `04_akun_pengguna.sql` | 11 akun (hash bcrypt), role akun, asisten tiap divisi | Opsional | **Ya (hash kata sandi)** |
| `05_audit_log_hanya_tambah.sql` | Migrasi 004: akun API tidak boleh mengubah/menghapus `audit_log`. **Ganti `[api_fai26]` dengan nama user database akun API** | Ya, di server sungguhan | Tidak |
| `fai26_dev_lengkap.bak` | Cadangan SQL Server **utuh** (termasuk data uji panen/IPD, audit, sesi, perangkat) | Alternatif | **Ya** |
| `SHA256.txt` | Sidik jari berkas | | |

Yang **sengaja tidak** ada di berkas `.sql`: catatan panen dan inspeksi uji, foto, jalur GPS, audit log, sesi login, dan perangkat (data uji/sementara, tidak untuk server). Foto profil tidak ikut.

## Cara memasang di server (urutan)

Perintah memakai `sqlcmd`; **`-I` wajib** (indeks terfilter), `-b` menghentikan bila ada galat, `-f 65001` untuk UTF-8. Jalankan dari folder ini.

```
:: 1) buat database kosong (akun DBA)
sqlcmd -S <server> -U <akun_dba> -I -b -Q "CREATE DATABASE fai26"

:: 2) skema, master, hak role
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -f 65001 -i 01_skema_lengkap.sql
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -f 65001 -i 02_data_master.sql
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -f 65001 -i 03_hak_role.sql

:: 3) akun: SALAH SATU
::    a) pakai akun dari pengembangan:
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -f 65001 -i 04_akun_pengguna.sql
::    b) atau buat ADMIN baru: lihat FINAL\lampiran\skrip\awal_unit_admin.sql dan fai26-api\cmd\hashpw

:: 4) login API berhak sempit (db_datareader + db_datawriter, bukan sa), lalu:
::    edit nama user di 05_audit_log_hanya_tambah.sql
sqlcmd -S <server> -d fai26 -U <akun_dba> -I -b -f 65001 -i 05_audit_log_hanya_tambah.sql
```

Lalu lanjutkan sesuai `FINAL\08_PENERAPAN_DAN_OPERASI.md` (isi `.env`, jalankan API, `periksa-server.ps1`). `01` hanya untuk database kosong dan **dijalankan sekali**; `02` juga sekali (kunci ganda bila diulang); `03` dan `04` aman diulang.

## Teruji

Seluruh urutan `01` sampai `04` dijalankan pada database kosong sementara di SQL Server lokal (kode keluar 0), hasilnya: 24 tabel, 118 blok, 2.487 TPH (QR unik semua), 61 pemanen, 11 akun, 12 role akun, 77 hak role aktif (MANDOR tanpa `panen.setujui`), view siap-impor 45 kolom, 4 prosedur `sp_fai_*`, role `fai26_hvdm`; `03` dan `04` diulang tanpa galat. Database sementara sudah dihapus. **Belum diuji:** API terhubung ke hasil pemasangan ini, dan pemasangan di SQL Server milik kebun.

## Yang HARUS disesuaikan setelah dipasang

1. **Nilai kunci ERP masih sementara**: `blok.erp_oid` (900001 dan seterusnya) dan `pemanen.erp_hrmb_code` (800001 dan seterusnya). `divisi.erp_oid` dan `pemanen.mandor_hrmb_code` kosong. Ganti dengan nilai asli ERP lewat web admin (lihat `FINAL\12_ERP_YANG_PERLU_DIBUAT.md` dan `FINAL\lampiran\cocokkan_*.csv`); tanpa itu ERP menolak semua impor.
2. **`unit`**: `erp_db` (`kp_stl2025`) dan `tahun_cluster` (2026) tidak konsisten dan nama unit masih "Kebun Contoh STL"; sesuaikan dengan ERP unit yang dipakai.
3. **Akun**: kata sandi akun di `04` adalah yang dipakai di pengembangan; ganti bila perlu. Akun mandor perlu `mandor_hrmb_code` dan `afdeling_kode` asli.
4. Master dari file ini adalah **foto 22 Nov 2026**; perubahan setelahnya di `fai26_dev` tidak ikut.

## Memulihkan dari `.bak` (alternatif)

```sql
RESTORE FILELISTONLY FROM DISK = N'<jalur>\fai26_dev_lengkap.bak';
RESTORE DATABASE fai26 FROM DISK = N'<jalur>\fai26_dev_lengkap.bak'
  WITH MOVE N'fai26_dev' TO N'<folder data>\fai26.mdf', MOVE N'fai26_dev_log' TO N'<folder data>\fai26_log.ldf', STATS = 10;
```

Peringatan: `.bak` dibuat di **SQL Server 2025 (17.0)** dan **tidak dapat dipulihkan ke versi lebih lama** (mis. 2022 atau 2019); berkas `.sql` tetap bisa (SQL Server 2016 SP1 ke atas). `.bak` membawa seluruh isi `fai26_dev` termasuk data uji dan hash sandi, sehingga **tidak disarankan untuk server sungguhan**; pakai `.sql`. Setelah memulihkan ke server lain, user database API menjadi yatim: `ALTER USER <user_api> WITH LOGIN = <login_api>;`.
