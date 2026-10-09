import { Button } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { follow, unfollow } from '../api/timeline'
import type { UserSummary } from '../api/types'
import { QUERY_KEYS, updateUser } from '../queryCache'
import { useNotify } from './SnackbarProvider'

/**
 * フォローのボタン（画面設計書 4.2、F-FL-01）。自分には出さない。
 * 未フォローなら「フォロー」（塗りつぶし）、フォロー中なら「フォロー中」（枠だけ）。「フォロー中」にマウスを乗せると「解除」。
 * 押したら返事を待たずに表示を切り替え、失敗したら元に戻す。
 */
export function FollowButton({ user }: { user: UserSummary }) {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [hover, setHover] = useState(false)

  const mutation = useMutation({
    mutationFn: (doFollow: boolean) => (doFollow ? follow(user.id) : unfollow(user.id)),
    onMutate: (doFollow) =>
      updateUser(queryClient, user.id, (u) => ({ ...u, followedByMe: doFollow })),
    onSuccess: () => {
      // フォロー中タブの中身が変わるので、次に開いたときに読み込み直す
      void queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.followingTimeline,
        refetchType: 'none',
      })
    },
    onError: (error, doFollow) => {
      updateUser(queryClient, user.id, (u) => ({ ...u, followedByMe: !doFollow }))
      notify(error.message, 'error')
    },
  })

  if (user.isMe) return null
  const following = user.followedByMe
  return (
    <Button
      size="small"
      variant={following ? 'outlined' : 'contained'}
      color={following && hover ? 'error' : 'primary'}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onClick={() => mutation.mutate(!following)}
      aria-label={
        following ? `${user.displayName}さんのフォローを解除` : `${user.displayName}さんをフォロー`
      }
      sx={{ flexShrink: 0, borderRadius: 5, minWidth: 96 }}
    >
      {following ? (hover ? '解除' : 'フォロー中') : 'フォロー'}
    </Button>
  )
}
