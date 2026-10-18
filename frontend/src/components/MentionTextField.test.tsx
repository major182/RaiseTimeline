import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { makeUser } from '../test/fixtures'
import { json, renderWithProviders, stubApi } from '../test/render'
import { MentionTextField } from './MentionTextField'

const users = [
  makeUser({ id: 2, username: 'yamada', displayName: '山田' }),
  makeUser({ id: 3, username: 'yamamoto', displayName: '山本' }),
]
const search = (q: string) => `GET /api/users/search?q=${encodeURIComponent(q)}`

/** 入力欄だけを置く。Enter などの、候補が使わなかったキーを記録する。 */
function Field({ onKeyDown }: { onKeyDown?: (key: string) => void }) {
  const [value, setValue] = useState('')
  return (
    <MentionTextField
      value={value}
      onValueChange={setValue}
      multiline
      slotProps={{ htmlInput: { 'aria-label': '本文' } }}
      onKeyDown={(e) => onKeyDown?.(e.key)}
    />
  )
}

function open(stub: Parameters<typeof stubApi>[0] = {}, onKeyDown?: (key: string) => void) {
  const fetchMock = stubApi({
    [search('yam')]: json(200, { items: users, nextCursor: null }),
    ...stub,
  })
  renderWithProviders(<Field onKeyDown={onKeyDown} />)
  return {
    fetchMock,
    user: userEvent.setup(),
    input: screen.getByRole('textbox', { name: '本文' }),
  }
}

// アイコンの文字（表示名の 1 文字目）を除き、表示名と @ユーザー名 だけを比べる
const options = () =>
  screen.queryAllByRole('option').map((o) => o.querySelector('.MuiListItemText-root')?.textContent)

describe('メンションの候補（画面設計書 4.5 MC-01、BR-61）', () => {
  it('UT-MN-04 @ に続けて打つと、打ち終えてから検索し、候補を出す', async () => {
    const { user, input, fetchMock } = open()
    await user.type(input, 'こんにちは @yam')
    expect(await screen.findByRole('listbox', { name: 'メンションの候補' })).toBeInTheDocument()
    expect(options()).toEqual(['山田@yamada', '山本@yamamoto'])
    // 1 文字ずつ検索せず、打ち終えた「yam」だけで探す
    const searches = fetchMock.mock.calls.filter((c) => String(c[0]).includes('/search'))
    expect(searches.map((c) => c[0])).toEqual(['/api/users/search?q=yam'])
  })

  it('UT-MN-04 候補は最大 5 人', async () => {
    const many = Array.from({ length: 7 }, (_, i) => makeUser({ id: i + 10, username: `yam_${i}` }))
    const { user, input } = open({ [search('yam')]: json(200, { items: many, nextCursor: null }) })
    await user.type(input, '@yam')
    await screen.findByRole('listbox')
    expect(screen.getAllByRole('option')).toHaveLength(5)
  })

  it('UT-MN-04 ↓ と Enter で選ぶと @ユーザー名 と空白を入れ、Enter はほかに使わない', async () => {
    const keys = vi.fn()
    const { user, input } = open({}, keys)
    await user.type(input, '@yam')
    await screen.findByRole('listbox')
    expect(screen.getAllByRole('option')[0]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowDown}')
    expect(screen.getAllByRole('option')[1]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowDown}') // 最後の次は最初に戻る
    expect(screen.getAllByRole('option')[0]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowUp}{Enter}')

    expect(input).toHaveValue('@yamamoto ')
    expect(keys).not.toHaveBeenCalledWith('Enter')
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument())
  })

  it('UT-MN-04 押しても選べる', async () => {
    const { user, input } = open()
    await user.type(input, 'やあ @yam')
    await user.click(await screen.findByRole('option', { name: /yamada/ }))
    expect(input).toHaveValue('やあ @yamada ')
  })

  it('UT-MN-04 Esc で閉じ、同じ @ では出し直さない。候補を閉じている間の Enter はそのまま渡す', async () => {
    const keys = vi.fn()
    const { user, input } = open({}, keys)
    await user.type(input, '@yam')
    await screen.findByRole('listbox')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    await user.keyboard('{Enter}')
    expect(keys).toHaveBeenCalledWith('Enter')
  })

  it('UT-MN-04 0 人か、検索に失敗したら、メッセージを出さずに候補を閉じたままにする', async () => {
    const { user, input } = open({
      [search('nobody')]: json(200, { items: [], nextCursor: null }),
      [search('broken')]: json(500, { status: 500, code: 'INTERNAL_ERROR', detail: 'x' }),
    })
    await user.type(input, '@nobody')
    await new Promise((r) => setTimeout(r, 400))
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    await user.clear(input)
    await user.type(input, '@broken')
    await new Promise((r) => setTimeout(r, 400))
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('UT-MN-04 入力欄から焦点が外れたら閉じる', async () => {
    const { user, input } = open()
    await user.type(input, '@yam')
    await screen.findByRole('listbox')
    await user.tab()
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument())
  })
})
