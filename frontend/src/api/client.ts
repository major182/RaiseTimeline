// API を呼ぶ共通の関数（API 設計書 2 章）
// - アクセストークンを Authorization: Bearer ヘッダーに入れて送る（JWT 方式。技術選定書 4.1）
// - アクセストークンの期限切れ（401）なら、一度だけ取り直して同じ通信をやり直す
// - エラーは ApiError にして投げる。画面は code（エラーの種類）と errors（入力欄ごとの誤り）で表示を決める
import { getAccessToken, setAccessToken } from './tokenStore'

/** 入力欄ごとの誤り（API 設計書 2.4 の errors の1件）。 */
export type FieldError = { field: string; code: string; message: string }

/** API が返したエラー。通信そのものに失敗したときは status が 0、code が NETWORK_ERROR。 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: FieldError[]

  constructor(status: number, code: string, message: string, errors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.errors = errors
  }

  /** 指定した入力欄の誤りのメッセージ。なければ undefined。 */
  fieldMessage(field: string): string | undefined {
    return this.errors.find((e) => e.field === field)?.message
  }
}

/** 登録・ログイン・取り直しの応答（API 設計書 3.6）。 */
export type AuthResponse<U> = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: U
}

const NETWORK_ERROR_MESSAGE = '通信に失敗しました。時間をおいてもう一度お試しください' // 画面設計書 S-01
const REFRESH_PATH = '/api/auth/refresh'

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/**
 * API を呼び、JSON の本文を返す（本文がない 204 のときは undefined）。
 * body が FormData なら multipart/form-data で送る（投稿の作成など。API 設計書 2.1）。ほかは JSON にして送る。
 */
export async function request<T = void>(method: Method, path: string, body?: unknown): Promise<T> {
  const response = await send(method, path, body)
  // 期限切れなら一度だけ取り直してやり直す。認証の API 自体（ログインの失敗など）は対象外
  if (response.status === 401 && !path.startsWith('/api/auth/') && getAccessToken() !== null) {
    if (await refreshAccessToken()) return parse<T>(await send(method, path, body))
  }
  return parse<T>(response)
}

/** 取り直しの呼び出しを1つにまとめるための、実行中の取り直し。 */
let refreshing: Promise<AuthResponse<unknown> | null> | null = null

/**
 * リフレッシュトークン（Cookie）でアクセストークンを取り直す。取り直せなければ null。
 * 同時に何度呼ばれても、サーバーへの取り直しは1回にまとめる（ローテーションで古いトークンが使えなくなるため）。
 */
export function refreshSession<U>(): Promise<AuthResponse<U> | null> {
  refreshing ??= (async () => {
    try {
      const response = await send('POST', REFRESH_PATH)
      if (!response.ok) {
        setAccessToken(null)
        return null
      }
      const auth = (await response.json()) as AuthResponse<unknown>
      setAccessToken(auth.accessToken)
      return auth
    } finally {
      refreshing = null
    }
  })()
  return refreshing as Promise<AuthResponse<U> | null>
}

async function refreshAccessToken(): Promise<boolean> {
  return (await refreshSession()) !== null
}

async function send(method: Method, path: string, body?: unknown): Promise<Response> {
  // フォーム（画像を送る API）は、ブラウザが区切りの文字を含めた Content-Type を付けるので、自分では付けない
  const isForm = body instanceof FormData
  const headers: Record<string, string> = {
    Accept: 'application/json',
    // Cookie を使う API（取り直し・ログアウト）で必須にしているヘッダー（API 設計書 2.2 の CSRF の対策）
    'X-Requested-With': 'RaiseTimeline',
  }
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    return await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE)
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    if (response.status === 204) return undefined as T
    return (await response.json()) as T
  }
  throw await toApiError(response)
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as {
      code?: string
      detail?: string
      errors?: FieldError[]
    }
    return new ApiError(
      response.status,
      problem.code ?? 'INTERNAL_ERROR',
      problem.detail ?? NETWORK_ERROR_MESSAGE,
      problem.errors ?? [],
    )
  } catch {
    // 本文が JSON でない（プロキシの誤りなど）
    return new ApiError(
      response.status,
      'INTERNAL_ERROR',
      'エラーが発生しました。時間をおいてもう一度お試しください',
    )
  }
}
