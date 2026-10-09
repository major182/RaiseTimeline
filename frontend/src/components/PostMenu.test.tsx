import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import type { Post } from '../api/types'
import { makePost, makeUser } from '../test/fixtures'
import { TEST_ME, authResponse, json, noContent, renderApp, stubApi } from '../test/render'

afterEach(() => localStorage.clear())

const MINE = makePost({
  id: 10,
  body: '元の本文',
  isMine: true,
  author: makeUser({
    id: TEST_ME.id,
    displayName: TEST_ME.displayName,
    username: TEST_ME.username,
    isMe: true,
  }),
})

/** タイムラインに投稿を並べて表示する。 */
async function showTimeline(posts: Post[], extra: Parameters<typeof stubApi>[0] = {}) {
  const fetchMock = stubApi({
    'POST /api/auth/refresh': authResponse(TEST_ME),
    'GET /api/timeline/following': json(200, { highlights: [], items: posts, nextCursor: null }),
    ...extra,
  })
  renderApp('/')
  await screen.findAllByRole('article')
  return { user: userEvent.setup(), fetchMock }
}

async function openMenuItem(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(screen.getByRole('button', { name: '投稿のメニュー' }))
  await user.click(await screen.findByRole('menuitem', { name }))
}

describe('自分の投稿のメニュー（画面設計書 4.1、BR-12）', () => {
  it('自分の投稿にだけメニューを出す', async () => {
    await showTimeline([MINE, makePost({ id: 11, body: '他人の投稿' })])
    expect(screen.getAllByRole('button', { name: '投稿のメニュー' })).toHaveLength(1)
    const mine = screen.getByRole('article', { name: 'レイズさんの投稿' })
    expect(within(mine).getByRole('button', { name: '投稿のメニュー' })).toBeInTheDocument()
  })
})

describe('投稿編集モーダル（MD-02、F-PO-06）', () => {
  it('今の本文で開き、変えていなければ保存できない', async () => {
    const { user } = await showTimeline([MINE])
    await openMenuItem(user, '編集')
    const dialog = await screen.findByRole('dialog', { name: '投稿を編集' })
    const input = within(dialog).getByRole<HTMLTextAreaElement>('textbox', { name: '本文' })
    expect(input).toHaveValue('元の本文')
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeDisabled()
    // 続きを書きやすいよう、カーソルは本文の末尾
    await waitFor(() => expect(input).toHaveFocus())
    await waitFor(() => expect(input.selectionStart).toBe('元の本文'.length))
  })

  it('保存したら、カードの本文と「編集済み」を更新して知らせる', async () => {
    let sent: unknown
    const { user } = await showTimeline([MINE], {
      'PATCH /api/posts/10': (body) => {
        sent = body
        return json(200, { ...MINE, body: '直した本文', editedAt: new Date().toISOString() })
      },
    })
    await openMenuItem(user, '編集')
    const dialog = await screen.findByRole('dialog', { name: '投稿を編集' })
    const input = within(dialog).getByRole('textbox', { name: '本文' })
    await user.clear(input)
    await user.type(input, '直した本文')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))

    expect(await screen.findByText('投稿を編集しました')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: '投稿を編集' })).not.toBeInTheDocument(),
    )
    expect(sent).toEqual({ body: '直した本文' })
    expect(screen.getByTestId('post-body')).toHaveTextContent('直した本文')
    expect(screen.getByText(/・編集済み/)).toBeInTheDocument()
  })

  it('画像のない投稿は、本文を空にして保存できない（BR-11）', async () => {
    const { user } = await showTimeline([MINE])
    await openMenuItem(user, '編集')
    const dialog = await screen.findByRole('dialog', { name: '投稿を編集' })
    await user.clear(within(dialog).getByRole('textbox', { name: '本文' }))
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeDisabled()
  })

  it('画像のある投稿は、本文を空にして保存でき、画像は消せない（BR-15）', async () => {
    const withImage = { ...MINE, images: [{ url: '/media/a.png', width: 10, height: 10 }] }
    const { user } = await showTimeline([withImage])
    await openMenuItem(user, '編集')
    const dialog = await screen.findByRole('dialog', { name: '投稿を編集' })
    await user.clear(within(dialog).getByRole('textbox', { name: '本文' }))
    expect(within(dialog).getByRole('button', { name: '保存' })).toBeEnabled()
    expect(within(dialog).getByRole('img', { name: '画像 1/1' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: /画像.*外す/ })).not.toBeInTheDocument()
  })

  it('本文を変えて閉じるときは確認し、破棄したら元の本文のまま', async () => {
    const { user } = await showTimeline([MINE])
    await openMenuItem(user, '編集')
    const dialog = await screen.findByRole('dialog', { name: '投稿を編集' })
    await user.type(within(dialog).getByRole('textbox', { name: '本文' }), '追記')
    await user.click(within(dialog).getByRole('button', { name: '閉じる' }))
    await user.click(await screen.findByRole('button', { name: '破棄' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByTestId('post-body')).toHaveTextContent('元の本文')

    // もう一度開くと、元の本文から始まる
    await openMenuItem(user, '編集')
    const again = await screen.findByRole('dialog', { name: '投稿を編集' })
    expect(within(again).getByRole('textbox', { name: '本文' })).toHaveValue('元の本文')
  })

  it('権限がなければ（403）、サーバーのメッセージを出す', async () => {
    const { user } = await showTimeline([MINE], {
      'PATCH /api/posts/10': json(403, {
        status: 403,
        code: 'FORBIDDEN',
        detail: 'この操作はできません',
      }),
    })
    await openMenuItem(user, '編集')
    const dialog = await screen.findByRole('dialog', { name: '投稿を編集' })
    await user.type(within(dialog).getByRole('textbox', { name: '本文' }), '！')
    await user.click(within(dialog).getByRole('button', { name: '保存' }))
    expect(await screen.findByText('この操作はできません')).toBeInTheDocument()
  })
})

describe('削除確認ダイアログ（DL-01、F-PO-03）', () => {
  it('確認の文言を出し、最初はキャンセルを選んだ状態にする', async () => {
    const { user } = await showTimeline([MINE])
    await openMenuItem(user, '削除')
    const dialog = await screen.findByRole('dialog', { name: '投稿を削除しますか？' })
    expect(
      within(dialog).getByText('この操作は取り消せません。コメントといいねも削除されます。'),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(within(dialog).getByRole('button', { name: 'キャンセル' })).toHaveFocus(),
    )
  })

  it('キャンセルなら削除しない', async () => {
    const { user, fetchMock } = await showTimeline([MINE])
    await openMenuItem(user, '削除')
    await user.click(await screen.findByRole('button', { name: 'キャンセル' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('元の本文')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[1]?.method === 'DELETE')).toBe(false)
  })

  it('削除したら一覧から消して知らせる', async () => {
    const { user } = await showTimeline([MINE, makePost({ id: 11, body: '残る投稿' })], {
      'DELETE /api/posts/10': noContent(),
    })
    await openMenuItem(user, '削除')
    const dialog = await screen.findByRole('dialog', { name: '投稿を削除しますか？' })
    await user.click(within(dialog).getByRole('button', { name: '削除' }))

    expect(await screen.findByText('投稿を削除しました')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('元の本文')).not.toBeInTheDocument())
    expect(screen.getByText('残る投稿')).toBeInTheDocument()
  })

  it('削除に失敗したら、一覧に残してメッセージを出す', async () => {
    const { user } = await showTimeline([MINE], {
      'DELETE /api/posts/10': json(404, {
        status: 404,
        code: 'NOT_FOUND',
        detail: '見つかりません',
      }),
    })
    await openMenuItem(user, '削除')
    const dialog = await screen.findByRole('dialog', { name: '投稿を削除しますか？' })
    await user.click(within(dialog).getByRole('button', { name: '削除' }))
    expect(await screen.findByText('見つかりません')).toBeInTheDocument()
    expect(screen.getByText('元の本文')).toBeInTheDocument()
  })
})
