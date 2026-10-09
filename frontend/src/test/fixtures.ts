// テスト用の見本データ（API 設計書 3 章の形）
import type { Post, UserSummary } from '../api/types'

export function makeUser(overrides: Partial<UserSummary> = {}): UserSummary {
  return {
    id: 2,
    username: 'yamada',
    displayName: '山田',
    avatarUrl: null,
    bio: '',
    followedByMe: false,
    isMe: false,
    ...overrides,
  }
}

export function makePost(overrides: Partial<Post> = {}): Post {
  return {
    id: 100,
    author: makeUser(),
    body: '本文です',
    images: [],
    createdAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(), // 3 時間前
    editedAt: null,
    likeCount: 0,
    commentCount: 0,
    likedByMe: false,
    likedVia: null,
    isMine: false,
    ...overrides,
  }
}
