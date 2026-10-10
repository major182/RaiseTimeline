import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Profile } from '../api/types'
import { makeUser } from '../test/fixtures'
import { TEST_ME, authResponse, json, renderApp, stubApi } from '../test/render'

// 縮小・再エンコードは canvas を使い、テストの環境（jsdom）では動かないので、そのまま返す形に置き換える
vi.mock('../imageAttach', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../imageAttach')>()),
  prepareImage: vi.fn(async (file: File) => file),
}))

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})

const ME_PROFILE: Profile = {
  ...makeUser({ id: 1, username: 'raise_me', displayName: 'レイズ', isMe: true }),
  bio: '自己紹介',
  followingCount: 0,
  followerCount: 0,
  createdAt: '2026-10-01T00:00:00Z',
}

function openEdit(extra: Parameters<typeof stubApi>[0] = {}) {
  const fetchMock = stubApi({
    'POST /api/auth/refresh': authResponse(TEST_ME),
    'GET /api/users/1': json(200, ME_PROFILE),
    'GET /api/users/1/posts': json(200, { items: [], nextCursor: null }),
    ...extra,
  })
  renderApp('/settings/profile')
  return { fetchMock, user: userEvent.setup() }
}

const field = (name: string) => screen.findByRole('textbox', { name })

describe('プロフィールの編集（SC-06）', () => {
  it('今の値を出し、ユーザー名の下に注意書きを出す', async () => {
    openEdit()
    expect(await field('表示名')).toHaveValue('レイズ')
    expect(await field('ユーザー名')).toHaveValue('raise_me')
    expect(await field('自己紹介')).toHaveValue('自己紹介')
    expect(screen.getByText('変更すると、プロフィールの URL も変わります')).toBeInTheDocument()
  })

  it('変えた項目だけを送り、新しい URL のプロフィールへ移って I-06 を出す', async () => {
    let sent: unknown
    const saved = { ...ME_PROFILE, username: 'new_name' }
    const { user } = openEdit({
      'PATCH /api/me/profile': (body) => {
        sent = body
        return json(200, saved)
      },
    })
    const username = await field('ユーザー名')
    await user.clear(username)
    await user.type(username, 'new_name')
    await user.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('プロフィールを保存しました')).toBeInTheDocument()
    expect(sent).toEqual({ username: 'new_name' })
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/users/new_name'))
    // ナビの「プロフィール」も新しい URL になる
    expect(screen.getByRole('link', { name: 'プロフィール' })).toHaveAttribute(
      'href',
      '/users/new_name',
    )
  })

  it('送る前に E-08・E-06・E-09 を確かめる', async () => {
    const { user, fetchMock } = openEdit()
    await user.clear(await field('表示名'))
    const username = await field('ユーザー名')
    await user.clear(username)
    await user.type(username, 'a-b')
    await user.click(await field('自己紹介'))
    await user.paste('あ'.repeat(160))
    await user.click(screen.getByRole('button', { name: '保存' }))

    expect(screen.getByText('1〜50 文字で入力してください')).toBeInTheDocument()
    expect(screen.getByText('4〜15 文字の半角英数字と「_」で入力してください')).toBeInTheDocument()
    expect(screen.getByText('160 文字以内で入力してください')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/me/profile')).toBe(false)
  })

  it('ユーザー名が使われていたら、入力欄の下に E-07 を出す', async () => {
    const { user } = openEdit({
      'PATCH /api/me/profile': json(409, {
        status: 409,
        code: 'USERNAME_TAKEN',
        detail: '入力内容を確認してください',
        errors: [
          {
            field: 'username',
            code: 'USERNAME_TAKEN',
            message: 'このユーザー名は既に使われています',
          },
        ],
      }),
    })
    const username = await field('ユーザー名')
    await user.clear(username)
    await user.type(username, 'taken')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('このユーザー名は既に使われています')).toBeInTheDocument()
  })

  it('アイコンは選んだらプレビューし、保存したときに送る', async () => {
    let sent: FormData | undefined
    const { user } = openEdit({
      'PUT /api/me/avatar': (body) => {
        sent = body as FormData
        return json(200, { ...ME_PROFILE, avatarUrl: '/media/avatar.png' })
      },
    })
    await field('表示名')
    const input = screen.getByTestId('avatar-input') as HTMLInputElement
    await user.upload(input, new File(['x'], 'a.png', { type: 'image/png' }))
    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'レイズ' })).toHaveAttribute('src', 'blob:preview'),
    )
    expect(sent).toBeUndefined() // 保存するまで送らない
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('プロフィールを保存しました')).toBeInTheDocument()
    expect((sent!.get('file') as File).name).toBe('a.png')
  })

  it('形式の違うアイコンは E-10 を出す', async () => {
    openEdit()
    await field('表示名')
    const input = screen.getByTestId('avatar-input') as HTMLInputElement
    // 形式の違うファイルも選べるように、accept で弾かない
    await userEvent
      .setup({ applyAccept: false })
      .upload(input, new File(['x'], 'a.svg', { type: 'image/svg+xml' }))
    expect(screen.getByRole('alert')).toHaveTextContent(
      'JPEG・PNG・WebP・GIF の画像を選んでください',
    )
  })

  it('変更があれば、キャンセルの前に確認する（C-03）', async () => {
    const { user } = openEdit()
    await user.type(await field('自己紹介'), '追記')
    await user.click(screen.getByRole('button', { name: 'キャンセル' }))
    const confirm = await screen.findByRole('dialog', { name: '編集内容を破棄しますか？' })
    await user.click(within(confirm).getByRole('button', { name: '破棄' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/users/raise_me'))
  })

  it('変更がなければ、確認せずにプロフィールへ戻る', async () => {
    const { user } = openEdit()
    await field('表示名')
    await user.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/users/raise_me')
  })
})
