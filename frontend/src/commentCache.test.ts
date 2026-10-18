import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import type { Comment } from './api/types'
import { appendComment, type CommentPages, updateComment } from './commentCache'
import { makeUser } from './test/fixtures'

const KEY = ['comments', 'replies', 1]

const comment = (id: number): Comment => ({
  id,
  postId: 100,
  parentId: 1,
  author: makeUser(),
  body: `返信${id}`,
  createdAt: '2026-10-18T00:00:00Z',
  replyCount: 0,
  deletable: false,
})

function cache(nextCursor: string | null) {
  const queryClient = new QueryClient()
  queryClient.setQueryData<CommentPages>(KEY, {
    pages: [{ items: [comment(11)], nextCursor }],
    pageParams: [null],
  })
  return queryClient
}

const ids = (queryClient: QueryClient) =>
  queryClient
    .getQueryData<CommentPages>(KEY)
    ?.pages.flatMap((p) => p.items)
    .map((c) => c.id)

describe('コメント・返信の一覧のキャッシュ', () => {
  it('UT-CM-05 最後まで読み終えていれば、最後に足す', () => {
    const queryClient = cache(null)
    appendComment(queryClient, KEY, comment(12))
    expect(ids(queryClient)).toEqual([11, 12])
  })

  it('UT-CM-05 続きがあるか、まだ読んでいなければ足さない（続きを読んだときに届く）', () => {
    const queryClient = cache('next')
    appendComment(queryClient, KEY, comment(12))
    expect(ids(queryClient)).toEqual([11])

    const empty = new QueryClient()
    appendComment(empty, KEY, comment(12))
    expect(empty.getQueryData(KEY)).toBeUndefined()
  })

  it('UT-CM-05 書き換えた値に置き換え、null なら一覧から消す。ほかのコメントと、読んでいない一覧はそのまま', () => {
    const queryClient = cache(null)
    appendComment(queryClient, KEY, comment(12))
    updateComment(queryClient, KEY, 11, (c) => ({ ...c, body: '書き換えた' }))
    expect(queryClient.getQueryData<CommentPages>(KEY)?.pages[0].items[0].body).toBe('書き換えた')
    updateComment(queryClient, KEY, 12, () => null)
    expect(ids(queryClient)).toEqual([11])

    const empty = new QueryClient()
    updateComment(empty, KEY, 11, () => null)
    expect(empty.getQueryData(KEY)).toBeUndefined()
  })
})
