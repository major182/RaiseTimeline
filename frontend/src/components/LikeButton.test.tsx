import { useQuery } from '@tanstack/react-query'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Post } from '../api/types'
import { createQueryClient } from '../auth/session'
import { makePost } from '../test/fixtures'
import { json, renderWithProviders, stubApi } from '../test/render'
import { LikeButton } from './LikeButton'

/** キャッシュ（['posts', 'test']）にある投稿を表示する。押したあとの書き換えが画面に出るかを確かめるため。 */
function CachedLikeButton() {
  const { data } = useQuery<Post>({
    queryKey: ['posts', 'test'],
    queryFn: () => new Promise(() => {}),
  })
  return data ? <LikeButton post={data} /> : null
}

function setup(post: Post) {
  const queryClient = createQueryClient()
  queryClient.setQueryData(['posts', 'test'], post)
  renderWithProviders(<CachedLikeButton />, queryClient)
  return userEvent.setup()
}

/** 返事を、テストが決めたときに返す（返事の前の表示を確かめるため）。 */
function deferred() {
  let resolve!: (r: Response) => void
  const promise = new Promise<Response>((r) => (resolve = r))
  return { promise, resolve }
}

describe('いいねのボタン（F-LK-01・02）', () => {
  it('押したら返事を待たずに塗りつぶしにして数を増やし、返事の数に合わせる', async () => {
    const reply = deferred()
    stubApi({ 'PUT /api/posts/100/like': () => reply.promise as unknown as Response })
    const user = setup(makePost({ likeCount: 4 }))

    await user.click(screen.getByRole('button', { name: 'いいね（4）' }))
    // 返事の前に切り替わっている
    const pressed = screen.getByRole('button', { name: 'いいねを取り消す（5）' })
    expect(pressed).toHaveAttribute('aria-pressed', 'true')

    // ほかの人のいいねも増えていて、サーバーは 7 を返した
    reply.resolve(json(200, { liked: true, likeCount: 7 }))
    expect(await screen.findByRole('button', { name: 'いいねを取り消す（7）' })).toBeInTheDocument()
  })

  it('いいね済みなら、押すと取り消す', async () => {
    const fetchMock = stubApi({
      'DELETE /api/posts/100/like': json(200, { liked: false, likeCount: 2 }),
    })
    const user = setup(makePost({ likedByMe: true, likeCount: 3 }))

    await user.click(screen.getByRole('button', { name: 'いいねを取り消す（3）' }))
    expect(await screen.findByRole('button', { name: 'いいね（2）' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/posts/100/like',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('失敗したら元に戻し、メッセージを出す', async () => {
    stubApi({
      'PUT /api/posts/100/like': json(404, {
        status: 404,
        code: 'NOT_FOUND',
        detail: '見つかりません',
      }),
    })
    const user = setup(makePost({ likeCount: 4 }))

    await user.click(screen.getByRole('button', { name: 'いいね（4）' }))
    expect(await screen.findByText('見つかりません')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'いいね（4）' })).toHaveAttribute(
        'aria-pressed',
        'false',
      ),
    )
  })
})
