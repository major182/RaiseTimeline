import { describe, expect, it, vi } from 'vitest'
import { ApiError, request } from './client'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('request', () => {
  it('GET では CSRF トークンを送らず、JSON の本文を返す', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { id: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('GET', '/api/auth/me')).resolves.toEqual({ id: 1 })
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('GET 以外では Cookie の CSRF トークンをヘッダーに入れて送る', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('POST', '/api/auth/logout')).resolves.toBeUndefined()
    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBe('abc123')
  })

  it('CSRF トークンの Cookie がなければ、先に受け取ってから送る', async () => {
    const fetchMock = vi.fn().mockImplementation((path: string) => {
      if (path === '/api/auth/csrf') {
        document.cookie = 'XSRF-TOKEN=fresh; path=/' // サーバーが Cookie を置いたことを真似る
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return Promise.resolve(jsonResponse(200, { ok: true }))
    })
    vi.stubGlobal('fetch', fetchMock)

    await request('POST', '/api/auth/login', { email: 'a', password: 'b' })
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(['/api/auth/csrf', '/api/auth/login'])
    expect((fetchMock.mock.calls[1][1].headers as Record<string, string>)['X-XSRF-TOKEN']).toBe(
      'fresh',
    )
  })

  it('エラーは code と入力欄ごとの誤りを持つ ApiError になる', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(409, {
          status: 409,
          code: 'CONFLICT',
          detail: '入力内容を確認してください',
          errors: [
            {
              field: 'username',
              code: 'USERNAME_TAKEN',
              message: 'このユーザー名は既に使われています',
            },
          ],
        }),
      ),
    )

    const error = await request('POST', '/api/auth/signup', {}).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(409)
    expect(apiError.code).toBe('CONFLICT')
    expect(apiError.fieldMessage('username')).toBe('このユーザー名は既に使われています')
    expect(apiError.fieldMessage('email')).toBeUndefined()
  })

  it('通信に失敗したら NETWORK_ERROR の ApiError になる', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(request('GET', '/api/auth/me')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    })
  })
})
