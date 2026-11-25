-- 03 HAK ROLE (WAJIB) : menyamakan role_permission dengan database pengembangan
-- Dibuat 22 Nov 2026 dari database pengembangan fai26_dev (API 0.21.0, migrasi sampai 012).
-- Jalankan SETELAH 01. PENTING: skema dasar memberi MANDOR hak panen.setujui; keputusan 19 Nov: yang menyetujui adalah KRANI, jadi hak itu harus dicabut
-- dari MANDOR. File ini menambah pasangan role-permission yang kurang dan MENCABUT (hapus lunak) yang tidak ada di daftar pengembangan.
-- Aman diulang.
-- Jalankan dengan: sqlcmd -S <server> -d <database> -U <akun> -I -b -f 65001 -i <berkas>

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @dev TABLE (role_kode NVARCHAR(30), perm_kode NVARCHAR(50));
INSERT INTO @dev (role_kode, perm_kode) VALUES
  (N'ADMIN', N'device.kelola'),
  (N'ADMIN', N'grading.ubah'),
  (N'ADMIN', N'ipd.jejak'),
  (N'ADMIN', N'ipd.kelola'),
  (N'ADMIN', N'ipd.lihat'),
  (N'ADMIN', N'laporan.ipd'),
  (N'ADMIN', N'laporan.lihat'),
  (N'ADMIN', N'master.kelola'),
  (N'ADMIN', N'master.lihat'),
  (N'ADMIN', N'model.kelola'),
  (N'ADMIN', N'panen.buat'),
  (N'ADMIN', N'panen.hapus'),
  (N'ADMIN', N'panen.kirim'),
  (N'ADMIN', N'panen.koreksi'),
  (N'ADMIN', N'panen.lihat'),
  (N'ADMIN', N'panen.setujui'),
  (N'ADMIN', N'panen.ubah'),
  (N'ADMIN', N'role.kelola'),
  (N'ADMIN', N'user.kelola'),
  (N'ASISTEN', N'akses.ipd'),
  (N'ASISTEN', N'akses.panen'),
  (N'ASISTEN', N'impor.erp'),
  (N'ASISTEN', N'ipd.buat'),
  (N'ASISTEN', N'ipd.jejak'),
  (N'ASISTEN', N'ipd.kirim'),
  (N'ASISTEN', N'ipd.lihat'),
  (N'ASISTEN', N'ipd.setujui'),
  (N'ASISTEN', N'laporan.ipd'),
  (N'ASISTEN', N'laporan.lihat'),
  (N'ASISTEN', N'master.lihat'),
  (N'ASISTEN', N'panen.hapus'),
  (N'ASISTEN', N'panen.koreksi'),
  (N'ASISTEN', N'panen.lihat'),
  (N'ASKEP', N'ipd.jejak'),
  (N'ASKEP', N'ipd.lihat'),
  (N'ASKEP', N'laporan.ipd'),
  (N'ASKEP', N'laporan.lihat'),
  (N'ASKEP', N'master.lihat'),
  (N'ASKEP', N'panen.lihat'),
  (N'AUDITOR', N'audit.lihat'),
  (N'AUDITOR', N'ipd.lihat'),
  (N'AUDITOR', N'laporan.ipd'),
  (N'AUDITOR', N'laporan.lihat'),
  (N'AUDITOR', N'master.lihat'),
  (N'AUDITOR', N'panen.lihat'),
  (N'KRANI', N'akses.ipd'),
  (N'KRANI', N'akses.panen'),
  (N'KRANI', N'grading.ubah'),
  (N'KRANI', N'ipd.buat'),
  (N'KRANI', N'ipd.kirim'),
  (N'KRANI', N'ipd.lihat'),
  (N'KRANI', N'master.lihat'),
  (N'KRANI', N'panen.buat'),
  (N'KRANI', N'panen.kirim'),
  (N'KRANI', N'panen.lihat'),
  (N'KRANI', N'panen.setujui'),
  (N'KRANI', N'panen.ubah'),
  (N'KTU', N'ipd.jejak'),
  (N'KTU', N'ipd.lihat'),
  (N'KTU', N'laporan.ipd'),
  (N'KTU', N'laporan.lihat'),
  (N'KTU', N'master.lihat'),
  (N'KTU', N'panen.lihat'),
  (N'MANDOR', N'akses.ipd'),
  (N'MANDOR', N'akses.panen'),
  (N'MANDOR', N'grading.ubah'),
  (N'MANDOR', N'ipd.buat'),
  (N'MANDOR', N'ipd.kirim'),
  (N'MANDOR', N'ipd.lihat'),
  (N'MANDOR', N'laporan.lihat'),
  (N'MANDOR', N'master.lihat'),
  (N'MANDOR', N'panen.buat'),
  (N'MANDOR', N'panen.hapus'),
  (N'MANDOR', N'panen.kirim'),
  (N'MANDOR', N'panen.koreksi'),
  (N'MANDOR', N'panen.lihat'),
  (N'MANDOR', N'panen.ubah');

-- 1) tambah yang kurang
INSERT INTO dbo.role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM @dev d JOIN dbo.role r ON r.kode=d.role_kode JOIN dbo.permission p ON p.kode=d.perm_kode
WHERE NOT EXISTS (SELECT 1 FROM dbo.role_permission z WHERE z.role_id=r.role_id AND z.permission_id=p.permission_id AND z.dihapus_pada IS NULL);

-- 2) cabut yang tidak ada di daftar (hapus lunak; hanya untuk role yang ada di daftar)
UPDATE rp SET dihapus_pada = SYSUTCDATETIME(), diubah_pada = SYSUTCDATETIME()
FROM dbo.role_permission rp JOIN dbo.role r ON r.role_id=rp.role_id JOIN dbo.permission p ON p.permission_id=rp.permission_id
WHERE rp.dihapus_pada IS NULL AND r.kode IN (SELECT DISTINCT role_kode FROM @dev)
  AND NOT EXISTS (SELECT 1 FROM @dev d WHERE d.role_kode=r.kode AND d.perm_kode=p.kode);
GO

-- hasil: jumlah hak aktif per role
SELECT r.kode AS role, COUNT(*) AS jumlah_hak FROM dbo.role_permission rp JOIN dbo.role r ON r.role_id=rp.role_id WHERE rp.dihapus_pada IS NULL GROUP BY r.kode ORDER BY r.kode;
GO
