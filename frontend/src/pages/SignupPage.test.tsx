import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { TEST_ME, UNAUTHENTICATED, authResponse, json, renderApp, stubApi } from '../test/render'

type Input = { email: string; password: string; confirmation: string; username: string }

const VALID: Input = {
  email: 'me@example.com',
  password: 'pass1234',
  confirmation: 'pass1234',
  username: 'raise_me',
}

async function fillAndSubmit(input: Input) {
  const user = userEvent.setup()
  const submit = await screen.findByRole('button', { name: '登録' }) // 画面の読み込みを待つ
  const type = async (label: string, value: string) => {
    if (value) await user.type(screen.getByLabelText(label, { exact: true }), value)
  }
  await type('メールアドレス', input.email)
  await type('パスワード', input.password)
  await type('パスワード（確認）', input.confirmation)
  await type('ユーザー名', input.username)
  await user.click(submit)
}

describe('利用者登録画面（SC-02）', () => {
  it('登録すると、そのままホームへ移動し、完了の知らせを出す', async () => {
    const fetchMock = stubApi({
      'POST /api/auth/refresh': UNAUTHENTICATED,
      'POST /api/auth/signup': authResponse(TEST_ME),
    })
    renderApp('/signup')
    await fillAndSubmit(VALID)

    expect(await screen.findByRole('heading', { name: 'ようこそ、レイズさん' })).toBeInTheDocument()
    expect(await screen.findByText('ようこそ、RaiseTimeline へ')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find((c) => c[0] === '/api/auth/signup')!
    expect(JSON.parse(call[1]!.body as string)).toEqual({
      email: 'me@example.com',
      password: 'pass1234',
      passwordConfirmation: 'pass1234',
      username: 'raise_me',
    })
  })

  it('画面で見つけた誤りは入力欄ごとに出し、サーバーに送らない', async () => {
    const fetchMock = stubApi({ 'POST /api/auth/refresh': UNAUTHENTICATED })
    renderApp('/signup')
    await fillAndSubmit({ email: 'x', password: 'short', confirmation: 'other', username: 'a' })

    expect(await screen.findByText('メールアドレスの形式が正しくありません')).toBeInTheDocument()
    expect(
      screen.getByText('8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください'),
    ).toBeInTheDocument()
    expect(screen.getByText('パスワードが一致しません')).toBeInTheDocument()
    expect(screen.getByText('4〜15 文字の半角英数字と「_」で入力してください')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/signup')).toBe(false)
  })

  it('使われているメールアドレス・ユーザー名は、サーバーの誤りを該当の入力欄の下に出す', async () => {
    stubApi({
      'POST /api/auth/refresh': UNAUTHENTICATED,
      'POST /api/auth/signup': json(409, {
        status: 409,
        code: 'CONFLICT',
        detail: '入力内容を確認してください',
        errors: [
          {
            field: 'email',
            code: 'EMAIL_TAKEN',
            message: 'このメールアドレスは既に使われています',
          },
          {
            field: 'username',
            code: 'USERNAME_TAKEN',
            message: 'このユーザー名は既に使われています',
          },
        ],
      }),
    })
    renderApp('/signup')
    await fillAndSubmit(VALID)

    const email = screen.getByLabelText('メールアドレス', { exact: true })
    expect(email).toHaveAccessibleDescription('このメールアドレスは既に使われています')
    expect(screen.getByLabelText('ユーザー名', { exact: true })).toHaveAccessibleDescription(
      'このユーザー名は既に使われています',
    )
    expect(screen.getByTestId('location')).toHaveTextContent('/signup')
  })

  it('通信に失敗したら、入力欄の上にメッセージを出す', async () => {
    stubApi({
      'POST /api/auth/refresh': UNAUTHENTICATED,
      'POST /api/auth/signup': json(500, {
        status: 500,
        code: 'INTERNAL_ERROR',
        detail: 'エラーが発生しました。時間をおいてもう一度お試しください',
      }),
    })
    renderApp('/signup')
    await fillAndSubmit(VALID)

    expect(await screen.findByRole('alert')).toHaveTextContent('エラーが発生しました')
  })
})
