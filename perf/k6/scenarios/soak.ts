// PT-05 soak：30 人で 1 時間。応答時間が時間とともに悪くならないか、メモリ・DB の接続が枯れないか（性能テスト設計書 3 章）
// メモリと DB の接続は、scripts/perf.ts が実行中に metrics.jsonl へ記録する
import type { Options } from 'k6/options'
import { userIteration } from '../lib/behavior.ts'
import { commonThresholds } from '../lib/config.ts'
import { minutes } from '../lib/time.ts'

export const options: Options = {
  stages: [
    { duration: minutes(2), target: 30 },
    { duration: minutes(60), target: 30 },
    { duration: minutes(1), target: 0 },
  ],
  thresholds: commonThresholds,
}

export default userIteration

// 終了時に HTML のレポートと集計の表を書き出す
export { handleSummary } from '../lib/report.ts'
