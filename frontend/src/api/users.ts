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
