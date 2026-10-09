import { describe, expect, it } from 'vitest'
import { safeRedirect } from './RouteGuards'

describe('safeRedirect（ログインの後に戻る先。技術選定書 4.7 S-06）', () => {
  it.each(['/', '/posts/1', '/search?q=raise'])('アプリの中の URL はそのまま：%s', (from) => {
    expect(safeRedirect(from)).toBe(from)
  })

  it.each([undefined, '', 'https://evil.example.com', '//evil.example.com', '/\\evil.example.com'])(
    '他のサイトへ移動しうる URL はホームにする：%s',
    (from) => {
      expect(safeRedirect(from)).toBe('/')
    },
  )
})
