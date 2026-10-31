import { Avatar } from '@mui/material'
import { useEffect } from 'react'
import { useImageRefresh } from './useImageRefresh'

type Props = {
  user: { displayName: string; avatarUrl: string | null }
  size?: number
}

/** 利用者のアイコン。未設定なら表示名の最初の 1 文字を出す（画面設計書 4.1）。 */
export function UserAvatar({ user, size = 40 }: Props) {
  const refreshImages = useImageRefresh()
  const { avatarUrl } = user

  // Avatar は読み込みの失敗を外へ知らせず文字に切り替えるので、URL の期限切れはここで確かめる
  useEffect(() => {
    if (!avatarUrl) return
    const image = new Image()
    image.onerror = refreshImages
    image.src = avatarUrl
    return () => {
      image.onerror = null
    }
  }, [avatarUrl, refreshImages])

  return (
    <Avatar
      src={user.avatarUrl ?? undefined}
      alt={user.displayName}
      sx={{ width: size, height: size }}
    >
      {/* 絵文字などを途中で切らないよう、文字（コードポイント）単位で 1 文字目を取る */}
      {Array.from(user.displayName)[0]}
    </Avatar>
  )
}
