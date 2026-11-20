# fAI26 Web Admin

Alat admin untuk sistem fAI26: kelola master (divisi, blok, TPH, pemanen), cetak QR TPH, pantau catatan panen dari HP krani, lihat ranking produktivitas pemanen per hari, kelola pengguna dan perangkat, dan lihat audit log.

Aplikasi satu halaman (SPA) yang berbicara dengan API di `../fai26-api` (dokumen: `../fai26-api/docs/API.md` dan `openapi.yaml`).

## Teknologi

React 18, TypeScript (strict), Vite 6, Ant Design 5, React Router 6, TanStack Query 5. Tes: Vitest, Testing Library, jsdom, jsQR.

## Menjalankan untuk pengembangan

Butuh Node 20+ dan API yang sedang jalan.

```bash
# terminal 1: API (lihat ../fai26-api/README.md untuk .env dan database)
cd ../fai26-api
go run ./cmd/api

# terminal 2: web
cd ../fai26-web
npm install
npm run dev          # http://localhost:5173
```

Vite meneruskan `/api/*` ke `http://localhost:8080`, jadi tidak ada pengaturan CORS. Akun dibuat admin di database yang dipakai API (tidak ada akun contoh bawaan).

## Perintah

| Perintah | Fungsi |
|---|---|
| `npm run dev` | Server pengembangan |
| `npm run build` | Periksa tipe lalu bangun ke `dist/` |
| `npm run typecheck` | Periksa tipe saja |
| `npm run lint` | ESLint (harus 0 galat dan 0 peringatan) |
| `npm test` | Semua tes |

## Struktur

```
src/
  api/          klien HTTP (refresh token otomatis), tipe sesuai API, daftar endpoint
  app/          App, router (halaman dimuat malas), tema terang/gelap
  components/   tata letak, menu menurut hak akses, komponen umum
  features/     satu folder per fitur: auth, dashboard, master, qr, panen, pengguna, perangkat, audit
  lib/          format angka/tanggal, daftar izin dan halaman, penanganan galat
  theme/        token Ant Design (hijau kebun, emas)
```

## Catatan konfigurasi TypeScript

`tsconfig.app.json` sengaja tidak memakai `baseUrl` (usang di TypeScript 6 dan dihapus di 7). Alias `@/*` cukup lewat `paths` relatif (`./src/*`) dan `resolve.alias` di `vite.config.ts`; jaga keduanya tetap sama.

## Aturan penting

- **Hak akses:** menu dan halaman mengikuti permission akun (`src/lib/izin.ts`). Server tetap pemeriksa akhir; web hanya menyembunyikan yang tidak boleh dibuka.
- **Token:** access token hanya di memori; refresh token di `localStorage` (`fai26.refresh`). Refresh token dirotasi tiap dipakai, jadi permintaan refresh dijalankan satu per satu.
- **QR TPH:** format `FAI26|TPH|1|<unit>|<blok>|<no>` dibuat server. Mengubah kode blok atau nomor TPH mengubah QR, dan QR yang sudah dicetak tidak berlaku lagi. Spesifikasi cetak: `../progres/SPEK_QR_TPH.md`.
- **Penghapusan master** selalu lunak (ditandai nonaktif).

## Produksi

Bangun dengan `npm run build`, lalu sajikan dari alamat yang sama dengan API (satu origin: tanpa CORS). Cara paling sederhana: isi `FAI26_WEB_DIR` di `.env` API dengan folder `dist/` ini, lalu jalankan API. API melayani `/api/*` dan web sekaligus (fallback `index.html` untuk rute React, cache panjang untuk `/assets`, header keamanan dan CSP). Alternatifnya reverse proxy yang meneruskan `/api` ke API dan sisanya ke `dist/` dengan fallback ke `index.html`. Pakai HTTPS di depan keduanya.

**Selalu coba hasil build di browser**, bukan hanya `npm run build`: build produksi dan mode dev bisa berbeda (pernah ada galat yang hanya muncul di produksi).
