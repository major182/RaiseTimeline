// 利用者の操作（API の呼び出し）。シナリオはこれらを組み合わせる
// 応答の型は使う項目だけを書く（全体は docs/api/openapi.yaml）
import http, { type RefinedResponse, type ResponseType } from 'k6/http'
import { check } from 'k6'
import { BASE_URL } from './config.ts'
import { authParams } from './auth.ts'

export interface UserSummary {
  id: number
  username: string
}

export interface Me {
  id: number
  username: string
}

export interface Post {
  id: number
  author: UserSummary
  likedByMe: boolean
}

export interface Comment {
  id: number
}

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

export type Tab = 'all' | 'following'

function ok<T>(res: RefinedResponse<ResponseType | undefined>, label: string): T {
  check(res, { [`${label} が 2xx`]: (r) => r.status >= 200 && r.status < 300 })
  return res.json() as T
}

export function me(): Me {
  return ok(http.get(`${BASE_URL}/api/auth/me`, authParams('GET /api/auth/me', 'read')), 'me')
}

// cursor を渡すと続きを取る
export function timeline(tab: Tab, cursor?: string): CursorPage<Post> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  const name = cursor ? `GET /api/timeline/${tab}?cursor` : `GET /api/timeline/${tab}`
  return ok(http.get(`${BASE_URL}/api/timeline/${tab}${query}`, authParams(name, 'read')), name)
}

export function userPosts(userId: number): CursorPage<Post> {
  const name = 'GET /api/users/{id}/posts'
  return ok(http.get(`${BASE_URL}/api/users/${userId}/posts`, authParams(name, 'read')), name)
}

export function userProfile(userId: number): UserSummary {
  const name = 'GET /api/users/{id}'
  return ok(http.get(`${BASE_URL}/api/users/${userId}`, authParams(name, 'read')), name)
}

export function comments(postId: number): CursorPage<Comment> {
  const name = 'GET /api/posts/{id}/comments'
  return ok(http.get(`${BASE_URL}/api/posts/${postId}/comments`, authParams(name, 'read')), name)
}

export function searchUsers(q: string): CursorPage<UserSummary> {
  const name = 'GET /api/users/search'
  return ok(
    http.get(`${BASE_URL}/api/users/search?q=${encodeURIComponent(q)}`, authParams(name, 'read')),
    name,
  )
}

export function createPost(body: string): Post {
  const name = 'POST /api/posts'
  // 投稿は multipart/form-data（画像なし）。k6 はファイルがないと form-urlencoded で送るため、本文を自分で組み立てる
  const boundary = 'perfboundary'
  const payload = `--${boundary}\r\nContent-Disposition: form-data; name="body"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n${body}\r\n--${boundary}--\r\n`
  const params = authParams(name, 'write')
  params.headers['Content-Type'] = `multipart/form-data; boundary=${boundary}`
  return ok(http.post(`${BASE_URL}/api/posts`, payload, params), name)
}

export function like(postId: number): void {
  const name = 'PUT /api/posts/{id}/like'
  ok(http.put(`${BASE_URL}/api/posts/${postId}/like`, null, authParams(name, 'write')), name)
}

export function unlike(postId: number): void {
  const name = 'DELETE /api/posts/{id}/like'
  ok(http.del(`${BASE_URL}/api/posts/${postId}/like`, null, authParams(name, 'write')), name)
}

export function createComment(postId: number, body: string, parentId?: number): Comment {
  const name = 'POST /api/posts/{id}/comments'
  const params = authParams(name, 'write')
  params.headers['Content-Type'] = 'application/json'
  const payload = parentId ? { body, parentId } : { body }
  return ok(
    http.post(`${BASE_URL}/api/posts/${postId}/comments`, JSON.stringify(payload), params),
    name,
  )
}
