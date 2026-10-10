// タイムライン・いいね・おすすめ・フォローの API（API 設計書 4.2・4.3・4.6・4.7）
import { request } from './client'
import { withCursor } from './cursor'
import type { CursorPage, FollowingTimeline, Post, UserSummary } from './types'

export function getFollowingTimeline(cursor: string | null): Promise<FollowingTimeline> {
  return request('GET', withCursor('/api/timeline/following', cursor))
}

export function getAllTimeline(cursor: string | null): Promise<CursorPage<Post>> {
  return request('GET', withCursor('/api/timeline/all', cursor))
}

/** いいね・取り消しの応答。操作のあとの状態。 */
export type LikeState = { liked: boolean; likeCount: number }

export function likePost(postId: number): Promise<LikeState> {
  return request('PUT', `/api/posts/${postId}/like`)
}

export function unlikePost(postId: number): Promise<LikeState> {
  return request('DELETE', `/api/posts/${postId}/like`)
}

export function getRecommendations(): Promise<{ items: UserSummary[] }> {
  return request('GET', '/api/users/recommendations')
}

export function follow(userId: number): Promise<void> {
  return request('PUT', `/api/users/${userId}/follow`)
}

export function unfollow(userId: number): Promise<void> {
  return request('DELETE', `/api/users/${userId}/follow`)
}
