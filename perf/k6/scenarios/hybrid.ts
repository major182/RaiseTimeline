// PT-08 hybrid：PT-02 と同じ API の負荷（50 人）をかけながら、ブラウザ 3 人で PT-07 と同じ操作を行う。
// 負荷がかかった状態で、利用者が感じる速さが PT-07 の基準の値からどこまで落ちるかを見る（性能テスト設計書 3 章）
// ブラウザは 1 人あたりの CPU・メモリが大きいので、負荷は API で作り、ブラウザは少数で体感を測る役にする
import type { Options } from 'k6/options'
import { userIteration } from '../lib/behavior.ts'
import { browserIteration, browserThresholds } from '../lib/browser-flow.ts'
import { commonThresholds } from '../lib/config.ts'
import { minutes } from '../lib/time.ts'

export const options: Options = {
  scenarios: {
    api: {
      executor: 'ramping-vus',
      exec: 'apiUser',
      stages: [
        { duration: minutes(2), target: 50 },
        { duration: minutes(10), target: 50 },
        { duration: minutes(1), target: 0 },
      ],
    },
    // API の負荷が 50 人に達してから、10 分の間ブラウザで操作する
    browser: {
      executor: 'constant-vus',
      exec: 'browserUser',
      vus: 3,
      duration: minutes(10),
      startTime: minutes(2),
      options: { browser: { type: 'chromium' } },
    },
  },
  thresholds: {
    ...commonThresholds,
    ...browserThresholds,
    // 確認（check）の割合は、API とブラウザで分けて判定する。合わせると、件数の多い API の成功にブラウザの失敗が埋もれる
    'checks{scenario:api}': ['rate>0.99'],
    'checks{scenario:browser}': ['rate>0.99'],
  },
}

export function apiUser(): void {
  userIteration()
}

export async function browserUser(): Promise<void> {
  await browserIteration()
}

// 終了時に HTML のレポートと集計の表を書き出す
export { handleSummary } from '../lib/report.ts'
