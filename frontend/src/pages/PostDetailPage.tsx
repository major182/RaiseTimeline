import { Alert, Box, Button, Stack, TextField } from '@mui/material'
import {
  type InfiniteData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { createComment, deleteComment, getComments, getPost } from '../api/posts'
import type { Comment, CursorPage } from '../api/types'
import { useMe } from '../auth/useMe'
import { CharCounter } from '../components/CharCounter'
import { CommentItem } from '../components/CommentItem'
import { LoadMore } from '../components/LoadMore'
import { EmptyMessage, PageColumn } from '../components/PageColumn'
import { PostCard } from '../components/PostCard'
import { PostListSkeleton } from '../components/PostListSkeleton'
import { useNotify } from '../components/SnackbarProvider'
import { UserAvatar } from '../components/UserAvatar'
import { BODY_MAX_LENGTH, countChars } from '../postLength'
import { QUERY_KEYS, updatePost } from '../queryCache'

type CommentPages = InfiniteData<CursorPage<Comment>, string | null>

/** 投稿の詳細（画面設計書 5.7 SC-04）。投稿・コメントの入力欄・コメントの一覧。 */
export function PostDetailPage() {
  const postId = Number(useParams().postId)
  const valid = Number.isInteger(postId) && postId > 0
  const navigate = useNavigate()
  const query = useQuery({
    queryKey: QUERY_KEYS.post(postId),
    queryFn: () => getPost(postId),
    enabled: valid,
  })

  let content
  if (!valid || (query.error instanceof ApiError && query.error.status === 404)) {
    content = <EmptyMessage>この投稿は削除されたか、見つかりません</EmptyMessage> // N-05
  } else if (query.isPending) {
    content = <PostListSkeleton count={1} />
  } else if (query.isError) {
    content = (
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
  } else {
    content = (
      <>
        {/* 詳細で消したら、もう見るものがないのでホームへ戻る */}
        <PostCard
          post={query.data}
          detail
          onDeleted={() => void navigate('/', { replace: true })}
        />
        <CommentForm postId={postId} />
        <CommentList postId={postId} />
      </>
    )
  }

  return <PageColumn title="投稿">{content}</PageColumn>
}

/** コメントの入力欄（F-CM-01）。1〜280 文字。 */
function CommentForm({ postId }: { postId: number }) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const { data: me } = useMe()
  const [body, setBody] = useState('')
  const [error, setError] = useState<string>()
  const over = countChars(body) > BODY_MAX_LENGTH

  const mutation = useMutation({
    mutationFn: () => createComment(postId, body),
    onSuccess: (comment) => {
      setBody('')
      // 一覧を最後まで読み終えているときだけ、最後に足す（途中なら、続きを読んだときにサーバーから届く）
      const key = QUERY_KEYS.comments(postId)
      const data = queryClient.getQueryData<CommentPages>(key)
      const last = data?.pages.at(-1)
      if (data && last && last.nextCursor === null) {
        queryClient.setQueryData<CommentPages>(key, {
          ...data,
          pages: [...data.pages.slice(0, -1), { ...last, items: [...last.items, comment] }],
        })
      }
      updatePost(queryClient, postId, (p) => ({ ...p, commentCount: p.commentCount + 1 }))
    },
    onError: (e) => {
      // 本文の誤り（E-13 など）は入力欄の下に、ほかは画面の下に知らせる（画面設計書 1.4）
      const field = e instanceof ApiError ? e.fieldMessage('body') : undefined
      if (field) setError(field)
      else notify(e.message, 'error')
    },
  })

  return (
    <Box
      component="form"
      aria-label="コメントの入力"
      onSubmit={(e) => {
        e.preventDefault()
        mutation.mutate()
      }}
      sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
    >
      <Stack direction="row" spacing={1.5}>
        {me && <UserAvatar user={me} />}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <TextField
            value={body}
            onChange={(e) => {
              setBody(e.target.value)
              setError(undefined)
            }}
            placeholder="コメントを書く"
            multiline
            fullWidth
            variant="standard"
            error={Boolean(error)}
            helperText={error}
            slotProps={{ htmlInput: { 'aria-label': 'コメント' } }}
          />
          <Stack
            direction="row"
            spacing={2}
            sx={{ alignItems: 'center', justifyContent: 'flex-end', mt: 1 }}
          >
            {body !== '' && <CharCounter text={body} max={BODY_MAX_LENGTH} />}
            <Button
              type="submit"
              variant="contained"
              // 空か空白だけ・280 文字を超えたら押せない（サーバーと同じ）
              disabled={body.trim() === '' || over || mutation.isPending}
              sx={{ borderRadius: 5 }}
            >
              コメントする
            </Button>
          </Stack>
        </Box>
      </Stack>
    </Box>
  )
}

/** コメントの一覧（F-CM-02・04）。古い順に 20 件ずつ。 */
function CommentList({ postId }: { postId: number }) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const key = QUERY_KEYS.comments(postId)
  const query = useInfiniteQuery({
    queryKey: key,
    queryFn: ({ pageParam }) => getComments(postId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })

  const deletion = useMutation({
    mutationFn: (comment: Comment) => deleteComment(comment.id),
    onSuccess: (_, comment) => {
      queryClient.setQueryData<CommentPages>(key, (data) =>
        data
          ? {
              ...data,
              pages: data.pages.map((p) => ({
                ...p,
                items: p.items.filter((c) => c.id !== comment.id),
              })),
            }
          : data,
      )
      updatePost(queryClient, postId, (p) => ({
        ...p,
        commentCount: Math.max(0, p.commentCount - 1),
      }))
      notify('コメントを削除しました') // I-05
    },
    onError: (e) => notify(e.message, 'error'),
  })

  if (query.isPending) return <PostListSkeleton />
  if (query.isError) {
    return (
      <Alert severity="error" sx={{ m: 2 }}>
        {query.error.message}
      </Alert>
    )
  }
  const comments = query.data.pages.flatMap((p) => p.items)
  return (
    <>
      <Box component="section" aria-label="コメントの一覧">
        {comments.map((comment) => (
          <CommentItem key={comment.id} comment={comment} onDelete={(c) => deletion.mutate(c)} />
        ))}
      </Box>
      {query.hasNextPage && (
        <LoadMore
          hasNext
          loading={query.isFetchingNextPage}
          onLoadMore={() => void query.fetchNextPage()}
        />
      )}
    </>
  )
}
