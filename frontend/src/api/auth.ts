// 認証の API（API 設計書 4.1）。アクセストークンはメモリ（tokenStore）に置く
import { type AuthResponse, refreshSession, request } from './client'
import { getAccessToken, setAccessToken } from './tokenStore'

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

/**
 * ログインしている利用者を取る。ログインしていなければ null。
 * アクセストークンがなければ（起動直後・再読み込みの後）、リフレッシュトークンで取り直す。
 */
export async function getMe(): Promise<Me | null> {
  if (getAccessToken() === null) {
    const auth = await refreshSession<Me>()
    return auth?.user ?? null
  }
  return request<Me>('GET', '/api/auth/me')
}

export async function login(input: LoginInput): Promise<Me> {
  return keep(await request<AuthResponse<Me>>('POST', '/api/auth/login', input))
}

export async function signup(input: SignupInput): Promise<Me> {
  return keep(await request<AuthResponse<Me>>('POST', '/api/auth/signup', input))
}

/**
 * ログアウトする。サーバーがリフレッシュトークンを無効にして Cookie を消し、画面はアクセストークンを捨てる。
 * 通信に失敗しても、この端末ではログインしていない状態にする。
 */
export async function logout(): Promise<void> {
  try {
    await request('POST', '/api/auth/logout')
  } finally {
    setAccessToken(null)
  }
}

function keep(auth: AuthResponse<Me>): Me {
  setAccessToken(auth.accessToken)
  return auth.user
}
