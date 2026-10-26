// 利用者の操作（API の呼び出し）。シナリオはこれらを組み合わせる
import http from 'k6/http'
import { check } from 'k6'
import { BASE_URL } from './config.js'
import { authParams } from './auth.js'

function ok(res, label) {
  check(res, { [`${label} が 2xx`]: (r) => r.status >= 200 && r.status < 300 })
  return res
}

export function me() {
  return ok(http.get(`${BASE_URL}/api/auth/me`, authParams('GET /api/auth/me', 'read')), 'me').json()
}

// tab は all か following。cursor を渡すと続きを取る
export function timeline(tab, cursor) {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  const name = cursor ? `GET /api/timeline/${tab}?cursor` : `GET /api/timeline/${tab}`
  return ok(http.get(`${BASE_URL}/api/timeline/${tab}${query}`, authParams(name, 'read')), name).json()
}

export function userPosts(userId) {
  const name = 'GET /api/users/{id}/posts'
  return ok(http.get(`${BASE_URL}/api/users/${userId}/posts`, authParams(name, 'read')), name).json()
}

export function userProfile(userId) {
  const name = 'GET /api/users/{id}'
  return ok(http.get(`${BASE_URL}/api/users/${userId}`, authParams(name, 'read')), name).json()
}

export function comments(postId) {
  const name = 'GET /api/posts/{id}/comments'
  return ok(http.get(`${BASE_URL}/api/posts/${postId}/comments`, authParams(name, 'read')), name).json()
}

export function searchUsers(q) {
  const name = 'GET /api/users/search'
  return ok(http.get(`${BASE_URL}/api/users/search?q=${encodeURIComponent(q)}`, authParams(name, 'read')), name).json()
}

export function createPost(body) {
  const name = 'POST /api/posts'
  // 投稿は multipart/form-data（画像なし）。k6 はファイルがないと form-urlencoded で送るため、本文を自分で組み立てる
  const boundary = 'perfboundary'
  const payload = `--${boundary}\r\nContent-Disposition: form-data; name="body"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n${body}\r\n--${boundary}--\r\n`
  const params = authParams(name, 'write')
  params.headers['Content-Type'] = `multipart/form-data; boundary=${boundary}`
  return ok(http.post(`${BASE_URL}/api/posts`, payload, params), name).json()
}

export function like(postId) {
  const name = 'PUT /api/posts/{id}/like'
  return ok(http.put(`${BASE_URL}/api/posts/${postId}/like`, null, authParams(name, 'write')), name)
}

export function unlike(postId) {
  const name = 'DELETE /api/posts/{id}/like'
  return ok(http.del(`${BASE_URL}/api/posts/${postId}/like`, null, authParams(name, 'write')), name)
}

export function createComment(postId, body, parentId) {
  const name = 'POST /api/posts/{id}/comments'
  const params = authParams(name, 'write')
  params.headers['Content-Type'] = 'application/json'
  const payload = parentId ? { body, parentId } : { body }
  return ok(http.post(`${BASE_URL}/api/posts/${postId}/comments`, JSON.stringify(payload), params), name).json()
}
