// PT-03 stress：50→300 人に段階的に増やし、限界点（エラーや遅れが増え始める人数）を見る（性能テスト設計書 3 章）
// 応答時間のしきい値を超えるのは想定のうち。どの段階で超えたかを結果から読む。
// 失敗が 10% を超えたら、それ以上は意味がないので止める
import type { Options } from 'k6/options'
import { userIteration } from '../lib/behavior.ts'
import { commonThresholds } from '../lib/config.ts'
import { minutes } from '../lib/time.ts'

export const options: Options = {
  stages: [
    { duration: minutes(2), target: 50 },
    { duration: minutes(3), target: 50 },
    { duration: minutes(2), target: 100 },
    { duration: minutes(3), target: 100 },
    { duration: minutes(2), target: 200 },
    { duration: minutes(3), target: 200 },
    { duration: minutes(2), target: 300 },
    { duration: minutes(3), target: 300 },
    { duration: minutes(2), target: 0 },
  ],
  thresholds: {
    ...commonThresholds,
    http_req_failed: [{ threshold: 'rate<0.1', abortOnFail: true, delayAbortEval: '30s' }],
  },
}

export default userIteration
