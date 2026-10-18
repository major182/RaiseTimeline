import { describe, expect, it } from 'vitest'
import { withCursor } from './cursor'

describe('続きのカーソルの付け方（API 設計書 2.5）', () => {
  it('UT-CO-01 カーソルがなければ（最初の読み込み）パスをそのまま使う', () => {
    expect(withCursor('/api/timeline/all', null)).toBe('/api/timeline/all')
    expect(withCursor('/api/timeline/all', '')).toBe('/api/timeline/all')
  })

  it('UT-CO-01 カーソルがあれば ?cursor= を付ける', () => {
    expect(withCursor('/api/timeline/all', 'abc')).toBe('/api/timeline/all?cursor=abc')
  })

  it('UT-CO-01 すでに ? があれば & でつなぐ', () => {
    expect(withCursor('/api/users/search?q=raise', 'abc')).toBe(
      '/api/users/search?q=raise&cursor=abc',
    )
  })

  it('UT-CO-01 カーソルの記号は URL の中で意味を持たないように変える', () => {
    expect(withCursor('/api/timeline/all', 'a+b/c=&d')).toBe(
      '/api/timeline/all?cursor=a%2Bb%2Fc%3D%26d',
    )
  })
})
