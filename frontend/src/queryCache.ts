// TanStack Query のキャッシュの名前と、押した操作をすぐ画面に反映するための書き換え
import type { QueryClient } from '@tanstack/react-query'
import type { Post, UserSummary } from './api/types'

/** キャッシュの名前。投稿を含むものは 'posts'、利用者の一覧は 'users' で始める（まとめて書き換えるため）。 */
export const QUERY_KEYS = {
  timeline: ['posts', 'timeline'] as const,
  followingTimeline: ['posts', 'timeline', 'following'] as const,
  allTimeline: ['posts', 'timeline', 'all'] as const,
  recommendations: ['users', 'recommendations'] as const,
  post: (postId: number) => ['posts', 'detail', postId] as const,
  comments: (postId: number) => ['comments', postId] as const,
  likes: (postId: number) => ['users', 'likes', postId] as const,
  /** プロフィール。ユーザー名は大文字・小文字を区別しないので、小文字にそろえる。 */
  profile: (username: string) => ['users', 'profile', username.toLowerCase()] as const,
  profiles: ['users', 'profile'] as const,
  userPosts: (userId: number) => ['posts', 'user', userId] as const,
  following: (userId: number) => ['users', 'following', userId] as const,
  followers: (userId: number) => ['users', 'followers', userId] as const,
  search: (q: string) => ['users', 'search', q] as const,
}

/** オブジェクトの中の配列 items・highlights と、一覧の pages をたどって、要素を書き換える。 */
function mapDeep<T>(
  data: unknown,
  map: (item: T) => T,
  match: (value: unknown) => value is T,
): unknown {
  if (Array.isArray(data)) return data.map((v) => mapDeep(v, map, match))
  if (match(data)) return map(data)
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>
    let changed = false
    const next: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(record)) {
      // 一覧（pages）と、その中の items・highlights だけをたどる（ほかの項目はそのまま）
      if (key === 'pages' || key === 'items' || key === 'highlights') {
        next[key] = mapDeep(value, map, match)
        changed = true
      } else {
        next[key] = value
      }
    }
    return changed ? next : data
  }
  return data
}

function isPost(value: unknown): value is Post {
  return !!value && typeof value === 'object' && 'author' in value && 'likeCount' in value
}

function isUser(value: unknown): value is UserSummary {
  return !!value && typeof value === 'object' && 'followedByMe' in value && 'username' in value
}

/** 投稿を含むすべてのキャッシュ（タイムラインのどのタブでも）で、指定した投稿を書き換える。 */
export function updatePost(queryClient: QueryClient, postId: number, update: (post: Post) => Post) {
  queryClient.setQueriesData({ queryKey: ['posts'] }, (data: unknown) =>
    data === undefined
      ? data
      : mapDeep<Post>(data, (p) => (p.id === postId ? update(p) : p), isPost),
  )
}

/** 利用者を含むすべてのキャッシュと、投稿の投稿者で、指定した利用者を書き換える。 */
export function updateUser(
  queryClient: QueryClient,
  userId: number,
  update: (user: UserSummary) => UserSummary,
) {
  queryClient.setQueriesData({ queryKey: ['users'] }, (data: unknown) =>
    data === undefined
      ? data
      : mapDeep<UserSummary>(data, (u) => (u.id === userId ? update(u) : u), isUser),
  )
  updatePostsByAuthor(queryClient, userId, update)
}

function updatePostsByAuthor(
  queryClient: QueryClient,
  userId: number,
  update: (user: UserSummary) => UserSummary,
) {
  queryClient.setQueriesData({ queryKey: ['posts'] }, (data: unknown) =>
    data === undefined
      ? data
      : mapDeep<Post>(
          data,
          (p) => (p.author.id === userId ? { ...p, author: update(p.author) } : p),
          isPost,
        ),
  )
}

/**
 * 自分の投稿を、読み込み済みのタイムライン（フォロー中・全体）の先頭に足す（画面設計書 5.3）。
 * まだ読み込んでいないタブは、開いたときにサーバーから取るので何もしない。
 */
export function prependPost(queryClient: QueryClient, post: Post) {
  for (const key of [QUERY_KEYS.followingTimeline, QUERY_KEYS.allTimeline]) {
    queryClient.setQueryData(key, (data: unknown) => {
      const infinite = data as { pages: { items: Post[] }[] } | undefined
      if (!infinite || infinite.pages.length === 0) return data
      const [first, ...rest] = infinite.pages
      return { ...infinite, pages: [{ ...first, items: [post, ...first.items] }, ...rest] }
    })
  }
}

/** 消した投稿を、投稿を含むすべてのキャッシュの一覧（items・highlights）から除く。 */
export function removePost(queryClient: QueryClient, postId: number) {
  queryClient.setQueriesData({ queryKey: ['posts'] }, (data: unknown) =>
    data === undefined ? data : filterDeep(data, postId),
  )
}

function filterDeep(data: unknown, postId: number): unknown {
  if (Array.isArray(data)) {
    return data.filter((v) => !(isPost(v) && v.id === postId)).map((v) => filterDeep(v, postId))
  }
  if (data && typeof data === 'object' && !isPost(data)) {
    const record = data as Record<string, unknown>
    const next: Record<string, unknown> = { ...record }
    for (const key of ['pages', 'items', 'highlights']) {
      if (key in record) next[key] = filterDeep(record[key], postId)
    }
    return next
  }
  return data
}
