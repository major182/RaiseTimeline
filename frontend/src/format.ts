// 日時・数の表示（画面設計書 1.3）。日時はサーバーから UTC で届くので、日本時間に直して表示する
import type { LikedVia } from './api/types'

const TIME_ZONE = 'Asia/Tokyo'
const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** 日本時間の年・月・日・時・分。 */
function partsInJapan(date: Date) {
  const parts = new Intl.DateTimeFormat('ja-JP', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return {
    year: Number(get('year')),
    month: Number(get('month')),
    day: Number(get('day')),
    hour: get('hour'),
    minute: get('minute'),
  }
}

/**
 * 一覧での投稿日時。1 分未満は「たった今」、1 時間未満は「N分」、24 時間未満は「N時間」、
 * 今年は「10月9日」、去年以前は「2025年10月9日」。
 */
export function formatPostTime(iso: string, now: Date = new Date()): string {
  const date = new Date(iso)
  const diff = Math.max(0, now.getTime() - date.getTime()) // 端末の時計のずれで未来になっても「たった今」にする
  if (diff < MINUTE) return 'たった今'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}分`
  if (diff < DAY) return `${Math.floor(diff / HOUR)}時間`
  const d = partsInJapan(date)
  if (d.year === partsInJapan(now).year) return `${d.month}月${d.day}日`
  return `${d.year}年${d.month}月${d.day}日`
}

/** 投稿の詳細での日時。「2026年10月9日 14:05」（日本時間）。 */
export function formatFullTime(iso: string): string {
  const d = partsInJapan(new Date(iso))
  return `${d.year}年${d.month}月${d.day}日 ${d.hour}:${d.minute}`
}

/** 利用開始。「2026年10月から利用」（日本時間。画面設計書 5.8）。 */
export function formatJoined(iso: string): string {
  const d = partsInJapan(new Date(iso))
  return `${d.year}年${d.month}月から利用`
}

/** いいね・コメント・フォローの数。1 万以上は「1.2万」（小数第 1 位まで。切り捨て）。 */
export function formatCount(count: number): string {
  if (count < 10_000) return String(count)
  const man = Math.floor(count / 1_000) / 10
  return `${man}万`
}

/** 「山田さんがいいねしました」「山田さん、ほか 2 人がいいねしました」（BR-50-3）。 */
export function likedViaText(likedVia: LikedVia): string {
  return likedVia.othersCount > 0
    ? `${likedVia.displayName}さん、ほか ${likedVia.othersCount} 人がいいねしました`
    : `${likedVia.displayName}さんがいいねしました`
}
