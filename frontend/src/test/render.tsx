import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { vi } from 'vitest'
import { App } from '../App'
import type { Me } from '../api/auth'
import { SessionExpiryHandler } from '../auth/SessionExpiryHandler'
import { createQueryClient } from '../auth/session'
import { SnackbarProvider } from '../components/SnackbarProvider'

export const TEST_ME: Me = {
  id: 1,
  username: 'raise_me',
  displayName: 'レイズ',
  avatarUrl: null,
  email: 'me@example.com',
}

/** 返す値を API ごとに決める。関数なら、呼ばれるたびに送られた本文を受け取って Response を返す。 */
export type ApiStub = Record<string, Response | ((body: unknown) => Response)>

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export const noContent = () => new Response(null, { status: 204 })

/** 登録・ログイン・取り直しの応答（API 設計書 3.6）。 */
export function authResponse(user: Me, accessToken = 'test-access-token') {
  return json(200, { accessToken, tokenType: 'Bearer', expiresIn: 900, user })
}

/** fetch を真似る。キーは「メソッド パス」（例：'POST /api/auth/login'）。 */
export function stubApi(stub: ApiStub) {
  const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${path}`
    const entry = stub[key]
    if (!entry) throw new Error(`テストで用意していない API が呼ばれました: ${key}`)
    if (typeof entry === 'function') {
      return entry(init?.body ? JSON.parse(init.body as string) : undefined)
    }
    return entry.clone()
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** 今の URL を画面に出す（移動先を確かめるため）。 */
function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}</div>
}

/** App を、指定した URL から表示する。 */
export function renderApp(path: string) {
  const queryClient = createQueryClient()
  const result = render(
    <QueryClientProvider client={queryClient}>
      <SnackbarProvider>
        <SessionExpiryHandler />
        <MemoryRouter initialEntries={[path]}>
          <App />
          <LocationProbe />
        </MemoryRouter>
      </SnackbarProvider>
    </QueryClientProvider>,
  )
  return { ...result, queryClient }
}

/** ログインしていない（取り直せない）ときの応答（401）。 */
export const UNAUTHENTICATED = json(401, {
  status: 401,
  code: 'UNAUTHENTICATED',
  detail: 'ログインの有効期限が切れました。もう一度ログインしてください',
})
