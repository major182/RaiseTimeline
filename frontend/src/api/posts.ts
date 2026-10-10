// 投稿・コメント・いいねした人の API（API 設計書 4.4・4.5・4.6）
import { request } from './client'
import { withCursor } from './cursor'
import type { Comment, CursorPage, Post, UserSummary } from './types'

/** 投稿する。画像も送るので multipart/form-data で送る。画像は送った順が並び順になる。 */
export function createPost(body: string, images: File[] = []): Promise<Post> {
  const form = new FormData()
  form.append('body', body)
  for (const image of images) form.append('images', image)
  return request('POST', '/api/posts', form)
}

export function getPost(postId: number): Promise<Post> {
  return request('GET', `/api/posts/${postId}`)
}

/** 本文を変える（BR-15。画像は変えられない）。 */
export function updatePostBody(postId: number, body: string): Promise<Post> {
  return request('PATCH', `/api/posts/${postId}`, { body })
}

export function deletePost(postId: number): Promise<void> {
  return request('DELETE', `/api/posts/${postId}`)
}

/** コメントの一覧（古い順）。 */
export function getComments(postId: number, cursor: string | null): Promise<CursorPage<Comment>> {
  return request('GET', withCursor(`/api/posts/${postId}/comments`, cursor))
}

export function createComment(postId: number, body: string): Promise<Comment> {
  return request('POST', `/api/posts/${postId}/comments`, { body })
}

export function deleteComment(commentId: number): Promise<void> {
  return request('DELETE', `/api/comments/${commentId}`)
}

/** いいねした人の一覧（いいねした時刻の新しい順）。 */
export function getLikes(postId: number, cursor: string | null): Promise<CursorPage<UserSummary>> {
  return request('GET', withCursor(`/api/posts/${postId}/likes`, cursor))
}
