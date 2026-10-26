// ログインとトークンの管理。仮想利用者（VU）ごとに別のテストデータの利用者でログインする
import http from 'k6/http'
import { check, fail } from 'k6'
import { BASE_URL, PASSWORD, USER_COUNT } from './config.js'

// アクセストークンの期限は 15 分。期限の前に取り直す（長時間のシナリオ用）
const RELOGIN_AFTER_MS = 10 * 60 * 1000

// VU ごとの状態（k6 は VU ごとにこのモジュールを別に読み込むので、ほかの VU とは共有されない）
let session = null

export function userEmail(n) {
  return `perf-${String(n).padStart(4, '0')}@perf.example.com`
}

function login() {
  // VU の番号から利用者を決める。USER_COUNT を超えたら先頭に戻る
  const n = ((__VU - 1) % USER_COUNT) + 1
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email: userEmail(n), password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /api/auth/login', kind: 'auth' },
  })
  if (!check(res, { 'ログインできる': (r) => r.status === 200 })) {
    fail(`ログインに失敗しました（${res.status}）。テストデータが入っているか確かめてください`)
  }
  session = { token: res.json('accessToken'), loggedInAt: Date.now() }
}

// Authorization を付けた共通のパラメータ。name は集計の単位（URL の ID ごとに分かれないようにする）
export function authParams(name, kind) {
  if (session === null || Date.now() - session.loggedInAt > RELOGIN_AFTER_MS) {
    login()
  }
  return {
    headers: { Authorization: `Bearer ${session.token}` },
    tags: { name, kind },
  }
}
