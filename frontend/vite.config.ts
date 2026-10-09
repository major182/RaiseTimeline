import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 開発中は API を Spring Boot に転送する。ブラウザから見て同じオリジンになるので、CORS の設定がいらない
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    // ブラウザの代わりに jsdom（DOM を真似る仕組み）で画面の部品を動かす
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
