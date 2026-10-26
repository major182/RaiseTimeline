// ログインとトークンの管理。仮想利用者（VU）ごとに別のテストデータの利用者でログインする
import http, { type Params } from 'k6/http'
import { check, fail } from 'k6'
import { BASE_URL, PASSWORD, USER_COUNT } from './config.ts'

// 集計の単位。read・write はしきい値の対象（config.ts）、auth はログイン
export type RequestKind = 'read' | 'write' | 'auth'

interface Session {
  token: string
  loggedInAt: number
}

// headers を必ず持つ Params（呼び出し側で Content-Type を足すため）
export type AuthParams = Params & { headers: Record<string, string> }

// アクセストークンの期限は 15 分。期限の前に取り直す（長時間のシナリオ用）
const RELOGIN_AFTER_MS = 10 * 60 * 1000

// VU ごとの状態（k6 は VU ごとにこのモジュールを別に読み込むので、ほかの VU とは共有されない）
let session: Session | null = null

export function userEmail(n: number): string {
  return `perf-${String(n).padStart(4, '0')}@perf.example.com`
}

function login(): Session {
  // VU の番号から利用者を決める。USER_COUNT を超えたら先頭に戻る
  const n = ((__VU - 1) % USER_COUNT) + 1
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: userEmail(n), password: PASSWORD }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/auth/login', kind: 'auth' },
    },
  )
  if (!check(res, { ログインできる: (r) => r.status === 200 })) {
    fail(`ログインに失敗しました（${res.status}）。テストデータが入っているか確かめてください`)
  }
  return { token: res.json('accessToken') as string, loggedInAt: Date.now() }
}

// Authorization を付けた共通のパラメータ。name は集計の単位（URL の ID ごとに分かれないようにする）
export function authParams(name: string, kind: RequestKind): AuthParams {
  if (session === null || Date.now() - session.loggedInAt > RELOGIN_AFTER_MS) {
    session = login()
  }
  return {
    headers: { Authorization: `Bearer ${session.token}` },
    tags: { name, kind },
  }
}
