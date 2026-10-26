// 性能テストの共通の設定（性能テスト設計書 3 章）。値は環境変数で上書きできる
//   BASE_URL   対象の API（既定は scripts/perf.ts が起動する API）
//   USER_COUNT テストデータの利用者の数（perf/seed/seed.sql と合わせる）
import type { Options } from 'k6/options'

export const BASE_URL: string = __ENV.BASE_URL || 'http://host.docker.internal:8092'
export const USER_COUNT = Number(__ENV.USER_COUNT || 1000)
export const PASSWORD = 'pass1234'

// 全シナリオ共通の合否のしきい値（性能テスト設計書 6 章）。シナリオごとに足してよい
export const commonThresholds: NonNullable<Options['thresholds']> = {
  // 失敗（4xx・5xx・接続の失敗）が 1% 未満
  http_req_failed: ['rate<0.01'],
  // 応答の中身の確認（check）が 99% 以上通る
  checks: ['rate>0.99'],
  // 読み込み（タイムライン・一覧）は p95 300ms 未満、書き込みは p95 500ms 未満
  'http_req_duration{kind:read}': ['p(95)<300'],
  'http_req_duration{kind:write}': ['p(95)<500'],
}
