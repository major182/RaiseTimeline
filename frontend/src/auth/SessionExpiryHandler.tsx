import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useNotify } from '../components/SnackbarProvider'
import { setUnauthenticatedHandler } from './session'
import { ME_QUERY_KEY } from './useMe'

/**
 * ログインの期限が切れたら、メッセージ（S-02）を出し、「ログインしていない」状態にする。
 * そうすると RequireAuth がログイン画面へ移動させる。画面には何も表示しない。
 */
export function SessionExpiryHandler() {
  const queryClient = useQueryClient()
  const notify = useNotify()
  useEffect(() => {
    setUnauthenticatedHandler(() => {
      if (queryClient.getQueryData(ME_QUERY_KEY) === null) return // すでにログインしていない
      notify('ログインの有効期限が切れました。もう一度ログインしてください', 'error')
      queryClient.setQueryData(ME_QUERY_KEY, null)
    })
    return () => setUnauthenticatedHandler(() => {})
  }, [queryClient, notify])
  return null
}
