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
    author: makeUser(),
    body: 'コメントです',
    createdAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
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
    expect(screen.getByRole('link', { name: 'コメント（2）' })).toBeInTheDocument()
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
    expect(screen.getByRole('link', { name: 'コメント（0）' })).toBeInTheDocument()
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
