import { Alert, Box, Button, CircularProgress } from '@mui/material'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { createComment, deleteComment, getReplies } from '../api/posts'
import type { Comment } from '../api/types'
import { appendComment, updateComment } from '../commentCache'
import { QUERY_KEYS, updatePost } from '../queryCache'
import { CommentInput } from './CommentInput'
import { CommentItem } from './CommentItem'
import { ConfirmDialog } from './ConfirmDialog'
import { useNotify } from './SnackbarProvider'

/** 返信の入力欄を開いている先。返信の「返信」なら、返信先の @ユーザー名 を入れて開く。 */
export type ReplyTarget = { rootId: number; replyTo: Comment }

type Props = {
  postId: number
  /** 返信ではないコメント。 */
  comment: Comment
  /** このコメントで返信の入力欄を開いているなら、その返信先。 */
  replyTarget: ReplyTarget | null
  onReply: (target: ReplyTarget) => void
  onCloseReply: () => void
  onDelete: (comment: Comment) => void
}

/** 返信の一覧の字下げ。コメントのアイコン（40px）と間（12px）の幅（画面設計書 5.7）。 */
const REPLY_INDENT = '52px'

/**
 * コメントと、その返信の一覧・返信の入力欄（画面設計書 5.7、F-CM-05・06）。
 * 返信は一段だけ字下げし、最初は閉じておく。「返信を表示（N 件）」で古い順に 20 件ずつ出す。
 */
export function CommentThread({
  postId,
  comment,
  replyTarget,
  onReply,
  onCloseReply,
  onDelete,
}: Props) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [expanded, setExpanded] = useState(false)
  const key = QUERY_KEYS.replies(comment.id)
  const replies = useInfiniteQuery({
    queryKey: key,
    queryFn: ({ pageParam }) => getReplies(comment.id, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: expanded,
  })

  const deletion = useMutation({
    mutationFn: (reply: Comment) => deleteComment(reply.id),
    onSuccess: (_, reply) => {
      updateComment(queryClient, key, reply.id, () => null)
      updateComment(queryClient, QUERY_KEYS.comments(postId), comment.id, (c) => ({
        ...c,
        replyCount: Math.max(0, c.replyCount - 1),
      }))
      updatePost(queryClient, postId, (p) => ({
        ...p,
        commentCount: Math.max(0, p.commentCount - 1),
      }))
      notify('コメントを削除しました') // I-05
    },
    onError: (e) => notify(e.message, 'error'),
  })

  const reply = (target: Comment) => onReply({ rootId: comment.id, replyTo: target })

  return (
    <Box sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}>
      <CommentItem comment={comment} onDelete={onDelete} onReply={reply} />
      <Box sx={{ pl: REPLY_INDENT }}>
        {expanded && (
          <Box component="section" aria-label="返信の一覧">
            {replies.data?.pages
              .flatMap((p) => p.items)
              .map((r) => (
                <CommentItem
                  key={r.id}
                  comment={r}
                  onDelete={(c) => deletion.mutate(c)}
                  onReply={reply}
                />
              ))}
            {replies.isPending && <CircularProgress size={20} aria-label="読み込み中" />}
            {replies.isError && (
              <Alert severity="error" sx={{ my: 1 }}>
                {replies.error.message}
              </Alert>
            )}
            {replies.hasNextPage && (
              <Button
                size="small"
                onClick={() => void replies.fetchNextPage()}
                disabled={replies.isFetchingNextPage}
              >
                さらに返信を表示
              </Button>
            )}
          </Box>
        )}
        {replyTarget && (
          <ReplyForm
            // 返信先を変えたら、入力欄を作り直す（@ユーザー名 を入れ直す）
            key={replyTarget.replyTo.id}
            postId={postId}
            target={replyTarget}
            onClose={onCloseReply}
            onPosted={() => setExpanded(true)}
          />
        )}
        {comment.replyCount > 0 && (
          <Button size="small" onClick={() => setExpanded((v) => !v)} sx={{ mb: 1 }}>
            {expanded ? '返信を隠す' : `返信を表示（${comment.replyCount} 件）`}
          </Button>
        )}
      </Box>
    </Box>
  )
}

type ReplyFormProps = {
  postId: number
  target: ReplyTarget
  onClose: () => void
  /** 返信した（返信の一覧を開く）。 */
  onPosted: () => void
}

/**
 * 返信の入力欄（F-CM-05）。返信の「返信」なら、返信先の @ユーザー名 を入れて開く（BR-37）。
 * 入力があるときの「キャンセル」は C-03 で確かめる。
 */
function ReplyForm({ postId, target, onClose, onPosted }: ReplyFormProps) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const initial = target.replyTo.parentId === null ? '' : `@${target.replyTo.author.username} `
  const [body, setBody] = useState(initial)
  const [error, setError] = useState<string>()
  const [confirming, setConfirming] = useState(false)
  const commentsKey = QUERY_KEYS.comments(postId)

  const mutation = useMutation({
    // 返信先が返信でも、そのまま送る（サーバーが元のコメントへの返信にする）
    mutationFn: () => createComment(postId, body, target.replyTo.id),
    onSuccess: (created) => {
      appendComment(queryClient, QUERY_KEYS.replies(target.rootId), created)
      updateComment(queryClient, commentsKey, target.rootId, (c) => ({
        ...c,
        replyCount: c.replyCount + 1,
      }))
      updatePost(queryClient, postId, (p) => ({ ...p, commentCount: p.commentCount + 1 }))
      onPosted()
      onClose()
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 404) {
        // 返信先が消えていた。そのコメント（と返信）を一覧から消す
        notify('このコメントは削除されたため、返信できません', 'error') // S-05
        let removed = 0
        updateComment(queryClient, commentsKey, target.rootId, (c) => {
          removed = 1 + c.replyCount
          return null
        })
        updatePost(queryClient, postId, (p) => ({
          ...p,
          commentCount: Math.max(0, p.commentCount - removed),
        }))
        onClose()
        return
      }
      const field = e instanceof ApiError ? e.fieldMessage('body') : undefined
      if (field) setError(field)
      else notify(e.message, 'error')
    },
  })

  const cancel = () => {
    if (body.trim() !== '' && body !== initial) setConfirming(true)
    else onClose()
  }

  return (
    <Box sx={{ py: 1 }}>
      <CommentInput
        value={body}
        onChange={(value) => {
          setBody(value)
          setError(undefined)
        }}
        formLabel="返信の入力"
        label="返信"
        placeholder="返信を入力"
        submitLabel="返信する"
        submitting={mutation.isPending}
        onSubmit={() => mutation.mutate()}
        error={error}
        onCancel={cancel}
        autoFocus
      />
      <ConfirmDialog
        open={confirming}
        title="編集内容を破棄しますか？"
        body="入力した内容は保存されません。"
        confirmLabel="破棄"
        danger
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          onClose()
        }}
      />
    </Box>
  )
}
