import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { updatePostBody } from '../api/posts'
import type { Post } from '../api/types'
import { updatePost } from '../queryCache'
import { PostFormDialog } from './PostFormDialog'
import { PostImages } from './PostImages'
import { useNotify } from './SnackbarProvider'

type Props = { post: Post; open: boolean; onClose: () => void }

/** 投稿編集モーダル（画面設計書 5.5 MD-02、F-PO-06）。変えられるのは本文だけ（BR-15）。 */
export function PostEditor({ post, open, onClose }: Props) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [body, setBody] = useState(post.body)
  const [error, setError] = useState<string>()
  const changed = body !== post.body
  const hasImages = post.images.length > 0

  const close = () => {
    setBody(post.body)
    setError(undefined)
    onClose()
  }

  const mutation = useMutation({
    mutationFn: () => updatePostBody(post.id, body),
    onSuccess: (updated) => {
      // カードの本文と「編集済み」を、サーバーが返した投稿で書き換える
      updatePost(queryClient, post.id, () => updated)
      notify('投稿を編集しました') // I-03
      onClose()
    },
    onError: (e) => {
      const field = e instanceof ApiError ? e.fieldMessage('body') : undefined
      if (field) setError(field)
      else notify(e.message, 'error')
    },
  })

  return (
    <PostFormDialog
      open={open}
      title="投稿を編集"
      body={body}
      onBodyChange={(value) => {
        setBody(value)
        setError(undefined)
      }}
      submitLabel="保存"
      // 変えていなければ押せない。画像のない投稿は本文を空にできない（BR-11）
      canSubmit={changed && (hasImages || body.trim() !== '')}
      submitting={mutation.isPending}
      onSubmit={() => mutation.mutate()}
      dirty={changed}
      onClose={close}
      error={error}
    >
      {/* 画像は変えられないので、見せるだけ（「×」を出さない） */}
      <PostImages images={post.images} />
    </PostFormDialog>
  )
}
