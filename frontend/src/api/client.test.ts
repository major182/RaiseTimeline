import { describe, expect, it, vi } from 'vitest'
import { ApiError, refreshSession, request, withRequestId } from './client'
import { getAccessToken, setAccessToken } from './tokenStore'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const UNAUTHENTICATED = () =>
  jsonResponse(401, { status: 401, code: 'UNAUTHENTICATED', detail: '期限切れ' })

const headersOf = (call: unknown[]) => (call[1] as RequestInit).headers as Record<string, string>

describe('request', () => {
  it('アクセストークンを Authorization ヘッダーに入れ、X-Requested-With も付ける', async () => {
    setAccessToken('token-1')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { id: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('GET', '/api/auth/me')).resolves.toEqual({ id: 1 })
    expect(headersOf(fetchMock.mock.calls[0])).toMatchObject({
      Authorization: 'Bearer token-1',
      'X-Requested-With': 'RaiseTimeline',
    })
  })

  it('アクセストークンがなければ Authorization ヘッダーを付けない', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('POST', '/api/auth/logout')).resolves.toBeUndefined()
    expect(headersOf(fetchMock.mock.calls[0]).Authorization).toBeUndefined()
  })

  it('期限切れ（401）なら一度だけ取り直し、新しいトークンで同じ通信をやり直す', async () => {
    setAccessToken('old')
    const fetchMock = vi.fn().mockImplementation((path: string, init: RequestInit) => {
      if (path === '/api/auth/refresh') {
        return Promise.resolve(
          jsonResponse(200, { accessToken: 'new', tokenType: 'Bearer', expiresIn: 900, user: {} }),
        )
      }
      const auth = (init.headers as Record<string, string>).Authorization
      return Promise.resolve(
        auth === 'Bearer new' ? jsonResponse(200, { ok: true }) : UNAUTHENTICATED(),
      )
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('GET', '/api/posts')).resolves.toEqual({ ok: true })
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      '/api/posts',
      '/api/auth/refresh',
      '/api/posts',
    ])
    expect(getAccessToken()).toBe('new')
  })

  it('取り直せなければ、期限切れの ApiError を投げてトークンを捨てる', async () => {
    setAccessToken('old')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(UNAUTHENTICATED())),
    )

    await expect(request('GET', '/api/posts')).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHENTICATED',
    })
    expect(getAccessToken()).toBeNull()
  })

  it('認証の API（ログインの失敗など）の 401 では取り直さない', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse(401, { status: 401, code: 'LOGIN_FAILED', detail: '違います' }),
      )
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('POST', '/api/auth/login', {})).rejects.toMatchObject({
      code: 'LOGIN_FAILED',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('エラーは code と入力欄ごとの誤りを持つ ApiError になる', async () => {
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

describe('refreshSession', () => {
  it('同時に何度呼ばれても、サーバーへの取り直しは1回にまとめる', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(200, {
        accessToken: 'new',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: { id: 1 },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const results = await Promise.all([refreshSession(), refreshSession(), refreshSession()])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(results.every((r) => r?.accessToken === 'new')).toBe(true)
  })
})

describe('問い合わせ番号（X-Request-Id）', () => {
  const withId = (status: number, body: unknown, requestId = 'req-1') =>
    new Response(typeof body === 'string' ? body : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId },
    })

  it('サーバーの誤り（500）ならメッセージの後ろに問い合わせ番号を付ける', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(withId(500, { code: 'INTERNAL_ERROR', detail: 'エラーです' })),
    )

    const error = await request('GET', '/api/posts').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).message).toBe('エラーです（問い合わせ番号：req-1）')
    expect((error as ApiError).requestId).toBe('req-1')
  })

  it('本文が JSON でない 5xx（プロキシの誤りなど）でも、番号があれば付ける', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(withId(502, '<html>')))

    await expect(request('GET', '/api/posts')).rejects.toMatchObject({
      status: 502,
      message: 'エラーが発生しました。時間をおいてもう一度お試しください（問い合わせ番号：req-1）',
    })
  })

  it('入力の誤りなどの 4xx には付けないが、requestId は持つ', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(withId(409, { code: 'CONFLICT', detail: '使われています' })),
    )

    await expect(request('POST', '/api/posts', {})).rejects.toMatchObject({
      message: '使われています',
      requestId: 'req-1',
    })
  })

  it('状態コードの境界（499・500・599）と番号のあり／なし', () => {
    expect(withRequestId('m', 499, 'id')).toBe('m')
    expect(withRequestId('m', 500, 'id')).toBe('m（問い合わせ番号：id）')
    expect(withRequestId('m', 599, 'id')).toBe('m（問い合わせ番号：id）')
    expect(withRequestId('m', 500, undefined)).toBe('m')
    expect(withRequestId('m', 500, '')).toBe('m')
  })

  it('応答に番号がなければ（ALB が返した 503 など）付けない', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>', { status: 503 })))

    await expect(request('GET', '/api/posts')).rejects.toMatchObject({
      message: 'エラーが発生しました。時間をおいてもう一度お試しください',
      requestId: undefined,
    })
  })
})
