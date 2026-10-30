import { defineConfig, devices } from '@playwright/test'

// E2E テスト（テスト仕様書 1 章）。ブラウザで画面を操作し、画面・API・DB を通して主な利用の流れを確かめる。
// npm run e2e で、E2E 用の DB（compose の db-e2e）を起動してから動かす。
// 開発中のサーバー（API 8080・画面 5173・DB 5432）とぶつからないよう、別のポートを使う。
// すでに動いているサーバーは使い回さない（同じポートのほかのアプリを、誤ってテストしないため）
const API_PORT = 8091
const WEB_PORT = 5191
const isCI = !!process.env.CI

export default defineConfig({
  testDir: './e2e',
  // 画面の操作は 1 つずつ順に行う（同じ DB を使うため。テストどうしは別の利用者を作るので順番には頼らない）
  workers: 1,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  timeout: 60_000,
  // 結果を HTML のレポート（playwright-report/）にも残す。npx playwright show-report で開ける
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    // 失敗したときだけ、操作の記録（トレース）とスクリーンショットを残す
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // API：開発の設定（dev。開発用のテストデータが入る）で、E2E 用の DB につなぐ
      command: process.platform === 'win32' ? '.\\gradlew.bat bootRun' : './gradlew bootRun',
      cwd: '../backend',
      url: `http://localhost:${API_PORT}/actuator/health`,
      env: {
        SERVER_PORT: String(API_PORT),
        DB_URL: 'jdbc:postgresql://localhost:5433/raisetimeline',
        STORAGE_LOCAL_DIR: './build/e2e-media',
      },
      timeout: 240_000,
    },
    {
      // 画面：Vite の開発サーバー。/api・/media を上の API に転送する
      command: `npx vite --port ${WEB_PORT} --strictPort`,
      url: `http://localhost:${WEB_PORT}`,
      env: { API_ORIGIN: `http://localhost:${API_PORT}` },
      timeout: 60_000,
    },
  ],
})
