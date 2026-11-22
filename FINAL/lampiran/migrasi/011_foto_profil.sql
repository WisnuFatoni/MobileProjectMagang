-- fAI26: foto profil akun. Berkasnya disimpan di folder media (profil/<user_id>.jpg, persegi 384 px); kolom ini hanya menyimpan
-- versinya (waktu ubah dalam milidetik) supaya HP dan web tahu kapan harus mengunduh ulang. NULL = belum punya foto.
-- Jalankan setelah 010_satu_kemandoran.sql. Aman dijalankan berulang.

IF COL_LENGTH('dbo.app_user', 'foto_profil_versi') IS NULL
    ALTER TABLE dbo.app_user ADD foto_profil_versi BIGINT NULL;
GO
