import type { InfiniteData } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import type { FollowingTimeline } from './api/types'
import { createQueryClient } from './auth/session'
import { QUERY_KEYS, updatePost, updateUser } from './queryCache'
import { makePost, makeUser } from './test/fixtures'

function timeline(pages: FollowingTimeline[]): InfiniteData<FollowingTimeline> {
  return { pages, pageParams: pages.map((_, i) => (i === 0 ? null : `c${i}`)) }
}

describe('キャッシュの書き換え（押した操作をすぐ画面に反映する）', () => {
  it('どのタブのどのページにある投稿も、ハイライトの中も書き換える', () => {
    const queryClient = createQueryClient()
    queryClient.setQueryData(
      QUERY_KEYS.followingTimeline,
      timeline([
        { highlights: [makePost({ id: 1 })], items: [makePost({ id: 2 })], nextCursor: 'c1' },
        { highlights: [], items: [makePost({ id: 1 }), makePost({ id: 3 })], nextCursor: null },
      ]),
    )
    queryClient.setQueryData(
      QUERY_KEYS.allTimeline,
      timeline([{ highlights: [], items: [makePost({ id: 1 })], nextCursor: null }]),
    )

    updatePost(queryClient, 1, (p) => ({ ...p, likeCount: 9 }))

    const following = queryClient.getQueryData<InfiniteData<FollowingTimeline>>(
      QUERY_KEYS.followingTimeline,
    )!
    expect(following.pages[0].highlights[0].likeCount).toBe(9)
    expect(following.pages[1].items[0].likeCount).toBe(9)
    expect(following.pages[0].items[0].likeCount).toBe(0) // ほかの投稿は変えない
    expect(following.pageParams).toEqual([null, 'c1']) // 一覧の続きの情報はそのまま
    const all = queryClient.getQueryData<InfiniteData<FollowingTimeline>>(QUERY_KEYS.allTimeline)!
    expect(all.pages[0].items[0].likeCount).toBe(9)
  })

  it('利用者を書き換えると、おすすめの一覧と投稿の投稿者の両方に反映する', () => {
    const queryClient = createQueryClient()
    queryClient.setQueryData(QUERY_KEYS.recommendations, {
      items: [makeUser({ id: 5 }), makeUser({ id: 6 })],
    })
    queryClient.setQueryData(
      QUERY_KEYS.allTimeline,
      timeline([
        {
          highlights: [],
          items: [makePost({ id: 1, author: makeUser({ id: 5 }) }), makePost({ id: 2 })],
          nextCursor: null,
        },
      ]),
    )

    updateUser(queryClient, 5, (u) => ({ ...u, followedByMe: true }))

    const recommended = queryClient.getQueryData<{
      items: { id: number; followedByMe: boolean }[]
    }>(QUERY_KEYS.recommendations)!
    expect(recommended.items.map((u) => u.followedByMe)).toEqual([true, false])
    const all = queryClient.getQueryData<InfiniteData<FollowingTimeline>>(QUERY_KEYS.allTimeline)!
    expect(all.pages[0].items[0].author.followedByMe).toBe(true)
    expect(all.pages[0].items[1].author.followedByMe).toBe(false)
  })

  it('キャッシュがなければ何もしない', () => {
    const queryClient = createQueryClient()
    updatePost(queryClient, 1, (p) => ({ ...p, likeCount: 9 }))
    expect(queryClient.getQueryData(QUERY_KEYS.allTimeline)).toBeUndefined()
  })
})
