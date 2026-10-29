// PT-02 load：想定ピーク（50 人）で 10 分。しきい値を満たすか（性能テスト設計書 3 章）
import type { Options } from 'k6/options'
import { userIteration } from '../lib/behavior.ts'
import { commonThresholds } from '../lib/config.ts'
import { minutes } from '../lib/time.ts'

export const options: Options = {
  stages: [
    { duration: minutes(2), target: 50 },
    { duration: minutes(10), target: 50 },
    { duration: minutes(1), target: 0 },
  ],
  thresholds: commonThresholds,
}

export default userIteration

// 終了時に HTML のレポートと集計の表を書き出す
export { handleSummary } from '../lib/report.ts'
