import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Pengembangan: /api diteruskan ke fai26-api (go run ./cmd/api, port 8080) sehingga tidak perlu CORS.
// Produksi: sajikan hasil build dari origin yang sama dengan API (reverse proxy atau FAI26_WEB_DIR).
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5173,
    proxy: { '/api': { target: process.env.FAI26_API ?? 'http://localhost:8080', changeOrigin: true } },
  },
  build: {
    // Tanpa manualChunks: pemecahan manual bentuk objek memisahkan React dari helper CommonJS-nya dan membuat
    // build produksi kosong ("Cannot set properties of undefined (setting 'Children')"). Halaman sudah dimuat malas.
    chunkSizeWarningLimit: 1500,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Tes yang memuat Ant Design (login, dialog, tata letak) memakan beberapa detik di jsdom, apalagi di laptop yang sedang sibuk.
    testTimeout: 30_000,
  },
})
