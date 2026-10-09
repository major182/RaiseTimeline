import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { TEST_ME, authResponse, json, noContent, renderApp, stubApi } from '../test/render'

/** アカウントのメニューから、ログアウトの確認のダイアログを開く。 */
async function openLogoutDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'アカウントのメニュー' }))
  await user.click(await screen.findByRole('menuitem', { name: 'ログアウト' }))
  return screen.findByRole('dialog', { name: 'ログアウトしますか？' })
}

describe('画面の枠（画面設計書 1.1）', () => {
  it('ナビにホームを出す', async () => {
    stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME) })
    renderApp('/')
    const nav = await screen.findByRole('navigation', { name: 'メインのナビ' })
    expect(nav).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'ホーム' })).toHaveAttribute('href', '/')
  })
})

describe('ログアウト（F-AU-03）', () => {
  it('確認してからログアウトし、ログイン画面へ戻る', async () => {
    const fetchMock = stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      'POST /api/auth/logout': noContent(),
    })
    renderApp('/')
    const user = userEvent.setup()
    const dialog = await openLogoutDialog(user)
    // 確認のダイアログ（C-04）。最初はキャンセルを選んだ状態
    await waitFor(() => expect(screen.getByRole('button', { name: 'キャンセル' })).toHaveFocus())
    await user.click(dialog.querySelector('button.MuiButton-contained')!)

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/logout')).toBe(true)
  })

  it('確認でキャンセルすれば、ログアウトしない', async () => {
    const fetchMock = stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME) })
    renderApp('/')
    const user = userEvent.setup()
    await openLogoutDialog(user)
    await user.click(screen.getByRole('button', { name: 'キャンセル' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/logout')).toBe(false)
  })

  it('ログアウトに失敗したら、メッセージを出してホームに残る', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      'POST /api/auth/logout': json(500, {
        status: 500,
        code: 'INTERNAL_ERROR',
        detail: 'エラーが発生しました。時間をおいてもう一度お試しください',
      }),
    })
    renderApp('/')
    const user = userEvent.setup()
    const dialog = await openLogoutDialog(user)
    await user.click(dialog.querySelector('button.MuiButton-contained')!)

    expect(
      await screen.findByText('エラーが発生しました。時間をおいてもう一度お試しください'),
    ).toBeInTheDocument()
    // ダイアログが閉じ終わるのを待つ（閉じる途中は、画面のほかの部分が読み上げの対象から外れている）
    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })
})
