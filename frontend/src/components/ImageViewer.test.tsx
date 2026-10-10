import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { makePost } from '../test/fixtures'
import { json, renderWithProviders, stubApi } from '../test/render'
import { PostCard } from './PostCard'

const images = [1, 2, 3].map((n) => ({ url: `/media/${n}.png`, width: 800, height: 600 }))

function renderCard() {
  renderWithProviders(<PostCard post={makePost({ images })} />)
  return userEvent.setup()
}

const shown = () => screen.getByRole('dialog').querySelector('img')?.getAttribute('src')

describe('画像の拡大表示（IV-01）', () => {
  it('押した画像を開き、矢印で切り替える。端では矢印を出さない', async () => {
    const user = renderCard()
    await user.click(screen.getByRole('button', { name: '画像 2/3 を拡大' }))
    expect(shown()).toBe('/media/2.png')
    await user.click(screen.getByRole('button', { name: '次の画像' }))
    expect(shown()).toBe('/media/3.png')
    expect(screen.queryByRole('button', { name: '次の画像' })).not.toBeInTheDocument()
    await user.keyboard('{ArrowLeft}{ArrowLeft}')
    expect(shown()).toBe('/media/1.png')
    expect(screen.queryByRole('button', { name: '前の画像' })).not.toBeInTheDocument()
  })

  it('スワイプで切り替える', async () => {
    const user = renderCard()
    await user.click(screen.getByRole('button', { name: '画像 1/3 を拡大' }))
    const backdrop = screen.getByTestId('image-viewer-backdrop')
    fireEvent.touchStart(backdrop, { touches: [{ clientX: 300 }] })
    fireEvent.touchEnd(backdrop, { changedTouches: [{ clientX: 100 }] })
    expect(shown()).toBe('/media/2.png')
  })

  it('×・画像の外・Esc で閉じ、カードを押したことにはしない', async () => {
    const user = renderCard()
    for (const close of [
      () => user.click(screen.getByRole('button', { name: '閉じる' })),
      () => user.click(screen.getByTestId('image-viewer-backdrop')),
      () => user.keyboard('{Escape}'),
    ]) {
      await user.click(screen.getByRole('button', { name: '画像 1/3 を拡大' }))
      await close()
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    }
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})

describe('カードを押して詳細へ（4.1）', () => {
  it('本文を押したら投稿の詳細へ移る', async () => {
    const user = userEvent.setup()
    renderWithProviders(<PostCard post={makePost({ id: 7 })} />)
    await user.click(screen.getByTestId('post-body'))
    expect(screen.getByTestId('location')).toHaveTextContent('/posts/7')
  })

  it('いいねを押しても移らない', async () => {
    stubApi({ 'PUT /api/posts/7/like': json(200, { liked: true, likeCount: 1 }) })
    const user = userEvent.setup()
    renderWithProviders(<PostCard post={makePost({ id: 7 })} />)
    await user.click(screen.getByRole('button', { name: 'いいね（0）' }))
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })
})
