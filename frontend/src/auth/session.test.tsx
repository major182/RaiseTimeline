import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { TEST_ME, renderWithProviders } from '../test/render'
import { SessionExpiryHandler } from './SessionExpiryHandler'
import { createQueryClient, setUnauthenticatedHandler } from './session'
import { LOGGED_OUT_QUERY_KEY, ME_QUERY_KEY } from './useMe'

const EXPIRED = 'ログインの有効期限が切れました。もう一度ログインしてください'

/** 失敗する取得を 1 回動かす（キャッシュの onError を呼ばせる）。 */
async function fail(queryClient: ReturnType<typeof createQueryClient>, error: Error) {
  await queryClient
    .fetchQuery({
      queryKey: ['test', Math.random()],
      queryFn: () => Promise.reject(error),
    })
    .catch(() => {})
}

describe('ログインの期限切れの扱い（画面設計書 1.7）', () => {
  it('UT-AU-07 どの通信でも、UNAUTHENTICATED が返ったら期限切れの処理を呼ぶ', async () => {
    const handler = vi.fn()
    setUnauthenticatedHandler(handler)
    const queryClient = createQueryClient()

    await fail(queryClient, new ApiError(401, 'UNAUTHENTICATED', '期限切れ'))

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('UT-AU-07 同じ 401 でも、ログインの失敗（LOGIN_FAILED）やほかの誤りでは呼ばない', async () => {
    const handler = vi.fn()
    setUnauthenticatedHandler(handler)
    const queryClient = createQueryClient()

    await fail(queryClient, new ApiError(401, 'LOGIN_FAILED', '違います'))
    await fail(queryClient, new ApiError(403, 'FORBIDDEN', '権限がありません'))
    await fail(queryClient, new Error('ふつうの誤り'))

    expect(handler).not.toHaveBeenCalled()
  })

  it('UT-AU-07 期限が切れたら、メッセージを出して「ログインしていない」状態にする', async () => {
    const queryClient = createQueryClient()
    queryClient.setQueryData(ME_QUERY_KEY, TEST_ME)
    renderWithProviders(<SessionExpiryHandler />, queryClient)

    await fail(queryClient, new ApiError(401, 'UNAUTHENTICATED', '期限切れ'))

    expect(await screen.findByText(EXPIRED)).toBeInTheDocument()
    expect(queryClient.getQueryData(ME_QUERY_KEY)).toBeNull()
  })

  it('UT-AU-07 前にログアウトした印が残っていても消す（ログインし直したら今の画面へ戻すため）', async () => {
    const queryClient = createQueryClient()
    queryClient.setQueryData(LOGGED_OUT_QUERY_KEY, true)
    queryClient.setQueryData(ME_QUERY_KEY, TEST_ME)
    renderWithProviders(<SessionExpiryHandler />, queryClient)

    await fail(queryClient, new ApiError(401, 'UNAUTHENTICATED', '期限切れ'))

    await waitFor(() => expect(queryClient.getQueryData(ME_QUERY_KEY)).toBeNull())
    expect(queryClient.getQueryData(LOGGED_OUT_QUERY_KEY)).toBeUndefined()
  })

  it('UT-AU-07 すでにログインしていない状態なら、メッセージを重ねて出さない', async () => {
    const queryClient = createQueryClient()
    queryClient.setQueryData(ME_QUERY_KEY, null)
    renderWithProviders(<SessionExpiryHandler />, queryClient)

    await fail(queryClient, new ApiError(401, 'UNAUTHENTICATED', '期限切れ'))

    await waitFor(() => expect(screen.queryByText(EXPIRED)).not.toBeInTheDocument())
  })

  it('UT-AU-07 画面から外れたら、期限切れの処理を外す', async () => {
    const queryClient = createQueryClient()
    queryClient.setQueryData(ME_QUERY_KEY, TEST_ME)
    const { unmount } = renderWithProviders(<SessionExpiryHandler />, queryClient)
    unmount()

    await fail(queryClient, new ApiError(401, 'UNAUTHENTICATED', '期限切れ'))

    expect(queryClient.getQueryData(ME_QUERY_KEY)).toEqual(TEST_ME)
  })
})
