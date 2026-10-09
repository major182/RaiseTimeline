import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { TEST_ME, UNAUTHENTICATED, json, noContent, renderApp, stubApi } from '../test/render'

describe('仮のホーム画面とログアウト', () => {
  it('確認してからログアウトし、ログイン画面へ戻る', async () => {
    const fetchMock = stubApi({
      'GET /api/auth/me': json(200, TEST_ME),
      'POST /api/auth/logout': noContent(),
    })
    renderApp('/')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'ログアウト' }))

    // 確認のダイアログ（C-04）。最初はキャンセルを選んだ状態
    const dialog = await screen.findByRole('dialog', { name: 'ログアウトしますか？' })
    expect(screen.getByRole('button', { name: 'キャンセル' })).toHaveFocus()
    await user.click(dialog.querySelector('button.MuiButton-contained')!)

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/logout')).toBe(true)
  })

  it('確認でキャンセルすれば、ログアウトしない', async () => {
    const fetchMock = stubApi({ 'GET /api/auth/me': json(200, TEST_ME) })
    renderApp('/')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'ログアウト' }))
    await user.click(await screen.findByRole('button', { name: 'キャンセル' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'ようこそ、レイズさん' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/logout')).toBe(false)
  })

  it('ログインの期限が切れていても、ログアウトするとログイン画面へ戻る', async () => {
    stubApi({
      'GET /api/auth/me': json(200, TEST_ME),
      'POST /api/auth/logout': UNAUTHENTICATED,
    })
    renderApp('/')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'ログアウト' }))
    await user.click(
      (await screen.findByRole('dialog')).querySelector('button.MuiButton-contained')!,
    )

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })
})

describe('ログインの期限切れ', () => {
  it('通信で期限切れが返ったら、メッセージを出してログイン画面へ移動する（S-02）', async () => {
    stubApi({ 'GET /api/auth/me': json(200, TEST_ME) })
    const { queryClient } = renderApp('/')
    await screen.findByRole('heading', { name: 'ようこそ、レイズさん' })

    // 画面のどこかの通信が、期限切れ（401 UNAUTHENTICATED）を返したことを真似る
    await act(() =>
      queryClient
        .fetchQuery({
          queryKey: ['any'],
          queryFn: () =>
            Promise.reject(new ApiError(401, 'UNAUTHENTICATED', 'ログインの有効期限が切れました')),
        })
        .catch(() => {}),
    )

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(
      screen.getByText('ログインの有効期限が切れました。もう一度ログインしてください'),
    ).toBeInTheDocument()
  })

  it('ログインの失敗（LOGIN_FAILED）は期限切れとして扱わない', async () => {
    stubApi({ 'GET /api/auth/me': json(200, TEST_ME) })
    const { queryClient } = renderApp('/')
    await screen.findByRole('heading', { name: 'ようこそ、レイズさん' })

    await act(() =>
      queryClient
        .fetchQuery({
          queryKey: ['any'],
          queryFn: () => Promise.reject(new ApiError(401, 'LOGIN_FAILED', '違います')),
        })
        .catch(() => {}),
    )

    expect(screen.getByRole('heading', { name: 'ようこそ、レイズさん' })).toBeInTheDocument()
  })
})
