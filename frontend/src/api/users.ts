// 利用者・プロフィール・フォローの一覧の API（API 設計書 4.2・4.3・4.7）
import { request } from './client'
import { withCursor } from './cursor'
import type { CursorPage, Post, Profile, UserSummary } from './types'

/** ユーザー名で探す（大文字・小文字は区別しない）。いなければ 404。 */
export function getProfileByUsername(username: string): Promise<Profile> {
  return request('GET', `/api/users/by-username/${encodeURIComponent(username)}`)
}

/** この人の投稿（新しい順）。 */
export function getUserPosts(userId: number, cursor: string | null): Promise<CursorPage<Post>> {
  return request('GET', withCursor(`/api/users/${userId}/posts`, cursor))
}

/** この人がフォローしている人（フォローした時刻の新しい順）。 */
export function getFollowing(
  userId: number,
  cursor: string | null,
): Promise<CursorPage<UserSummary>> {
  return request('GET', withCursor(`/api/users/${userId}/following`, cursor))
}

/** この人のフォロワー（フォローされた時刻の新しい順）。 */
export function getFollowers(
  userId: number,
  cursor: string | null,
): Promise<CursorPage<UserSummary>> {
  return request('GET', withCursor(`/api/users/${userId}/followers`, cursor))
}

/** ID で取る（プロフィールの編集の画面で、今の値を出すため）。 */
export function getUser(userId: number): Promise<Profile> {
  return request('GET', `/api/users/${userId}`)
}

/** プロフィールを変える。送った項目だけ変わる。 */
export function updateProfile(
  input: Partial<Pick<Profile, 'displayName' | 'username' | 'bio'>>,
): Promise<Profile> {
  return request('PATCH', '/api/me/profile', input)
}

/** アイコンを変える（画像 1 枚）。 */
export function updateAvatar(file: File): Promise<Profile> {
  const form = new FormData()
  form.append('file', file)
  return request('PUT', '/api/me/avatar', form)
}

/** ユーザー名・表示名で探す（1〜50 文字）。 */
export function searchUsers(q: string, cursor: string | null): Promise<CursorPage<UserSummary>> {
  return request('GET', withCursor(`/api/users/search?q=${encodeURIComponent(q)}`, cursor))
}
