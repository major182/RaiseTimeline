// 認証の API（API 設計書 4.1）
import { ApiError, fetchCsrfToken, request } from './client'

/** ログインしている利用者（API 設計書 3.3）。 */
export type Me = {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
  email: string
}

export type LoginInput = { email: string; password: string }

export type SignupInput = {
  email: string
  password: string
  passwordConfirmation: string
  username: string
}

/** ログインしている利用者を取る。ログインしていなければ null。 */
export async function getMe(): Promise<Me | null> {
  try {
    return await request<Me>('GET', '/api/auth/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export function login(input: LoginInput): Promise<Me> {
  return request<Me>('POST', '/api/auth/login', input)
}

export function signup(input: SignupInput): Promise<Me> {
  return request<Me>('POST', '/api/auth/signup', input)
}

/** ログアウトする。サーバーが CSRF トークンの Cookie を消すので、次の操作に備えて取り直す。 */
export async function logout(): Promise<void> {
  await request('POST', '/api/auth/logout')
  await fetchCsrfToken()
}
