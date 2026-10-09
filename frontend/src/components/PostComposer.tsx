import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { createPost } from '../api/posts'
import { prependPost } from '../queryCache'
import { PostFormDialog } from './PostFormDialog'
import { useNotify } from './SnackbarProvider'

type Props = { open: boolean; onClose: () => void }

/** 投稿作成モーダル（画面設計書 5.4 MD-01、F-PO-01）。画像の添付は次の Issue で足す。 */
export function PostComposer({ open, onClose }: Props) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [body, setBody] = useState('')
  const [error, setError] = useState<string>()

  const close = () => {
    setBody('')
    setError(undefined)
    onClose()
  }

  const mutation = useMutation({
    mutationFn: () => createPost(body),
    onSuccess: (post) => {
      // 投稿したら、いまのタイムラインの先頭に自分の投稿を足す（画面設計書 5.3）
      prependPost(queryClient, post)
      notify('投稿しました') // I-02
      close()
    },
    onError: (e) => {
      // 本文の誤り（280 文字を超えたなど）は入力欄の下に、ほかは画面の下に知らせる（画面設計書 1.4）
      const field = e instanceof ApiError ? e.fieldMessage('body') : undefined
      if (field) setError(field)
      else notify(e.message, 'error')
    },
  })

  return (
    <PostFormDialog
      open={open}
      title="投稿を作成"
      body={body}
      onBodyChange={(value) => {
        setBody(value)
        setError(undefined)
      }}
      submitLabel="投稿する"
      // 本文がなければ押せない（BR-11）。空白だけも空とみなす（サーバーと同じ）
      canSubmit={body.trim() !== ''}
      submitting={mutation.isPending}
      onSubmit={() => mutation.mutate()}
      dirty={body !== ''}
      onClose={close}
      error={error}
    />
  )
}
