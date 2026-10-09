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

/** ログインしていない人向けの画面（ログイン・利用者登録）。ログイン済みならホームへ移動する。 */
export function PublicOnly() {
  const { data: me, isPending } = useMe()
  if (isPending) return <Loading />
  if (me) return <Navigate to="/" replace />
  return <Outlet />
}
