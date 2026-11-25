-- 05 AUDIT LOG HANYA-TAMBAH (migrasi 004)
-- Dibuat 22 Nov 2026 dari database pengembangan fai26_dev (API 0.21.0, migrasi sampai 012).
-- GANTI [api_fai26] di bawah dengan nama USER DATABASE milik login yang dipakai API (DB_USER di .env), lalu jalankan SETELAH login API dibuat.
-- Jalankan dengan: sqlcmd -S <server> -d <database> -U <akun> -I -b -f 65001 -i <berkas>

-- Migrasi 004 (11 Nov 2026): audit_log hanya boleh ditambah, tidak boleh diubah atau dihapus oleh akun API.
-- JALANKAN DI SERVER SUNGGUHAN OLEH DBA, SETELAH skema dan migrasi lain terpasang. TIDAK dijalankan di database
-- pengembangan: tes integrasi memang menghapus isi audit_log di database uji.
--
-- Ganti [api_fai26] dengan nama user database milik login yang dipakai API (DB_USER di .env). Bukan akun sa dan
-- bukan akun pemilik skema; akun API sebaiknya hanya punya SELECT, INSERT, UPDATE, DELETE pada tabel aplikasi
-- (db_datareader dan db_datawriter sudah cukup) ditambah EXECUTE bila perlu, lalu DENY di bawah ini membuat
-- audit_log menjadi hanya-tambah bagi akun itu.
--
-- Catatan: prosedur jembatan ERP (003) menulis audit_log atas nama pemanggilnya (login ERP), bukan akun API,
-- jadi tidak terpengaruh. Pemilik skema masih bisa mengubah audit_log; batasi akses akun itu secara terpisah.
SET QUOTED_IDENTIFIER ON;
GO

DENY UPDATE, DELETE ON dbo.audit_log TO [api_fai26];
GO

-- Pemeriksaan (jalankan sebagai akun API; keduanya HARUS gagal dengan "permission was denied"):
--   UPDATE dbo.audit_log SET keterangan = 'x' WHERE 1 = 0;
--   DELETE FROM dbo.audit_log WHERE 1 = 0;
-- Dan ini HARUS berhasil: INSERT INTO dbo.audit_log (...) VALUES (...);
