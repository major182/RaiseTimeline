import { type QueryClient, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'

/** 壊れた画像で取り直しを繰り返さないための間隔 */
export const IMAGE_REFRESH_INTERVAL_MS = 60_000

const lastRefreshedAt = new WeakMap<QueryClient, number>()

/**
 * 画像の読み込みに失敗したときに呼ぶ処理を返す。
 * 画像の URL には期限があり、画面を開いたままだと切れるので、表示中のデータを取り直して新しい URL にする。
 */
export function useImageRefresh(): () => void {
  const queryClient = useQueryClient()
  return useCallback(() => {
    const last = lastRefreshedAt.get(queryClient)
    const now = Date.now()
    if (last !== undefined && now - last < IMAGE_REFRESH_INTERVAL_MS) return
    lastRefreshedAt.set(queryClient, now)
    void queryClient.invalidateQueries()
  }, [queryClient])
}
