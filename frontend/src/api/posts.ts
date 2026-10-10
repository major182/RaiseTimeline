// 投稿の API（API 設計書 4.4）
import { request } from './client'
import type { Post } from './types'

/** 投稿する。画像も送るので multipart/form-data で送る。画像は送った順が並び順になる。 */
export function createPost(body: string, images: File[] = []): Promise<Post> {
  const form = new FormData()
  form.append('body', body)
  for (const image of images) form.append('images', image)
  return request('POST', '/api/posts', form)
}

/** 本文を変える（BR-15。画像は変えられない）。 */
export function updatePostBody(postId: number, body: string): Promise<Post> {
  return request('PATCH', `/api/posts/${postId}`, { body })
}

export function deletePost(postId: number): Promise<void> {
  return request('DELETE', `/api/posts/${postId}`)
}
