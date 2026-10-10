import { Alert, Box, Skeleton } from '@mui/material'
import { type QueryKey, useInfiniteQuery } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import type { CursorPage, UserSummary } from '../api/types'
import { LoadMore } from './LoadMore'
import { EmptyMessage } from './PageColumn'
import { UserRow } from './UserRow'

type Props = {
  /** キャッシュの名前。フォローの切り替えを反映するため 'users' で始める。 */
  queryKey: QueryKey
  fetchPage: (cursor: string | null) => Promise<CursorPage<UserSummary>>
  /** 0 人のときの文言（N-06〜N-09）。 */
  emptyText: string
  /** 対象（投稿・利用者）が見つからない（404）ときの文言。 */
  notFoundText?: string
}

/**
 * 利用者の行（UR-01）の一覧。20 件ずつ、下まで来たら続きを読む（画面設計書 1.6）。
 * SC-07・SC-08・SC-09 で使う。
 */
export function UserList({ queryKey, fetchPage, emptyText, notFoundText }: Props) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchPage(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })

  if (query.isPending) {
    return (
      <Box sx={{ px: 2, py: 1 }} aria-busy="true" aria-label="読み込み中">
        <Skeleton height={64} />
        <Skeleton height={64} />
        <Skeleton height={64} />
      </Box>
    )
  }
  if (query.isError) {
    if (notFoundText && query.error instanceof ApiError && query.error.status === 404) {
      return <EmptyMessage>{notFoundText}</EmptyMessage>
    }
    return (
      <Alert severity="error" sx={{ m: 2 }}>
        {query.error.message}
      </Alert>
    )
  }
  const users = query.data.pages.flatMap((p) => p.items)
  if (users.length === 0) return <EmptyMessage>{emptyText}</EmptyMessage>
  return (
    <>
      <Box component="section" aria-label="ユーザーの一覧">
        {users.map((user) => (
          <UserRow key={user.id} user={user} />
        ))}
      </Box>
      <LoadMore
        hasNext={query.hasNextPage}
        loading={query.isFetchingNextPage}
        onLoadMore={() => void query.fetchNextPage()}
        endText=""
      />
    </>
  )
}
