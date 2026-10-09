import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'

/** ログインの期限が切れたときに呼ぶ処理。SessionExpiryHandler が登録する。 */
let onUnauthenticated: () => void = () => {}

export function setUnauthenticatedHandler(handler: () => void) {
  onUnauthenticated = handler
}

function handleError(error: unknown) {
  // ログインの失敗（LOGIN_FAILED など）も 401 なので、code で「ログインしていない」だけを見分ける
  if (error instanceof ApiError && error.code === 'UNAUTHENTICATED') onUnauthenticated()
}

/**
 * サーバーのデータの取得・キャッシュ（TanStack Query）の設定。
 * どの通信でも、ログインの期限切れ（401 UNAUTHENTICATED）が返ったら onUnauthenticated を呼ぶ（画面設計書 1.7）。
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({ onError: handleError }),
    mutationCache: new MutationCache({ onError: handleError }),
    defaultOptions: { queries: { retry: false } },
  })
}
