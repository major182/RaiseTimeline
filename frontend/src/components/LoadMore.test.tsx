import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { installIntersectionObserver } from '../test/intersection'
import { LoadMore } from './LoadMore'

describe('続きの自動読み込み（画面設計書 1.6）', () => {
  it('一覧の最後が見えたら続きを読み込む', () => {
    const io = installIntersectionObserver()
    const onLoadMore = vi.fn()
    render(<LoadMore hasNext loading={false} onLoadMore={onLoadMore} />)
    expect(onLoadMore).not.toHaveBeenCalled()
    io.showAll()
    expect(onLoadMore).toHaveBeenCalledTimes(1)
  })

  it('読み込み中は見張らず、くるくるを出す', () => {
    const io = installIntersectionObserver()
    render(<LoadMore hasNext loading onLoadMore={vi.fn()} />)
    expect(io.watching).toBe(0)
    expect(screen.getByLabelText('続きを読み込み中')).toBeInTheDocument()
  })

  it('全部読み終えたら、見張らずに終わりの文言を出す', () => {
    const io = installIntersectionObserver()
    render(<LoadMore hasNext={false} loading={false} onLoadMore={vi.fn()} />)
    expect(io.watching).toBe(0)
    expect(screen.getByText('これ以上の投稿はありません')).toBeInTheDocument()
  })

  it('見張る仕組みがないブラウザでは、ボタンで続きを読む', async () => {
    vi.stubGlobal('IntersectionObserver', undefined)
    const onLoadMore = vi.fn()
    render(<LoadMore hasNext loading={false} onLoadMore={onLoadMore} />)
    await userEvent.setup().click(screen.getByRole('button', { name: 'さらに読み込む' }))
    expect(onLoadMore).toHaveBeenCalledTimes(1)
  })
})
