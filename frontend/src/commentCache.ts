// コメント・返信の一覧のキャッシュの書き換え（投稿の詳細で、操作をすぐ画面に反映するため）
import type { InfiniteData, QueryClient, QueryKey } from '@tanstack/react-query'
import type { Comment, CursorPage } from './api/types'

export type CommentPages = InfiniteData<CursorPage<Comment>, string | null>

/**
 * 一覧の最後に足す。最後まで読み終えているときだけ足す（途中なら、続きを読んだときにサーバーから届く）。
 * 一覧をまだ読んでいなければ何もしない。
 */
export function appendComment(queryClient: QueryClient, key: QueryKey, comment: Comment) {
  const data = queryClient.getQueryData<CommentPages>(key)
  const last = data?.pages.at(-1)
  if (!data || !last || last.nextCursor !== null) return
  queryClient.setQueryData<CommentPages>(key, {
    ...data,
    pages: [...data.pages.slice(0, -1), { ...last, items: [...last.items, comment] }],
  })
}

/** 一覧のコメントを書き換える。update が null を返したら一覧から消す。 */
export function updateComment(
  queryClient: QueryClient,
  key: QueryKey,
  commentId: number,
  update: (comment: Comment) => Comment | null,
) {
  queryClient.setQueryData<CommentPages>(key, (data) =>
    data
      ? {
          ...data,
          pages: data.pages.map((p) => ({
            ...p,
            items: p.items.flatMap((c) => {
              if (c.id !== commentId) return [c]
              const next = update(c)
              return next ? [next] : []
            }),
          })),
        }
      : data,
  )
}
