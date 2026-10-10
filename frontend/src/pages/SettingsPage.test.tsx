import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { TEST_ME, authResponse, json, noContent, renderApp, stubApi } from '../test/render'

function openSettings(extra: Parameters<typeof stubApi>[0] = {}) {
  const fetchMock = stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME), ...extra })
  renderApp('/settings')
  return { fetchMock, user: userEvent.setup() }
}

async function openLogoutDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'ログアウト' }))
  return screen.findByRole('dialog', { name: 'ログアウトしますか？' })
}

describe('設定（SC-10）', () => {
  it('プロフィールの編集へのリンクを出す', async () => {
    openSettings()
    expect(await screen.findByRole('link', { name: 'プロフィールを編集' })).toHaveAttribute(
      'href',
      '/settings/profile',
    )
  })
})

describe('パスワードの変更（F-AU-05）', () => {
  async function fill(user: ReturnType<typeof userEvent.setup>, values: string[]) {
    const labels = ['現在のパスワード', '新しいパスワード', '新しいパスワード（確認）']
    for (const [i, label] of labels.entries()) {
      if (values[i]) await user.type(await screen.findByLabelText(label), values[i])
    }
    await user.click(screen.getByRole('button', { name: '変更' }))
  }

  it('変更したら入力欄を空にし、I-07 を出す', async () => {
    let sent: unknown
    const { user } = openSettings({
      'PUT /api/me/password': (body) => {
        sent = body
        return noContent()
      },
    })
    await fill(user, ['oldpass1', 'newpass1', 'newpass1'])
    expect(await screen.findByText('パスワードを変更しました')).toBeInTheDocument()
    expect(sent).toEqual({
      currentPassword: 'oldpass1',
      newPassword: 'newpass1',
      newPasswordConfirmation: 'newpass1',
    })
    expect(screen.getByLabelText('現在のパスワード')).toHaveValue('')
  })

  it('送る前に E-01・E-04・E-05 を確かめる', async () => {
    const { user, fetchMock } = openSettings()
    await fill(user, ['', 'short', 'other'])
    expect(screen.getByText('入力してください')).toBeInTheDocument()
    expect(
      screen.getByText('8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください'),
    ).toBeInTheDocument()
    expect(screen.getByText('パスワードが一致しません')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/me/password')).toBe(false)
  })

  it('今のパスワードが違えば、その入力欄の下に E-22 を出す', async () => {
    const { user } = openSettings({
      'PUT /api/me/password': json(400, {
        status: 400,
        code: 'CURRENT_PASSWORD_WRONG',
        detail: '現在のパスワードが違います',
        errors: [
          {
            field: 'currentPassword',
            code: 'CURRENT_PASSWORD_WRONG',
            message: '現在のパスワードが違います',
          },
        ],
      }),
    })
    await fill(user, ['wrongpass1', 'newpass1', 'newpass1'])
    expect(await screen.findByText('現在のパスワードが違います')).toBeInTheDocument()
    expect(screen.getByLabelText('現在のパスワード')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('ログアウト（F-AU-03）', () => {
  it('確認してからログアウトし、ログイン画面へ戻る', async () => {
    const { user, fetchMock } = openSettings({ 'POST /api/auth/logout': noContent() })
    const dialog = await openLogoutDialog(user)
    // 確認のダイアログ（C-04）。最初はキャンセルを選んだ状態
    await waitFor(() => expect(screen.getByRole('button', { name: 'キャンセル' })).toHaveFocus())
    await user.click(dialog.querySelector('button.MuiButton-contained')!)

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/logout')).toBe(true)
  })

  it('確認でキャンセルすれば、ログアウトしない', async () => {
    const { user, fetchMock } = openSettings()
    await openLogoutDialog(user)
    await user.click(screen.getByRole('button', { name: 'キャンセル' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('heading', { name: '設定' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/logout')).toBe(false)
  })

  it('ログアウトに失敗したら、メッセージを出して設定に残る', async () => {
    const { user } = openSettings({
      'POST /api/auth/logout': json(500, {
        status: 500,
        code: 'INTERNAL_ERROR',
        detail: 'エラーが発生しました。時間をおいてもう一度お試しください',
      }),
    })
    const dialog = await openLogoutDialog(user)
    await user.click(dialog.querySelector('button.MuiButton-contained')!)

    expect(
      await screen.findByText('エラーが発生しました。時間をおいてもう一度お試しください'),
    ).toBeInTheDocument()
    // ダイアログが閉じ終わるのを待つ（閉じる途中は、画面のほかの部分が読み上げの対象から外れている）
    expect(await screen.findByRole('heading', { name: '設定' })).toBeInTheDocument()
  })
})
