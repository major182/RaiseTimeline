import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import type { Profile } from '../api/types'
import { makePost, makeUser } from '../test/fixtures'
import { TEST_ME, authResponse, json, noContent, renderApp, stubApi } from '../test/render'

afterEach(() => localStorage.clear())

function makeProfile(overrides: Partial<Profile> = {}): Profile {
  return {
    ...makeUser(),
    bio: '1 行目\n2 行目',
    followingCount: 12,
    followerCount: 34,
    createdAt: '2026-10-01T00:00:00Z',
    ...overrides,
  }
}

const page = <T,>(items: T[]) => json(200, { items, nextCursor: null })
const notFound = json(404, { status: 404, code: 'NOT_FOUND', detail: '見つかりません' })

function open(path: string, extra: Parameters<typeof stubApi>[0] = {}) {
  stubApi({
    'POST /api/auth/refresh': authResponse(TEST_ME),
    'GET /api/users/by-username/yamada': json(200, makeProfile()),
    'GET /api/users/2/posts': page([makePost({ body: '山田の投稿' })]),
    ...extra,
  })
  renderApp(path)
  return userEvent.setup()
}

describe('プロフィール（SC-05）', () => {
  it('利用者の情報と投稿の一覧を出す', async () => {
    open('/users/yamada')
    expect(await screen.findByText('山田の投稿')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '山田' })).toBeInTheDocument()
    expect(screen.getByTestId('profile-bio').textContent).toBe('1 行目\n2 行目')
    expect(screen.getByText('2026年10月から利用')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '12 フォロー' })).toHaveAttribute(
      'href',
      '/users/yamada/following',
    )
    expect(screen.getByRole('link', { name: '34 フォロワー' })).toHaveAttribute(
      'href',
      '/users/yamada/followers',
    )
  })

  it('他人ならフォローでき、フォロワー数をすぐ増やす', async () => {
    const user = open('/users/yamada', { 'PUT /api/users/2/follow': noContent() })
    await user.click(await screen.findByRole('button', { name: '山田さんをフォロー' }))
    expect(screen.getByRole('link', { name: '35 フォロワー' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '山田さんのフォローを解除' })).toBeInTheDocument()
  })

  it('フォローに失敗したら、数を元に戻す', async () => {
    const user = open('/users/yamada', {
      'PUT /api/users/2/follow': json(500, {
        status: 500,
        code: 'INTERNAL_ERROR',
        detail: 'エラーが発生しました。時間をおいてもう一度お試しください',
      }),
    })
    await user.click(await screen.findByRole('button', { name: '山田さんをフォロー' }))
    expect(
      await screen.findByText('エラーが発生しました。時間をおいてもう一度お試しください'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '34 フォロワー' })).toBeInTheDocument()
  })

  it('自分なら「プロフィールを編集」を出し、フォローボタンは出さない', async () => {
    open('/users/raise_me', {
      'GET /api/users/by-username/raise_me': json(
        200,
        makeProfile({ id: 1, username: 'raise_me', displayName: 'レイズ', isMe: true }),
      ),
      'GET /api/users/1/posts': page([]),
    })
    expect(await screen.findByRole('link', { name: 'プロフィールを編集' })).toHaveAttribute(
      'href',
      '/settings/profile',
    )
    expect(screen.queryByRole('button', { name: /フォロー/ })).not.toBeInTheDocument()
    expect(await screen.findByText('まだ投稿がありません')).toBeInTheDocument() // N-03
  })

  it('いなければ N-04 を出す', async () => {
    open('/users/nobody', { 'GET /api/users/by-username/nobody': notFound })
    expect(await screen.findByText('このユーザーは見つかりません')).toBeInTheDocument()
  })
})

describe('フォロー／フォロワー一覧（SC-08）', () => {
  it('タブで URL を切り替え、それぞれの一覧を出す', async () => {
    const user = open('/users/yamada/following', {
      'GET /api/users/2/following': page([makeUser({ id: 3, displayName: 'フォロー中の人' })]),
      'GET /api/users/2/followers': page([]),
    })
    expect(await screen.findByText('フォロー中の人')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'フォロー' })).toHaveAttribute('aria-selected', 'true')

    await user.click(screen.getByRole('tab', { name: 'フォロワー' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/users/yamada/followers')
    expect(await screen.findByText('まだフォロワーはいません')).toBeInTheDocument() // N-08
  })

  it('フォローが 0 人なら N-07', async () => {
    open('/users/yamada/following', { 'GET /api/users/2/following': page([]) })
    expect(await screen.findByText('まだ誰もフォローしていません')).toBeInTheDocument()
  })
})

describe('ナビの「プロフィール」', () => {
  it('自分のプロフィールへ移る', async () => {
    const user = open('/', {
      'GET /api/users/by-username/raise_me': json(
        200,
        makeProfile({ id: 1, username: 'raise_me', displayName: 'レイズ', isMe: true }),
      ),
      'GET /api/users/1/posts': page([]),
    })
    await user.click(await screen.findByRole('link', { name: 'プロフィール' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/users/raise_me'))
    expect(await screen.findByRole('heading', { name: 'レイズ' })).toBeInTheDocument()
  })
})
