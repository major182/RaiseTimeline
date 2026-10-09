// 投稿の API（API 設計書 4.4）
import { request } from './client'
import type { Post } from './types'

/** 投稿する。画像も送れるよう、multipart/form-data で送る（画像の添付は次の Issue で足す）。 */
export function createPost(body: string): Promise<Post> {
  const form = new FormData()
  form.append('body', body)
  return request('POST', '/api/posts', form)
}

/** 本文を変える（BR-15。画像は変えられない）。 */
export function updatePostBody(postId: number, body: string): Promise<Post> {
  return request('PATCH', `/api/posts/${postId}`, { body })
}

export function deletePost(postId: number): Promise<void> {
  return request('DELETE', `/api/posts/${postId}`)
}
