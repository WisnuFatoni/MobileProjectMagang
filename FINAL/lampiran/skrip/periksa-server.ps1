<#
.SYNOPSIS
  Memeriksa pemasangan fai26-api di sebuah server: konfigurasi, database, migrasi, hak akun API, dan API yang berjalan.

.DESCRIPTION
  Hanya MEMBACA: tidak mengubah database, berkas, atau pengaturan server, dan tidak mencetak kata sandi atau kunci.
  Jalankan dari folder pemasangan (yang berisi .env) atau beri -Folder. Setiap pemeriksaan dicetak [OK], [PERINGATAN],
  atau [GAGAL]; kode keluar = jumlah [GAGAL] (0 berarti siap). Butuh Windows PowerShell 5.1 atau lebih baru dan
  System.Data.SqlClient (sudah ada di Windows); tidak butuh sqlcmd.

  Pemeriksaan database memakai akun yang sama dengan API (DB_USER), jadi sekaligus membuktikan bahwa akun itu bisa masuk
  dan hanya punya hak yang semestinya. Bila .env memakai FAI26_DSN (bukan DB_*), pemeriksaan database dilewati.

.EXAMPLE
  .\scripts\periksa-server.ps1 -Folder C:\fai26
  .\scripts\periksa-server.ps1 -Folder C:\fai26 -Alamat https://fai26.contoh-kebun.co.id
#>
param(
    [string]$Folder = (Get-Location).Path,
    # Alamat dasar API untuk pemeriksaan kesehatan; bawaannya dari FAI26_ADDR di .env.
    [string]$Alamat = ''
)

$ErrorActionPreference = 'Stop'
$script:gagal = 0
$script:peringatan = 0
function Ok($t)    { Write-Host "[OK]         $t" -ForegroundColor Green }
function Gagal($t) { Write-Host "[GAGAL]      $t" -ForegroundColor Red; $script:gagal++ }
function Awas($t)  { Write-Host "[PERINGATAN] $t" -ForegroundColor Yellow; $script:peringatan++ }
function Judul($t) { Write-Host ""; Write-Host "== $t" -ForegroundColor Cyan }

# ---- .env -------------------------------------------------------------------------------------------------------
Judul 'Konfigurasi'
$envFile = Join-Path $Folder '.env'
$kv = @{}
if (-not (Test-Path $envFile)) {
    Gagal ".env tidak ditemukan di $Folder (API membacanya dari folder kerjanya)"
} else {
    Ok ".env ada di $Folder"
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Z0-9_]+)\s*=\s*"?(.*?)"?\s*$') { $kv[$Matches[1]] = $Matches[2] }
    }
}
function Nilai($k, $bawaan = '') { if ($kv.ContainsKey($k) -and $kv[$k] -ne '') { $kv[$k] } else { $bawaan } }

$exe = Get-ChildItem -Path $Folder -Filter 'fai26-api*.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($exe) { Ok "program API: $($exe.Name) ($([math]::Round($exe.Length/1MB,1)) MB, dibuat $($exe.LastWriteTime.ToString('yyyy-MM-dd HH:mm')))" } else { Gagal "fai26-api.exe tidak ada di $Folder" }

$jwt = Nilai 'FAI26_JWT_SECRET'
if ($jwt.Length -lt 32) { Gagal 'FAI26_JWT_SECRET kosong atau kurang dari 32 karakter' }
elseif ($jwt -match 'ganti|contoh|example|rahasia|secret') { Awas 'FAI26_JWT_SECRET tampak seperti nilai contoh; ganti dengan teks acak' }
else { Ok "FAI26_JWT_SECRET terisi ($($jwt.Length) karakter)" }

if ((Nilai 'DB_USER') -eq 'sa') { Gagal 'DB_USER adalah sa; pakai login khusus API (db_datareader dan db_datawriter)' }
if ((Nilai 'DB_ENCRYPT') -ne 'true') { Awas 'DB_ENCRYPT bukan true: sambungan ke SQL Server tidak dienkripsi (boleh bila server database di mesin yang sama)' } else { Ok 'DB_ENCRYPT=true' }
if ((Nilai 'DB_TRUST_CERT') -eq 'true') { Awas 'DB_TRUST_CERT=true: sertifikat SQL Server tidak diverifikasi (terima hanya bila Anda tahu risikonya)' }

$alamatDengar = Nilai 'FAI26_ADDR' ':8080'
if ($alamatDengar -match '^:' -or $alamatDengar -match '^0\.0\.0\.0') {
    if ((Nilai 'FAI26_PERCAYAI_PROXY') -eq 'true') { Gagal "FAI26_ADDR=$alamatDengar mendengar di semua antarmuka padahal FAI26_PERCAYAI_PROXY=true; header X-Forwarded-For bisa dipalsukan. Pakai 127.0.0.1:port di belakang proxy" }
    else { Awas "FAI26_ADDR=$alamatDengar mendengar di semua antarmuka (HTTP tanpa enkripsi); di produksi pakai 127.0.0.1:port di belakang reverse proxy HTTPS" }
} else { Ok "FAI26_ADDR=$alamatDengar" }

$media = Nilai 'FAI26_MEDIA_DIR' 'media'
$mediaPath = if ([System.IO.Path]::IsPathRooted($media)) { $media } else { Join-Path $Folder $media }
if (-not (Test-Path $mediaPath)) { Awas "folder foto $mediaPath belum ada (dibuat API saat foto pertama; pastikan akun layanan boleh menulis di sana)" }
else {
    try { $uji = Join-Path $mediaPath ('.periksa_' + [guid]::NewGuid().ToString('N')); [IO.File]::WriteAllText($uji, 'x'); Remove-Item $uji; Ok "folder foto bisa ditulis: $mediaPath" }
    catch { Gagal "folder foto $mediaPath tidak bisa ditulis oleh akun yang menjalankan pemeriksaan ini" }
}

$web = Nilai 'FAI26_WEB_DIR'
if ($web) {
    $webPath = if ([System.IO.Path]::IsPathRooted($web)) { $web } else { Join-Path $Folder $web }
    if (Test-Path (Join-Path $webPath 'index.html')) { Ok "web admin ada di $webPath" } else { Gagal "FAI26_WEB_DIR=$web tetapi index.html tidak ada (jalankan npm run build lalu salin isi dist)" }
} else { Awas 'FAI26_WEB_DIR kosong: API tidak menyajikan web admin (benar bila web dilayani proxy)' }

$simpan = [int](Nilai 'FAI26_JEJAK_SIMPAN_HARI' '0')
if ($simpan -le 0) { Awas 'FAI26_JEJAK_SIMPAN_HARI=0: jalur GPS petugas disimpan tanpa batas waktu (keputusan kebijakan, lihat docs/DEPLOY.md bagian 7)' } else { Ok "jalur GPS dihapus setelah $simpan hari" }

# ---- Database ---------------------------------------------------------------------------------------------------
Judul 'Database'
$cs = $null
if (Nilai 'FAI26_DSN') {
    Awas 'FAI26_DSN dipakai: pemeriksaan database dilewati (skrip ini membaca DB_*)'
} elseif (-not ((Nilai 'DB_HOST') -and (Nilai 'DB_NAME') -and (Nilai 'DB_USER'))) {
    Gagal 'DB_HOST, DB_NAME, atau DB_USER belum diisi di .env'
} else {
    $b = New-Object System.Data.SqlClient.SqlConnectionStringBuilder
    $b['Data Source'] = (Nilai 'DB_HOST') + ',' + (Nilai 'DB_PORT' '1433')
    $b['Initial Catalog'] = Nilai 'DB_NAME'
    $b['User ID'] = Nilai 'DB_USER'
    $b['Password'] = Nilai 'DB_PASSWORD'
    $b['Encrypt'] = ((Nilai 'DB_ENCRYPT') -eq 'true')
    $b['TrustServerCertificate'] = ((Nilai 'DB_TRUST_CERT') -eq 'true')
    $b['Connect Timeout'] = 10
    $cs = $b.ConnectionString
}

function Skalar($koneksi, $sql) {
    $c = $koneksi.CreateCommand(); $c.CommandText = $sql; $c.CommandTimeout = 20
    $c.ExecuteScalar()
}

if ($cs) {
    $kon = New-Object System.Data.SqlClient.SqlConnection $cs
    try {
        $kon.Open()
        Ok "tersambung ke $(Nilai 'DB_NAME') sebagai $(Skalar $kon 'SELECT SUSER_NAME()')"
    } catch {
        Gagal "tidak bisa tersambung ke database: $($_.Exception.Message)"
        $kon = $null
    }
    if ($kon) {
        # Setiap migrasi diwakili satu objek yang dibuat atau diubahnya.
        $migrasi = @(
            @('skema dasar (erd/fAI26_server_sqlserver.sql)', "SELECT COUNT(*) FROM sys.tables WHERE name IN ('app_user','role','permission','fai_panen','audit_log','device','unit')", 7),
            @('002 master', "SELECT COUNT(*) FROM sys.tables WHERE name IN ('divisi','blok','tph','pemanen')", 4),
            @('003 jembatan ERP', "SELECT COUNT(*) FROM sys.objects WHERE name IN ('v_fai_panen_siap_impor','sp_fai_impor_ok')", 2, $true),
            @('005 satu pemanen', "SELECT CASE WHEN COL_LENGTH('dbo.fai_panen','mandor_hrmb_code') IS NOT NULL THEN 1 ELSE 0 END", 1),
            @('006 peran HVDM', "SELECT COUNT(*) FROM sys.database_principals WHERE name='fai26_hvdm' AND type='R'", 1, $true),
            @('007 mandor input', "SELECT CASE WHEN COL_LENGTH('dbo.fai_panen','pemanen_hrmb_code') IS NOT NULL AND COL_LENGTH('dbo.app_user','mandor_hrmb_code') IS NOT NULL THEN 1 ELSE 0 END", 1),
            @('008 menunggu persetujuan', "SELECT COUNT(*) FROM sys.check_constraints WHERE name='ck_fai_panen_status_erp'", 1),
            @('009 IPD', "SELECT COUNT(*) FROM sys.tables WHERE name IN ('ipd_kode_temuan','fai_ipd','fai_ipd_pokok','fai_ipd_temuan','fai_ipd_foto')", 5),
            @('010 satu kemandoran', "SELECT CASE WHEN COL_LENGTH('dbo.fai_ipd','mandor_user_id') IS NOT NULL AND COL_LENGTH('dbo.fai_ipd','mandor_ancak_user_id') IS NULL THEN 1 ELSE 0 END", 1),
            @('011 foto profil', "SELECT CASE WHEN COL_LENGTH('dbo.app_user','foto_profil_versi') IS NOT NULL THEN 1 ELSE 0 END", 1),
            @('012 jalur GPS', "SELECT CASE WHEN OBJECT_ID('dbo.fai_ipd_jejak','U') IS NOT NULL AND COL_LENGTH('dbo.fai_ipd_pokok','dicatat_pada') IS NOT NULL THEN 1 ELSE 0 END", 1)
        )
        foreach ($m in $migrasi) {
            try {
                $n = [int](Skalar $kon $m[1])
                if ($n -ge $m[2]) { Ok "migrasi $($m[0])" }
                elseif ($m.Count -gt 3 -and $m[3]) { Awas "migrasi $($m[0]) tidak terlihat oleh akun API (prosedur dan peran memang tersembunyi dari akun yang tidak berhak); periksa sebagai DBA: lihat docs/DEPLOY.md bagian 5" }
                else { Gagal "migrasi $($m[0]) belum terpasang (ditemukan $n dari $($m[2]) objek)" }
            } catch { Gagal "migrasi $($m[0]) tidak bisa diperiksa: $($_.Exception.Message)" }
        }

        # Hak akun API: tidak boleh mengubah audit_log, tidak boleh membuat objek, bukan sysadmin atau db_owner.
        try {
            $upd = [int](Skalar $kon "SELECT HAS_PERMS_BY_NAME('dbo.audit_log','OBJECT','UPDATE')")
            $del = [int](Skalar $kon "SELECT HAS_PERMS_BY_NAME('dbo.audit_log','OBJECT','DELETE')")
            if ($upd -eq 0 -and $del -eq 0) { Ok 'akun API tidak boleh UPDATE atau DELETE pada audit_log (migrasi 004)' }
            else { Gagal 'akun API masih boleh mengubah atau menghapus audit_log: jalankan migrasi 004 dengan nama user API, dan pastikan bukan db_owner' }
            if ([int](Skalar $kon "SELECT IS_SRVROLEMEMBER('sysadmin')") -eq 1 -or [int](Skalar $kon "SELECT IS_MEMBER('db_owner')") -eq 1) { Gagal 'akun API adalah sysadmin atau db_owner; pakai login dengan db_datareader dan db_datawriter saja' }
            else { Ok 'akun API bukan sysadmin dan bukan db_owner' }
            if ([int](Skalar $kon "SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE')") -eq 1) { Awas 'akun API boleh membuat tabel; hak seharusnya hanya baca dan tulis data' }
        } catch { Gagal "hak akun API tidak bisa diperiksa: $($_.Exception.Message)" }

        # Data awal.
        try {
            $unit = [int](Skalar $kon 'SELECT COUNT(*) FROM dbo.unit WHERE aktif=1 AND dihapus_pada IS NULL')
            if ($unit -ge 1) { Ok "unit aktif: $unit" } else { Gagal 'belum ada unit aktif (jalankan scripts/awal_unit_admin.sql)' }
            $adm = [int](Skalar $kon @"
SELECT COUNT(DISTINCT u.user_id) FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.user_id AND ur.dihapus_pada IS NULL
JOIN dbo.role r ON r.role_id=ur.role_id WHERE r.kode='ADMIN' AND u.aktif=1 AND u.dihapus_pada IS NULL
"@)
            if ($adm -ge 1) { Ok "akun ADMIN aktif: $adm" } else { Gagal 'belum ada akun ADMIN aktif (jalankan scripts/awal_unit_admin.sql)' }
            $ganti = [int](Skalar $kon "SELECT COUNT(*) FROM dbo.app_user WHERE wajib_ganti_password=1 AND aktif=1 AND dihapus_pada IS NULL")
            if ($ganti -gt 0) { Awas "$ganti akun aktif masih wajib mengganti kata sandi (normal sebelum login pertama)" }
            $blok = [int](Skalar $kon 'SELECT COUNT(*) FROM dbo.blok WHERE dihapus_pada IS NULL')
            if ($blok -eq 0) { Awas 'belum ada blok; isi master (blok, TPH, pemanen) lewat web admin sebelum HP dipakai' } else { Ok "blok: $blok" }
        } catch { Gagal "data awal tidak bisa diperiksa: $($_.Exception.Message)" }
        $kon.Close()
    }
}

# ---- API yang berjalan ------------------------------------------------------------------------------------------
Judul 'API yang berjalan'
if (-not $Alamat) {
    $hp = $alamatDengar -replace '^0\.0\.0\.0', '127.0.0.1'
    if ($hp -match '^:') { $hp = '127.0.0.1' + $hp }
    $Alamat = "http://$hp"
}
$Alamat = $Alamat.TrimEnd('/')
function Ambil($url) {
    try { $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10; return $r }
    catch { if ($_.Exception.Response) { return [pscustomobject]@{ StatusCode = [int]$_.Exception.Response.StatusCode; Content = '' } } else { return $null } }
}
$h = Ambil "$Alamat/api/health"
if ($null -eq $h) { Gagal "tidak ada jawaban dari $Alamat/api/health (API belum berjalan, atau alamat salah)" }
elseif ($h.StatusCode -eq 200) { Ok "GET $Alamat/api/health menjawab 200 (server dan database hidup)" }
else { Gagal "GET $Alamat/api/health menjawab $($h.StatusCode)" }
if ($web -and $h) {
    $w = Ambil "$Alamat/"
    if ($w -and $w.StatusCode -eq 200 -and $w.Content -match '<div id="root"|<title>') { Ok 'web admin terbuka di alamat dasar' } else { Gagal 'web admin tidak terbuka di alamat dasar' }
}
if ($Alamat -like 'http://*' -and $Alamat -notmatch '127\.0\.0\.1|localhost') { Awas 'alamat memakai http (bukan https); HP versi rilis hanya mau berbicara HTTPS' }

Write-Host ""
if ($script:gagal -eq 0) { Write-Host "Hasil: siap ($($script:peringatan) peringatan)." -ForegroundColor Green }
else { Write-Host "Hasil: $($script:gagal) pemeriksaan GAGAL, $($script:peringatan) peringatan." -ForegroundColor Red }
exit $script:gagal
