import { Alert, Box, Button } from '@mui/material'
import { type QueryKey, useInfiniteQuery } from '@tanstack/react-query'
import type { CursorPage, Post } from '../api/types'
import { LoadMore } from './LoadMore'
import { EmptyMessage } from './PageColumn'
import { PostCard } from './PostCard'
import { PostListSkeleton } from './PostListSkeleton'

type Props = {
  /** キャッシュの名前。いいね・編集・削除を反映するため 'posts' で始める。 */
  queryKey: QueryKey
  fetchPage: (cursor: string | null) => Promise<CursorPage<Post>>
  /** 0 件のときの文言。 */
  emptyText: string
}

/** 投稿カードの一覧。20 件ずつ、下まで来たら続きを読む（画面設計書 1.6）。プロフィール（SC-05）で使う。 */
export function PostList({ queryKey, fetchPage, emptyText }: Props) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchPage(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })

  if (query.isPending) return <PostListSkeleton />
  if (query.isError) {
    return (
      <Alert
        severity="error"
        sx={{ m: 2 }}
        action={
          <Button color="inherit" size="small" onClick={() => void query.refetch()}>
            再読み込み
          </Button>
        }
      >
        {query.error.message}
      </Alert>
    )
  }
  const posts = query.data.pages.flatMap((p) => p.items)
  if (posts.length === 0) return <EmptyMessage>{emptyText}</EmptyMessage>
  return (
    <>
      <Box component="section" aria-label="投稿の一覧">
        {posts.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </Box>
      <LoadMore
        hasNext={query.hasNextPage}
        loading={query.isFetchingNextPage}
        onLoadMore={() => void query.fetchNextPage()}
      />
    </>
  )
}
