// PT-04 spike：10 人から 30 秒で 200 人に急増し、元に戻ったあと回復するかを見る（性能テスト設計書 3 章）
// 急増の最中の遅れは想定のうち。合否は失敗の割合と、戻ったあとの応答時間（recovery のタグ）で見る
import type { Options } from 'k6/options'
import { userIteration } from '../lib/behavior.ts'
import { minutes, seconds } from '../lib/time.ts'

export const options: Options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: minutes(1), target: 10 },
        { duration: seconds(30), target: 200 },
        { duration: minutes(2), target: 200 },
        { duration: seconds(30), target: 10 },
      ],
      tags: { phase: 'spike' },
    },
    // 急増のあとの 10 人：ここの応答時間が通常の値に戻っていれば回復した
    recovery: {
      executor: 'constant-vus',
      vus: 10,
      duration: minutes(3),
      startTime: minutes(4),
      tags: { phase: 'recovery' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
    'http_req_duration{phase:recovery,kind:read}': ['p(95)<300'],
    'http_req_duration{phase:recovery,kind:write}': ['p(95)<500'],
  },
}

export default userIteration
