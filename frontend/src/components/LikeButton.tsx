import Favorite from '@mui/icons-material/Favorite'
import FavoriteBorder from '@mui/icons-material/FavoriteBorder'
import { Button } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { likePost, unlikePost } from '../api/timeline'
import type { Post } from '../api/types'
import { formatCount } from '../format'
import { updatePost } from '../queryCache'
import { useNotify } from './SnackbarProvider'

/**
 * いいねのボタン（画面設計書 4.1、F-LK-01・02）。
 * 押したら、サーバーの返事を待たずに表示を切り替える。失敗したら元に戻してメッセージを出す。
 * 成功したら、サーバーが返した正しい数に合わせる（ほかの人のいいねが増えていることがあるため）。
 */
export function LikeButton({ post }: { post: Post }) {
  const queryClient = useQueryClient()
  const notify = useNotify()

  const mutation = useMutation({
    mutationFn: (like: boolean) => (like ? likePost(post.id) : unlikePost(post.id)),
    onMutate: (like) => {
      updatePost(queryClient, post.id, (p) => ({
        ...p,
        likedByMe: like,
        likeCount: Math.max(0, p.likeCount + (like ? 1 : -1)),
      }))
    },
    onSuccess: (state) => {
      updatePost(queryClient, post.id, (p) => ({
        ...p,
        likedByMe: state.liked,
        likeCount: state.likeCount,
      }))
    },
    onError: (error, like) => {
      updatePost(queryClient, post.id, (p) => ({
        ...p,
        likedByMe: !like,
        likeCount: Math.max(0, p.likeCount + (like ? -1 : 1)),
      }))
      notify(error.message, 'error')
    },
  })

  const liked = post.likedByMe
  return (
    <Button
      size="small"
      color={liked ? 'error' : 'inherit'}
      startIcon={liked ? <Favorite /> : <FavoriteBorder />}
      aria-pressed={liked}
      aria-label={liked ? `いいねを取り消す（${post.likeCount}）` : `いいね（${post.likeCount}）`}
      onClick={(e) => {
        e.stopPropagation()
        mutation.mutate(!liked)
      }}
      sx={{ minWidth: 0, color: liked ? undefined : 'text.secondary' }}
    >
      {formatCount(post.likeCount)}
    </Button>
  )
}
