import { describe, expect, it } from 'vitest'
import { formatCount, formatFullTime, formatPostTime } from './format'

// 基準の時刻：2026年10月9日 14:05（日本時間）
const NOW = new Date('2026-10-09T05:05:00Z')

describe('一覧の投稿日時（画面設計書 1.3）', () => {
  it('1 分未満は「たった今」', () => {
    expect(formatPostTime('2026-10-09T05:04:01Z', NOW)).toBe('たった今')
    expect(formatPostTime('2026-10-09T05:05:00Z', NOW)).toBe('たった今')
  })

  it('端末の時計がずれて未来の時刻になっても「たった今」', () => {
    expect(formatPostTime('2026-10-09T05:10:00Z', NOW)).toBe('たった今')
  })

  it('1 時間未満は「N分」', () => {
    expect(formatPostTime('2026-10-09T05:04:00Z', NOW)).toBe('1分')
    expect(formatPostTime('2026-10-09T04:05:01Z', NOW)).toBe('59分')
  })

  it('24 時間未満は「N時間」', () => {
    expect(formatPostTime('2026-10-09T04:05:00Z', NOW)).toBe('1時間')
    expect(formatPostTime('2026-10-08T05:05:01Z', NOW)).toBe('23時間')
  })

  it('今年なら「月日」を日本時間で出す', () => {
    // UTC では 10月7日 20時だが、日本時間では 10月8日 5時
    expect(formatPostTime('2026-10-07T20:00:00Z', NOW)).toBe('10月8日')
    expect(formatPostTime('2026-01-01T00:00:00Z', NOW)).toBe('1月1日')
  })

  it('去年以前なら「年月日」', () => {
    expect(formatPostTime('2025-10-09T00:00:00Z', NOW)).toBe('2025年10月9日')
    // UTC では 2025年の大みそかだが、日本時間では 2026年1月1日
    expect(formatPostTime('2025-12-31T16:00:00Z', NOW)).toBe('1月1日')
  })
})

describe('詳細の投稿日時', () => {
  it('日本時間で「年月日 時:分」', () => {
    expect(formatFullTime('2026-10-09T05:05:00Z')).toBe('2026年10月9日 14:05')
    expect(formatFullTime('2026-10-08T15:00:00Z')).toBe('2026年10月9日 0:00')
  })
})

describe('数の表示', () => {
  it('1 万未満はそのまま、0 も出す', () => {
    expect(formatCount(0)).toBe('0')
    expect(formatCount(9999)).toBe('9999')
  })

  it('1 万以上は「万」で小数第 1 位まで（切り捨て）', () => {
    expect(formatCount(10_000)).toBe('1万')
    expect(formatCount(12_345)).toBe('1.2万')
    expect(formatCount(19_999)).toBe('1.9万')
    expect(formatCount(1_000_000)).toBe('100万')
  })
})
