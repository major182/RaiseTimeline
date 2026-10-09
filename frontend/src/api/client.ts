// API を呼ぶ共通の関数（API 設計書 2 章）
// - GET 以外では、CSRF トークン（XSRF-TOKEN Cookie の値）を X-XSRF-TOKEN ヘッダーに入れて送る
// - エラーは ApiError にして投げる。画面は code（エラーの種類）と errors（入力欄ごとの誤り）で表示を決める

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

const NETWORK_ERROR_MESSAGE = '通信に失敗しました。時間をおいてもう一度お試しください' // 画面設計書 S-01

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

function readCookie(name: string): string | undefined {
  const found = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))
  return found ? decodeURIComponent(found.slice(name.length + 1)) : undefined
}

/** CSRF トークンの Cookie を受け取る。画面の起動時と、ログアウトの後に呼ぶ。 */
export async function fetchCsrfToken(): Promise<void> {
  await request('GET', '/api/auth/csrf')
}

/** API を呼び、JSON の本文を返す（本文がない 204 のときは undefined）。 */
export async function request<T = void>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    // トークンの Cookie がなければ先に受け取る（ログアウトの後など）
    if (!readCookie('XSRF-TOKEN')) await fetchCsrfToken()
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE)
  }

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
