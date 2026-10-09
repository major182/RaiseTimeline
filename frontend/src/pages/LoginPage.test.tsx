import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { TEST_ME, UNAUTHENTICATED, authResponse, json, renderApp, stubApi } from '../test/render'

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup()
  const submit = await screen.findByRole('button', { name: 'ログイン' }) // 画面の読み込みを待つ
  if (email) await user.type(screen.getByLabelText('メールアドレス'), email)
  if (password) await user.type(screen.getByLabelText('パスワード'), password)
  await user.click(submit)
}

describe('ログイン画面（SC-01）', () => {
  it('正しく入力すると、ホームへ移動して名前が出る', async () => {
    const fetchMock = stubApi({
      'POST /api/auth/refresh': UNAUTHENTICATED,
      'POST /api/auth/login': authResponse(TEST_ME),
    })
    renderApp('/login')
    await fillAndSubmit('me@example.com', 'pass1234')

    expect(await screen.findByRole('heading', { name: 'ようこそ、レイズさん' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
    const loginCall = fetchMock.mock.calls.find((c) => c[0] === '/api/auth/login')!
    expect(JSON.parse(loginCall[1]!.body as string)).toEqual({
      email: 'me@example.com',
      password: 'pass1234',
    })
  })

  it('空のまま送ると、両方の欄に「入力してください」を出し、サーバーに送らない', async () => {
    const fetchMock = stubApi({ 'POST /api/auth/refresh': UNAUTHENTICATED })
    renderApp('/login')
    await fillAndSubmit('', '')

    expect(await screen.findAllByText('入力してください')).toHaveLength(2)
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/auth/login')).toBe(false)
  })

  it('失敗したら、どちらが違うかを区別しないメッセージを出す（E-20）', async () => {
    stubApi({
      'POST /api/auth/refresh': UNAUTHENTICATED,
      'POST /api/auth/login': json(401, {
        status: 401,
        code: 'LOGIN_FAILED',
        detail: 'メールアドレスかパスワードが違います',
      }),
    })
    renderApp('/login')
    await fillAndSubmit('me@example.com', 'wrong123')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'メールアドレスかパスワードが違います',
    )
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })

  it('ログインが止められていれば、そのメッセージを出す（E-21）', async () => {
    stubApi({
      'POST /api/auth/refresh': UNAUTHENTICATED,
      'POST /api/auth/login': json(401, {
        status: 401,
        code: 'LOGIN_LOCKED',
        detail:
          'ログインに続けて失敗したため、しばらくログインできません。15 分ほどたってからお試しください',
      }),
    })
    renderApp('/login')
    await fillAndSubmit('me@example.com', 'pass1234')

    expect(await screen.findByRole('alert')).toHaveTextContent('しばらくログインできません')
  })

  it('ログイン画面へ移動させられた場合は、ログインの後に元の画面へ戻る', async () => {
    stubApi({
      'POST /api/auth/refresh': UNAUTHENTICATED,
      'POST /api/auth/login': authResponse(TEST_ME),
    })
    renderApp('/?tab=all') // ホームを開こうとして、ログイン画面へ移動させられる
    await screen.findByRole('heading', { name: 'ログイン' })
    await fillAndSubmit('me@example.com', 'pass1234')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
    expect(await screen.findByRole('heading', { name: 'ようこそ、レイズさん' })).toBeInTheDocument()
  })

  it('パスワードの表示・非表示を切り替えられる', async () => {
    stubApi({ 'POST /api/auth/refresh': UNAUTHENTICATED })
    renderApp('/login')
    const user = userEvent.setup()
    const input = await screen.findByLabelText('パスワード')
    expect(input).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'パスワードを表示' }))
    expect(input).toHaveAttribute('type', 'text')
  })
})
