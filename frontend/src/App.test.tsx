import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from './api/tokenStore'
import { TEST_ME, UNAUTHENTICATED, authResponse, renderApp, stubApi } from './test/render'

describe('画面の出し分け', () => {
  it('ログインしていなければ、ホームを開くとログイン画面へ移動する', async () => {
    stubApi({ 'POST /api/auth/refresh': UNAUTHENTICATED })
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })

  it('ログインしていれば、ホームに名前が出る', async () => {
    stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME) })
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'ようこそ、レイズさん' })).toBeInTheDocument()
  })

  it('ログインしていれば、ログイン画面と登録画面からホームへ移動する', async () => {
    for (const path of ['/login', '/signup']) {
      stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME) })
      const { unmount } = renderApp(path)
      expect(
        await screen.findByRole('heading', { name: 'ようこそ、レイズさん' }),
      ).toBeInTheDocument()
      unmount()
      setAccessToken(null) // 次の画面は、再読み込みした状態から始める
    }
  })

  it('ログインしていなくても、登録画面は開ける', async () => {
    stubApi({ 'POST /api/auth/refresh': UNAUTHENTICATED })
    renderApp('/signup')
    expect(await screen.findByRole('heading', { name: 'アカウントを作成' })).toBeInTheDocument()
  })

  it('どの画面にも当たらない URL は「見つかりません」を出す', async () => {
    stubApi({})
    renderApp('/no-such-page')
    expect(screen.getByRole('heading', { name: 'ページが見つかりません' })).toBeInTheDocument()
  })
})
