-- fAI26: IPD memakai SATU kemandoran (bukan mandor di blok dan mandor di ancak).
-- Kolom mandor_blok_* diganti namanya menjadi mandor_*, kolom mandor_ancak_* dibuang. Aman dijalankan berulang.
-- Jalankan setelah 009_ipd.sql.

IF COL_LENGTH('dbo.fai_ipd', 'mandor_ancak_user_id') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_fai_ipd_mandor_ancak')
        ALTER TABLE dbo.fai_ipd DROP CONSTRAINT fk_fai_ipd_mandor_ancak;
    ALTER TABLE dbo.fai_ipd DROP COLUMN mandor_ancak_user_id;
END
GO

IF COL_LENGTH('dbo.fai_ipd', 'mandor_ancak_hrmb') IS NOT NULL
    ALTER TABLE dbo.fai_ipd DROP COLUMN mandor_ancak_hrmb;
GO

IF COL_LENGTH('dbo.fai_ipd', 'mandor_blok_user_id') IS NOT NULL
    EXEC sp_rename 'dbo.fai_ipd.mandor_blok_user_id', 'mandor_user_id', 'COLUMN';
GO

IF COL_LENGTH('dbo.fai_ipd', 'mandor_blok_hrmb') IS NOT NULL
    EXEC sp_rename 'dbo.fai_ipd.mandor_blok_hrmb', 'mandor_hrmb', 'COLUMN';
GO

IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'fk_fai_ipd_mandor_blok')
    EXEC sp_rename 'dbo.fk_fai_ipd_mandor_blok', 'fk_fai_ipd_mandor', 'OBJECT';
GO
