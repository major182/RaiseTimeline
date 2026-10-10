import MoreVert from '@mui/icons-material/MoreVert'
import { IconButton, Menu, MenuItem } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { deletePost } from '../api/posts'
import type { Post } from '../api/types'
import { removePost } from '../queryCache'
import { ConfirmDialog } from './ConfirmDialog'
import { PostEditor } from './PostEditor'
import { useNotify } from './SnackbarProvider'

/**
 * 自分の投稿のメニュー（︙。画面設計書 4.1）。「編集」（MD-02）と「削除」（DL-01）。
 * 出すのは自分の投稿のときだけだが、他人の投稿の編集・削除はサーバーも拒否する（NF-SE-04）。
 */
export function PostMenu({ post, onDeleted }: { post: Post; onDeleted?: () => void }) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const deletion = useMutation({
    mutationFn: () => deletePost(post.id),
    onSuccess: () => {
      removePost(queryClient, post.id)
      notify('投稿を削除しました') // I-04
      onDeleted?.()
    },
    onError: (e) => notify(e.message, 'error'),
  })

  if (!post.isMine) return null
  return (
    <>
      <IconButton
        size="small"
        aria-label="投稿のメニュー"
        onClick={(e) => setAnchor(e.currentTarget)}
        sx={{ ml: 'auto', mt: -0.5 }}
      >
        <MoreVert fontSize="small" />
      </IconButton>
      <Menu anchorEl={anchor} open={anchor !== null} onClose={() => setAnchor(null)}>
        <MenuItem
          onClick={() => {
            setAnchor(null)
            setEditing(true)
          }}
        >
          編集
        </MenuItem>
        <MenuItem
          onClick={() => {
            setAnchor(null)
            setDeleting(true)
          }}
          sx={{ color: 'error.main' }}
        >
          削除
        </MenuItem>
      </Menu>
      {/* 開くたびに今の本文から始めるよう、閉じている間は作らない */}
      {editing && <PostEditor post={post} open onClose={() => setEditing(false)} />}
      <ConfirmDialog
        open={deleting}
        title="投稿を削除しますか？"
        body="この操作は取り消せません。コメントといいねも削除されます。"
        confirmLabel="削除"
        danger
        onCancel={() => setDeleting(false)}
        onConfirm={() => {
          setDeleting(false)
          deletion.mutate()
        }}
      />
    </>
  )
}
