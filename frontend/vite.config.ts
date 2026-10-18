import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 開発中は API を Spring Boot に転送する。ブラウザから見て同じオリジンになるので、CORS の設定がいらない
    proxy: {
      '/api': 'http://localhost:8080',
      // 開発環境の画像（署名つき URL。API 設計書 4.8）も Spring Boot が返す
      '/media': 'http://localhost:8080',
    },
  },
  test: {
    // ブラウザの代わりに jsdom（DOM を真似る仕組み）で画面の部品を動かす
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // カバレッジの計測で動きが遅くなり、入力の多い画面のテストが既定の 5 秒を超えるため延ばす
    testTimeout: 15000,
    // カバレッジ（テストが通ったコードの割合。テスト仕様書 5 章）。npm run test で計測し、下回ったら失敗させる
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      // テストの道具と、起動するだけのファイルは対象外
      exclude: ['src/test/**', 'src/**/*.test.{ts,tsx}', 'src/main.tsx', 'src/**/*.d.ts'],
      // しきい値は 2026-10-18 の値（命令 92%・分岐 85%）を少し下回る線
      thresholds: { statements: 90, branches: 80 },
      reporter: ['text-summary', 'html'],
    },
  },
})
