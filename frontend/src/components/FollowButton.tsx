import { Button } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { follow, unfollow } from '../api/timeline'
import type { UserSummary } from '../api/types'
import { QUERY_KEYS, updateUser } from '../queryCache'
import { useNotify } from './SnackbarProvider'

/**
 * フォローの状態を変える。プロフィール（followerCount を持つ）なら、フォロワー数もすぐ増減する（画面設計書 5.8）。
 * 同じ状態のままなら何もしない（二重に数えないため）。
 */
function withFollow(user: UserSummary, follow: boolean): UserSummary {
  if (user.followedByMe === follow) return user
  if ('followerCount' in user && typeof user.followerCount === 'number') {
    const followerCount = user.followerCount + (follow ? 1 : -1)
    return { ...user, followedByMe: follow, followerCount } as UserSummary
  }
  return { ...user, followedByMe: follow }
}

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
    onMutate: (doFollow) => updateUser(queryClient, user.id, (u) => withFollow(u, doFollow)),
    onSuccess: () => {
      // 自分のフォロー数・相手の一覧が変わるので、ほかのプロフィールは次に開いたときに読み込み直す
      void queryClient.invalidateQueries({ queryKey: QUERY_KEYS.profiles, refetchType: 'none' })
      // フォロー中タブの中身が変わるので、次に開いたときに読み込み直す
      void queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.followingTimeline,
        refetchType: 'none',
      })
    },
    onError: (error, doFollow) => {
      updateUser(queryClient, user.id, (u) => withFollow(u, !doFollow))
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
