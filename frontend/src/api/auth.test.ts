import { describe, expect, it } from 'vitest'
import { TEST_ME, UNAUTHENTICATED, authResponse, json, noContent, stubApi } from '../test/render'
import { getMe, login, logout } from './auth'
import { getAccessToken, setAccessToken } from './tokenStore'

describe('認証の API（API 設計書 4.1）', () => {
  it('UT-AU-07 起動直後（アクセストークンがない）は、リフレッシュトークンで取り直して利用者を返す', async () => {
    const fetchMock = stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME, 'new-token') })

    await expect(getMe()).resolves.toEqual(TEST_ME)
    expect(getAccessToken()).toBe('new-token')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('UT-AU-07 取り直せなければ（ログインしていない）null を返す', async () => {
    stubApi({ 'POST /api/auth/refresh': UNAUTHENTICATED })

    await expect(getMe()).resolves.toBeNull()
    expect(getAccessToken()).toBeNull()
  })

  it('UT-AU-07 アクセストークンがあれば、取り直さずに /api/auth/me を呼ぶ', async () => {
    setAccessToken('token')
    const fetchMock = stubApi({ 'GET /api/auth/me': json(200, TEST_ME) })

    await expect(getMe()).resolves.toEqual(TEST_ME)
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/auth/me'])
  })

  it('UT-AU-07 ログインしたら、アクセストークンをメモリに置く', async () => {
    stubApi({ 'POST /api/auth/login': authResponse(TEST_ME, 'login-token') })

    await expect(login({ email: 'me@example.com', password: 'pass1234' })).resolves.toEqual(TEST_ME)
    expect(getAccessToken()).toBe('login-token')
  })

  it('UT-AU-07 ログアウトしたら、アクセストークンを捨てる', async () => {
    setAccessToken('token')
    stubApi({ 'POST /api/auth/logout': noContent() })

    await logout()
    expect(getAccessToken()).toBeNull()
  })

  it('UT-AU-07 ログアウトの通信に失敗しても、この端末ではログインしていない状態にする', async () => {
    setAccessToken('token')
    stubApi({ 'POST /api/auth/logout': json(500, { code: 'INTERNAL_ERROR', detail: '誤り' }) })

    await expect(logout()).rejects.toThrow('誤り')
    expect(getAccessToken()).toBeNull()
  })
})
