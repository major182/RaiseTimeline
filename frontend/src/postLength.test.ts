import { describe, expect, it } from 'vitest'
import { BODY_MAX_LENGTH, countChars } from './postLength'

describe('投稿・コメントの文字数（BR-10、DB 設計書 D-7）', () => {
  it('UT-PO-01 上限は 280 文字', () => {
    expect(BODY_MAX_LENGTH).toBe(280)
  })

  it('UT-PO-01 空なら 0 文字、日本語は 1 文字ずつ数える', () => {
    expect(countChars('')).toBe(0)
    expect(countChars('あいう')).toBe(3)
  })

  it('UT-PO-01 絵文字は JavaScript の length では 2 だが、1 文字と数える', () => {
    expect('😀'.length).toBe(2)
    expect(countChars('😀')).toBe(1)
    expect(countChars('😀'.repeat(BODY_MAX_LENGTH))).toBe(BODY_MAX_LENGTH)
  })

  it('UT-PO-01 組み合わせの絵文字（家族）は、つないだ文字の数で数える（サーバーと同じ）', () => {
    // 男性・ZWJ・女性・ZWJ・女の子 の 5 つのコードポイント
    expect(countChars('👨‍👩‍👧')).toBe(5)
  })
})
