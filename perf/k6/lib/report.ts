// 終了時の結果の書き出し（性能テスト設計書 7 章）。各シナリオが handleSummary として再び書き出す
//   report.html：k6-reporter による HTML のレポート（しきい値・checks の合否と、API ごとの応答時間の表）
//   標準出力：今までどおりの集計の表（handleSummary を書くと k6 の既定の表が出なくなるため、同じものを出す）
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/3.0.4/dist/bundle.js'
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js'

export function handleSummary(data: object): Record<string, string> {
  return {
    '/results/report.html': htmlReport(data, {
      title: `RaiseTimeline ${__ENV.SCENARIO ?? ''} ${new Date().toISOString()}`,
    }),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  }
}
