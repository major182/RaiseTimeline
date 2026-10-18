import { describe, expect, it } from 'vitest'
import { findMentionQuery, insertMention, splitMentions } from './mention'

describe('本文のメンション（BR-60、画面設計書 4.4）', () => {
  it('UT-MN-01 @ユーザー名 をメンションに分け、残りは文字のままにする', () => {
    expect(splitMentions('こんにちは@sato_hana さん')).toEqual([
      { type: 'text', text: 'こんにちは' },
      { type: 'mention', username: 'sato_hana' },
      { type: 'text', text: ' さん' },
    ])
    expect(splitMentions('@abcd と @efgh')).toEqual([
      { type: 'mention', username: 'abcd' },
      { type: 'text', text: ' と ' },
      { type: 'mention', username: 'efgh' },
    ])
  })

  it.each([
    ['3 文字', '@abc', false],
    ['4 文字', '@abcd', true],
    ['15 文字', `@${'a'.repeat(15)}`, true],
    ['16 文字', `@${'a'.repeat(16)}`, false],
    ['前に英数字（メールアドレス）', 'tanaka@example.com', false],
    ['前に「_」', 'x_@abcd', false],
    ['後ろに記号', '@abcd!', true],
  ])('UT-MN-01 %s：リンクにする=%s', (_, text, linked) => {
    expect(splitMentions(text).some((p) => p.type === 'mention')).toBe(linked)
  })

  it('UT-MN-01 メンションがなければ全体を文字のまま返す。空なら何も返さない', () => {
    expect(splitMentions('ただの文')).toEqual([{ type: 'text', text: 'ただの文' }])
    expect(splitMentions('')).toEqual([])
  })
})

describe('入力中のメンション（BR-61、画面設計書 4.5）', () => {
  it.each([
    ['@ だけ', '@', null],
    ['@ + 1 文字', '@y', { start: 0, query: 'y' }],
    ['文の途中', 'こんにちは @yam', { start: 6, query: 'yam' }],
    ['日本語の直後', 'こんにちは@yam', { start: 5, query: 'yam' }],
    ['15 文字', `@${'a'.repeat(15)}`, { start: 0, query: 'a'.repeat(15) }],
    ['16 文字', `@${'a'.repeat(16)}`, null],
    ['前に英数字', 'mail@exa', null],
    ['後ろに空白（打ち終わった）', '@yamada ', null],
  ])('UT-MN-02 %s', (_, text, expected) => {
    expect(findMentionQuery(text, text.length)).toEqual(expected)
  })

  it('UT-MN-02 カーソルより後ろの文字は見ない', () => {
    expect(findMentionQuery('@yam です', 4)).toEqual({ start: 0, query: 'yam' })
  })

  it('UT-MN-02 選んだら @ユーザー名 と空白に置き換え、カーソルをその後ろに置く', () => {
    expect(insertMention('こんにちは @yam です', { start: 6, query: 'yam' }, 'yamada')).toEqual({
      text: 'こんにちは @yamada  です',
      caret: 14,
    })
  })
})
