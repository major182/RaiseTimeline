import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import type { Comment } from '../api/types'
import { makePost, makeUser } from '../test/fixtures'
import { TEST_ME, authResponse, json, noContent, renderApp, stubApi } from '../test/render'

afterEach(() => localStorage.clear())

function makeComment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    postId: 100,
    parentId: null,
    author: makeUser(),
    body: 'コメントです',
    createdAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    replyCount: 0,
    deletable: false,
    ...overrides,
  }
}

const page = <T,>(items: T[], nextCursor: string | null = null) => json(200, { items, nextCursor })

function openDetail(extra: Parameters<typeof stubApi>[0] = {}) {
  const fetchMock = stubApi({
    'POST /api/auth/refresh': authResponse(TEST_ME),
    'GET /api/posts/100': json(200, makePost({ commentCount: 1, likeCount: 3 })),
    'GET /api/posts/100/comments': page([makeComment()]),
    ...extra,
  })
  renderApp('/posts/100')
  return { fetchMock, user: userEvent.setup() }
}

const commentBodies = () => screen.queryAllByTestId('comment-body').map((el) => el.textContent)

describe('投稿の詳細（SC-04）', () => {
  it('投稿を全文・詳しい日時で出し、コメントを古い順に出す', async () => {
    openDetail({
      'GET /api/posts/100': json(
        200,
        makePost({ body: '1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11', createdAt: '2026-10-09T05:05:00Z' }),
      ),
      'GET /api/posts/100/comments': page([
        makeComment({ id: 1, body: '古いコメント' }),
        makeComment({ id: 2, body: '新しいコメント' }),
      ]),
    })
    expect(await screen.findByText('2026年10月9日 14:05')).toBeInTheDocument()
    expect(screen.queryByText('続きを読む')).not.toBeInTheDocument()
    await waitFor(() => expect(commentBodies()).toEqual(['古いコメント', '新しいコメント']))
  })

  it('いいねの数から、いいねした人の一覧へ移る', async () => {
    const { user } = openDetail({
      'GET /api/posts/100/likes': page([makeUser({ displayName: 'いいねした人' })]),
    })
    await user.click(await screen.findByRole('link', { name: '3 件のいいね' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/posts/100/likes')
    expect(await screen.findByText('いいねした人')).toBeInTheDocument()
  })

  it('投稿がなければ N-05 を出す', async () => {
    openDetail({
      'GET /api/posts/100': json(404, { status: 404, code: 'NOT_FOUND', detail: '見つかりません' }),
    })
    expect(await screen.findByText('この投稿は削除されたか、見つかりません')).toBeInTheDocument()
  })

  it('詳細で投稿を削除したら、ホームへ戻る', async () => {
    const { user } = openDetail({
      'GET /api/posts/100': json(200, makePost({ isMine: true })),
      'DELETE /api/posts/100': noContent(),
    })
    await user.click(await screen.findByRole('button', { name: '投稿のメニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByRole('button', { name: '削除' }))
    expect(await screen.findByText('投稿を削除しました')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
  })

  it('戻るボタンで、URL を直接開いたときはホームへ戻る', async () => {
    const { user } = openDetail()
    await user.click(await screen.findByRole('button', { name: '戻る' }))
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})

describe('コメント（F-CM-01〜04）', () => {
  it('コメントしたら入力欄を空にし、最後に足し、数を増やす', async () => {
    let sent: unknown
    const { user } = openDetail({
      'POST /api/posts/100/comments': (body) => {
        sent = body
        return json(
          201,
          makeComment({ id: 9, body: '書いたコメント', author: makeUser({ id: 1 }) }),
        )
      },
    })
    const input = await screen.findByRole('textbox', { name: 'コメント' })
    const submit = screen.getByRole('button', { name: 'コメントする' })
    expect(submit).toBeDisabled()
    await user.type(input, '書いたコメント')
    await user.click(submit)

    await waitFor(() => expect(commentBodies()).toEqual(['コメントです', '書いたコメント']))
    expect(sent).toEqual({ body: '書いたコメント' })
    expect(input).toHaveValue('')
    expect(screen.getByRole('link', { name: 'コメント 2' })).toBeInTheDocument()
  })

  it('空白だけ・280 文字を超えたらコメントできない', async () => {
    const { user } = openDetail()
    const input = await screen.findByRole('textbox', { name: 'コメント' })
    const submit = screen.getByRole('button', { name: 'コメントする' })
    await user.type(input, '   ')
    expect(submit).toBeDisabled()
    await user.clear(input)
    await user.click(input)
    await user.paste('あ'.repeat(281))
    expect(submit).toBeDisabled()
  })

  it('削除できるコメントだけにメニューを出し、確認してから消す（C-02・I-05）', async () => {
    const { user } = openDetail({
      'GET /api/posts/100/comments': page([
        makeComment({ id: 1, body: '消せないコメント' }),
        makeComment({ id: 2, body: '消せるコメント', deletable: true }),
      ]),
      'DELETE /api/comments/2': noContent(),
    })
    await screen.findByText('消せるコメント')
    const menus = screen.getAllByRole('button', { name: 'コメントのメニュー' })
    expect(menus).toHaveLength(1)
    await user.click(menus[0])
    await user.click(screen.getByRole('menuitem', { name: '削除' }))
    const confirm = await screen.findByRole('dialog', { name: 'コメントを削除しますか？' })
    await user.click(within(confirm).getByRole('button', { name: '削除' }))

    expect(await screen.findByText('コメントを削除しました')).toBeInTheDocument()
    expect(commentBodies()).toEqual(['消せないコメント'])
    expect(screen.getByRole('link', { name: 'コメント 0' })).toBeInTheDocument()
  })
})

describe('返信（F-CM-05・06、画面設計書 5.7）', () => {
  const comment = makeComment({ id: 1, body: '元のコメント', replyCount: 2 })
  const reply = (id: number, body: string, overrides: Partial<Comment> = {}) =>
    makeComment({
      id,
      parentId: 1,
      body,
      author: makeUser({ id: 3, username: 'suzuki_ken', displayName: '鈴木' }),
      ...overrides,
    })

  function openWithReplies(extra: Parameters<typeof stubApi>[0] = {}) {
    return openDetail({
      'GET /api/posts/100': json(200, makePost({ commentCount: 3 })),
      'GET /api/posts/100/comments': page([comment]),
      'GET /api/comments/1/replies': page([reply(11, '返信1'), reply(12, '返信2')]),
      ...extra,
    })
  }

  const replyList = () => screen.queryByRole('region', { name: '返信の一覧' })

  it('UT-CM-04 返信は最初は閉じ、「返信を表示（N 件）」で古い順に出し、「返信を隠す」で閉じる', async () => {
    const { user, fetchMock } = openWithReplies()
    await user.click(await screen.findByRole('button', { name: '返信を表示（2 件）' }))
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/comments/1/replies')).toBe(true)
    await waitFor(() => expect(commentBodies()).toEqual(['元のコメント', '返信1', '返信2']))
    expect(within(replyList()!).getAllByRole('article', { name: '鈴木さんの返信' })).toHaveLength(2)

    await user.click(screen.getByRole('button', { name: '返信を隠す' }))
    expect(replyList()).not.toBeInTheDocument()
    expect(commentBodies()).toEqual(['元のコメント'])
  })

  it('UT-CM-04 返信がなければ「返信を表示」を出さず、返信の一覧も読まない', async () => {
    const { fetchMock } = openDetail()
    await screen.findByText('コメントです')
    expect(screen.queryByRole('button', { name: /返信を表示/ })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/replies'))).toBe(false)
  })

  it('UT-CM-04 返信が 20 件を超えたら「さらに返信を表示」で続きを読む', async () => {
    const { user } = openWithReplies({
      'GET /api/comments/1/replies': page([reply(11, '返信1')], 'next'),
      'GET /api/comments/1/replies?cursor=next': page([reply(12, '返信2')]),
    })
    await user.click(await screen.findByRole('button', { name: '返信を表示（2 件）' }))
    await user.click(await screen.findByRole('button', { name: 'さらに返信を表示' }))
    await waitFor(() => expect(commentBodies()).toEqual(['元のコメント', '返信1', '返信2']))
    expect(screen.queryByRole('button', { name: 'さらに返信を表示' })).not.toBeInTheDocument()
  })

  it('UT-CM-04 コメントに返信すると、返信の一覧を開いて最後に足し、数を増やす', async () => {
    let sent: unknown
    const created = reply(13, '書いた返信', { author: makeUser({ id: 1 }) })
    const { user } = openWithReplies({
      'POST /api/posts/100/comments': (body) => {
        sent = body
        return json(201, created)
      },
      // 返信の一覧はまだ読んでいないので、返信した後にサーバーから読む（書いた返信も入っている）
      'GET /api/comments/1/replies': () =>
        page(sent ? [reply(11, '返信1'), reply(12, '返信2'), created] : []),
    })
    await user.click(await screen.findByRole('button', { name: '返信' }))
    const input = await screen.findByRole('textbox', { name: '返信' })
    expect(input).toHaveValue('') // コメントへの返信は、@ユーザー名 を入れない
    expect(input).toHaveFocus()
    await user.type(input, '書いた返信')
    await user.click(screen.getByRole('button', { name: '返信する' }))

    await waitFor(() =>
      expect(commentBodies()).toEqual(['元のコメント', '返信1', '返信2', '書いた返信']),
    )
    expect(sent).toEqual({ body: '書いた返信', parentId: 1 })
    expect(screen.queryByRole('textbox', { name: '返信' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'コメント 4' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '返信を隠す' }))
    expect(screen.getByRole('button', { name: '返信を表示（3 件）' })).toBeInTheDocument()
  })

  it('UT-CM-04 返信に返信すると、返信先の @ユーザー名 を入れて開き、その返信を返信先として送る', async () => {
    let sent: unknown
    const { user } = openWithReplies({
      'POST /api/posts/100/comments': (body) => {
        sent = body
        return json(201, reply(13, '@suzuki_ken どうも'))
      },
    })
    await user.click(await screen.findByRole('button', { name: '返信を表示（2 件）' }))
    const second = await screen.findByText('返信2')
    await user.click(within(second.closest('article')!).getByRole('button', { name: '返信' }))
    const input = await screen.findByRole('textbox', { name: '返信' })
    expect(input).toHaveValue('@suzuki_ken ')
    await user.type(input, 'どうも')
    await user.click(screen.getByRole('button', { name: '返信する' }))

    await waitFor(() => expect(sent).toEqual({ body: '@suzuki_ken どうも', parentId: 12 }))
  })

  it('UT-CM-04 入力欄は同時に 1 つだけ開く', async () => {
    const { user } = openDetail({
      'GET /api/posts/100/comments': page([
        makeComment({ id: 1, body: 'A' }),
        makeComment({ id: 2, body: 'B' }),
      ]),
    })
    await screen.findByText('B')
    const [first, second] = screen.getAllByRole('button', { name: '返信' })
    await user.click(first)
    await user.click(second)
    expect(screen.getAllByRole('textbox', { name: '返信' })).toHaveLength(1)
  })

  it('UT-CM-04 入力がなければキャンセルですぐ閉じ、入力があれば C-03 で確かめる', async () => {
    const { user } = openWithReplies()
    await user.click(await screen.findByRole('button', { name: '返信' }))
    await user.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(screen.queryByRole('textbox', { name: '返信' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '返信' }))
    await user.type(screen.getByRole('textbox', { name: '返信' }), '途中')
    await user.click(screen.getByRole('button', { name: 'キャンセル' }))
    const confirm = await screen.findByRole('dialog', { name: '編集内容を破棄しますか？' })
    await user.click(within(confirm).getByRole('button', { name: '破棄' }))
    await waitFor(() =>
      expect(screen.queryByRole('textbox', { name: '返信' })).not.toBeInTheDocument(),
    )
  })

  it('UT-CM-04 返信先が消えていたら（404）S-05 を出し、そのコメントを一覧から消す', async () => {
    const { user } = openWithReplies({
      'POST /api/posts/100/comments': json(404, {
        status: 404,
        code: 'NOT_FOUND',
        detail: '見つかりません',
      }),
    })
    await user.click(await screen.findByRole('button', { name: '返信' }))
    await user.type(screen.getByRole('textbox', { name: '返信' }), '返信')
    await user.click(screen.getByRole('button', { name: '返信する' }))

    expect(
      await screen.findByText('このコメントは削除されたため、返信できません'),
    ).toBeInTheDocument()
    expect(commentBodies()).toEqual([])
    expect(screen.getByRole('link', { name: 'コメント 0' })).toBeInTheDocument()
  })

  it('UT-CM-04 返信があるコメントは C-05 で確かめ、消したら返信の数も合わせてコメントの数を減らす', async () => {
    const { user } = openWithReplies({
      'GET /api/posts/100/comments': page([{ ...comment, deletable: true }]),
      'DELETE /api/comments/1': noContent(),
    })
    await user.click(await screen.findByRole('button', { name: 'コメントのメニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '削除' }))
    const confirm = await screen.findByRole('dialog', { name: 'コメントを削除しますか？' })
    expect(confirm).toHaveTextContent('この操作は取り消せません。返信も削除されます。')
    await user.click(within(confirm).getByRole('button', { name: '削除' }))

    expect(await screen.findByText('コメントを削除しました')).toBeInTheDocument()
    expect(commentBodies()).toEqual([])
    expect(screen.getByRole('link', { name: 'コメント 0' })).toBeInTheDocument()
  })

  it('UT-CM-04 返信を消したら、返信の数とコメントの数を 1 つ減らす（C-02）', async () => {
    const { user } = openWithReplies({
      'GET /api/comments/1/replies': page([
        reply(11, '返信1', { deletable: true }),
        reply(12, '返信2'),
      ]),
      'DELETE /api/comments/11': noContent(),
    })
    await user.click(await screen.findByRole('button', { name: '返信を表示（2 件）' }))
    await user.click(await screen.findByRole('button', { name: '返信のメニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '削除' }))
    const confirm = await screen.findByRole('dialog', { name: 'コメントを削除しますか？' })
    expect(confirm).not.toHaveTextContent('返信も削除されます')
    await user.click(within(confirm).getByRole('button', { name: '削除' }))

    await waitFor(() => expect(commentBodies()).toEqual(['元のコメント', '返信2']))
    expect(screen.getByRole('link', { name: 'コメント 2' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '返信を隠す' }))
    expect(screen.getByRole('button', { name: '返信を表示（1 件）' })).toBeInTheDocument()
  })

  it('UT-MN-03 コメントの本文の @ユーザー名 はプロフィールへのリンクにする', async () => {
    openDetail({
      'GET /api/posts/100/comments': page([makeComment({ body: '@sato_hana さんへ' })]),
    })
    expect(await screen.findByRole('link', { name: '@sato_hana' })).toHaveAttribute(
      'href',
      '/users/sato_hana',
    )
  })
})

describe('いいねした人の一覧（SC-09）', () => {
  it('0 人なら N-09 を出す', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      'GET /api/posts/100/likes': page([]),
    })
    renderApp('/posts/100/likes')
    expect(await screen.findByRole('heading', { name: 'いいねしたユーザー' })).toBeInTheDocument()
    expect(await screen.findByText('まだいいねはありません')).toBeInTheDocument()
  })
})
