import { Alert, Box, Button, Stack } from '@mui/material'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { createComment, deleteComment, getComments, getPost } from '../api/posts'
import type { Comment } from '../api/types'
import { useMe } from '../auth/useMe'
import { appendComment, updateComment } from '../commentCache'
import { CommentInput } from '../components/CommentInput'
import { CommentThread, type ReplyTarget } from '../components/CommentThread'
import { LoadMore } from '../components/LoadMore'
import { EmptyMessage, PageColumn } from '../components/PageColumn'
import { PostCard } from '../components/PostCard'
import { PostListSkeleton } from '../components/PostListSkeleton'
import { useNotify } from '../components/SnackbarProvider'
import { UserAvatar } from '../components/UserAvatar'
import { QUERY_KEYS, updatePost } from '../queryCache'

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

  const mutation = useMutation({
    mutationFn: () => createComment(postId, body),
    onSuccess: (comment) => {
      setBody('')
      appendComment(queryClient, QUERY_KEYS.comments(postId), comment)
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
    <Stack
      direction="row"
      spacing={1.5}
      sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
    >
      {me && <UserAvatar user={me} />}
      <CommentInput
        value={body}
        onChange={(value) => {
          setBody(value)
          setError(undefined)
        }}
        formLabel="コメントの入力"
        label="コメント"
        placeholder="コメントを書く"
        submitLabel="コメントする"
        submitting={mutation.isPending}
        onSubmit={() => mutation.mutate()}
        error={error}
      />
    </Stack>
  )
}

/** コメントの一覧（F-CM-02・04〜06）。古い順に 20 件ずつ。返信の入力欄は同時に 1 つだけ開く。 */
function CommentList({ postId }: { postId: number }) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const key = QUERY_KEYS.comments(postId)
  const [replyTarget, setReplyTarget] = useState<ReplyTarget | null>(null)
  const query = useInfiniteQuery({
    queryKey: key,
    queryFn: ({ pageParam }) => getComments(postId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })

  const deletion = useMutation({
    mutationFn: (comment: Comment) => deleteComment(comment.id),
    onSuccess: (_, comment) => {
      // コメントを消すと返信も消える（BR-38）
      updateComment(queryClient, key, comment.id, () => null)
      queryClient.removeQueries({ queryKey: QUERY_KEYS.replies(comment.id) })
      if (replyTarget?.rootId === comment.id) setReplyTarget(null)
      updatePost(queryClient, postId, (p) => ({
        ...p,
        commentCount: Math.max(0, p.commentCount - 1 - comment.replyCount),
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
          <CommentThread
            key={comment.id}
            postId={postId}
            comment={comment}
            replyTarget={replyTarget?.rootId === comment.id ? replyTarget : null}
            onReply={setReplyTarget}
            onCloseReply={() => setReplyTarget(null)}
            onDelete={(c) => deletion.mutate(c)}
          />
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
