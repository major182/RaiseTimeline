// PT-07 browser：ブラウザ 1 人で一連の操作を 5 回。ほかの負荷がない状態の、利用者が感じる速さの基準の値（性能テスト設計書 3 章）
import type { Options } from 'k6/options'
import { browserIteration, browserThresholds } from '../lib/browser-flow.ts'

export const options: Options = {
  scenarios: {
    browser: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 5,
      maxDuration: '10m',
      options: { browser: { type: 'chromium' } },
    },
  },
  thresholds: {
    ...browserThresholds,
    checks: ['rate>0.99'],
  },
}

export default browserIteration

// 終了時に HTML のレポートと集計の表を書き出す
export { handleSummary } from '../lib/report.ts'
