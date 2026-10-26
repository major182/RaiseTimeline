// シナリオの時間。TIME_SCALE（scripts/perf.ts の --short で 0.1）を掛けて、動作確認のときだけ短くする。
// 縮めると負荷のたまり方・回復を見る時間が本来と変わるので、縮めた結果は性能の合否に使わない
const TIME_SCALE = Number(__ENV.TIME_SCALE || 1)

// 秒数を k6 の時間の書き方（例：'600s'）にする。短くしても 1 秒は残す
export function seconds(value: number): string {
  return `${Math.max(1, Math.round(value * TIME_SCALE))}s`
}

export function minutes(value: number): string {
  return seconds(value * 60)
}
