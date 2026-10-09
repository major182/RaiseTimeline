import { Box, CircularProgress } from '@mui/material'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useMe } from './useMe'

/** ログイン画面へ移動するときに渡す値。ログインの後に、元の画面へ戻すために使う。 */
export type LoginRedirectState = { from?: string }

function Loading() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress aria-label="読み込み中" />
    </Box>
  )
}

/** ログインが必要な画面。ログインしていなければ、ログイン画面へ移動する（画面設計書 1.7）。 */
export function RequireAuth() {
  const { data: me, isPending } = useMe()
  const location = useLocation()
  if (isPending) return <Loading />
  if (!me) {
    const state: LoginRedirectState = { from: location.pathname + location.search }
    return <Navigate to="/login" replace state={state} />
  }
  return <Outlet />
}

/**
 * ログインの後に戻る先。アプリの中の URL（/ で始まり、// で始まらない）だけを許し、それ以外はホームにする。
 * 他のサイトへ移動させられるのを防ぐ（技術選定書 4.7 S-06）。
 */
// eslint-disable-next-line react-refresh/only-export-components -- 画面の出し分けと一緒に使う小さな関数のため、同じファイルに置く
export function safeRedirect(from: string | undefined): string {
  // 「/\」で始まる URL も、ブラウザによっては「//」と同じく他のサイトとみなされるので断る
  if (!from || !from.startsWith('/') || from.startsWith('//') || from.startsWith('/\\')) return '/'
  return from
}

/**
 * ログインしていない人向けの画面（ログイン・利用者登録）。
 * ログイン済みなら、ログイン画面へ来る前の画面（なければホーム）へ移動する。
 * ログインに成功したときの移動もここで行う（ログイン画面は、ログインしている利用者を書き換えるだけ）。
 */
export function PublicOnly() {
  const { data: me, isPending } = useMe()
  const location = useLocation()
  if (isPending) return <Loading />
  if (me) {
    const from = (location.state as LoginRedirectState | null)?.from
    return <Navigate to={safeRedirect(from)} replace />
  }
  return <Outlet />
}
