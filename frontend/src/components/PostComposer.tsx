import ImageOutlined from '@mui/icons-material/ImageOutlined'
import { Box, IconButton, Typography } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type ChangeEvent } from 'react'
import { ApiError } from '../api/client'
import { createPost } from '../api/posts'
import { IMAGE_MAX_COUNT, IMAGE_TYPES, checkImage, prepareImage } from '../imageAttach'
import { MESSAGES } from '../messages'
import { prependPost } from '../queryCache'
import { AttachedImages, type Attachment } from './AttachedImages'
import { PostFormDialog } from './PostFormDialog'
import { useNotify } from './SnackbarProvider'

type Props = { open: boolean; onClose: () => void }

let nextAttachmentId = 1

/** 投稿作成モーダル（画面設計書 5.4 MD-01、F-PO-01）。 */
export function PostComposer({ open, onClose }: Props) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [body, setBody] = useState('')
  const [error, setError] = useState<string>()
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [imageError, setImageError] = useState<string>()
  // 縮小・再エンコードの途中の枚数（終わるまで投稿できない）
  const [preparing, setPreparing] = useState(0)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const close = () => {
    for (const a of attachments) URL.revokeObjectURL(a.previewUrl)
    setBody('')
    setError(undefined)
    setAttachments([])
    setImageError(undefined)
    onClose()
  }

  /** 選んだ画像を確かめ、縮小して添付に足す（誤りは選んだ時点で出す。E-10〜E-12）。 */
  const addImages = async (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = '' // 同じファイルをもう一度選べるように
    let message: string | undefined
    const room = IMAGE_MAX_COUNT - attachments.length - preparing
    const accepted: File[] = []
    for (const file of files) {
      const invalid = checkImage(file)
      if (invalid) message = invalid
      else if (accepted.length >= room) message = MESSAGES.imageTooMany
      else accepted.push(file)
    }
    setImageError(message)
    if (accepted.length === 0) return

    setPreparing((n) => n + accepted.length)
    // 選んだ順を保つため、すべて終わってからまとめて足す
    const results = await Promise.allSettled(accepted.map(prepareImage))
    setPreparing((n) => n - accepted.length)
    const added: Attachment[] = []
    for (const result of results) {
      if (result.status === 'fulfilled') {
        const file = result.value
        added.push({ id: nextAttachmentId++, file, previewUrl: URL.createObjectURL(file) })
      } else {
        // 読めない画像（形式を偽ったファイルなど）や、縮めても 5MB を超えるとき
        setImageError(
          result.reason instanceof Error && result.reason.message === MESSAGES.imageTooLarge
            ? MESSAGES.imageTooLarge
            : MESSAGES.imageType,
        )
      }
    }
    setAttachments((current) => [...current, ...added])
  }

  const removeImage = (id: number) => {
    setAttachments((current) => {
      const target = current.find((a) => a.id === id)
      if (target) URL.revokeObjectURL(target.previewUrl)
      return current.filter((a) => a.id !== id)
    })
    setImageError(undefined)
  }

  const mutation = useMutation({
    mutationFn: () =>
      createPost(
        body,
        attachments.map((a) => a.file),
      ),
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

  const full = attachments.length + preparing >= IMAGE_MAX_COUNT

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
      // 本文も画像もなければ押せない（BR-11）。空白だけの本文も空とみなす（サーバーと同じ）
      canSubmit={(body.trim() !== '' || attachments.length > 0) && preparing === 0}
      submitting={mutation.isPending}
      onSubmit={() => mutation.mutate()}
      dirty={body !== '' || attachments.length > 0}
      onClose={close}
      error={error}
      tools={
        <>
          <IconButton
            color="primary"
            aria-label="画像を添付"
            disabled={full || mutation.isPending}
            onClick={() => fileInputRef.current?.click()}
          >
            <ImageOutlined />
          </IconButton>
          <Box
            component="input"
            ref={fileInputRef}
            type="file"
            accept={IMAGE_TYPES.join(',')}
            multiple
            hidden
            data-testid="image-input"
            onChange={addImages}
          />
        </>
      }
    >
      <AttachedImages attachments={attachments} onRemove={removeImage} />
      {imageError && (
        <Typography role="alert" variant="body2" color="error" sx={{ mt: 1 }}>
          {imageError}
        </Typography>
      )}
    </PostFormDialog>
  )
}
