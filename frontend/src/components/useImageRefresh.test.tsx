import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PostImages } from './PostImages'
import { IMAGE_REFRESH_INTERVAL_MS, useImageRefresh } from './useImageRefresh'
import { UserAvatar } from './UserAvatar'

function setup() {
  const client = new QueryClient()
  const invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { invalidate, wrapper }
}

describe('useImageRefresh', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('最初の失敗で取り直し、間隔の内は取り直さず、間隔ちょうどで取り直す', () => {
    const { invalidate, wrapper } = setup()
    const { result } = renderHook(() => useImageRefresh(), { wrapper })

    result.current()
    expect(invalidate).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(IMAGE_REFRESH_INTERVAL_MS - 1)
    result.current()
    expect(invalidate).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(1)
    result.current()
    expect(invalidate).toHaveBeenCalledTimes(2)
  })
})

describe('画像の読み込みの失敗', () => {
  it('投稿の画像が読み込めなければ取り直す', () => {
    const { invalidate, wrapper } = setup()
    render(<PostImages images={[{ url: '/a.jpg', width: 100, height: 100 }]} />, { wrapper })

    fireEvent.error(screen.getByRole('img', { name: '画像 1/1' }))

    expect(invalidate).toHaveBeenCalledTimes(1)
  })

  describe('アイコン', () => {
    const created: { onerror: (() => void) | null; src: string }[] = []

    beforeEach(() => {
      created.length = 0
      vi.stubGlobal(
        'Image',
        class {
          onerror: (() => void) | null = null
          onload: (() => void) | null = null
          src = ''
          constructor() {
            created.push(this)
          }
        },
      )
    })
    afterEach(() => {
      vi.unstubAllGlobals()
    })

    it('読み込めなければ取り直す', () => {
      const { invalidate, wrapper } = setup()
      render(<UserAvatar user={{ displayName: 'あ', avatarUrl: '/a.jpg' }} />, { wrapper })

      // Avatar も内部で同じ URL の Image を作る
      act(() => created.filter((image) => image.src === '/a.jpg').forEach((i) => i.onerror?.()))

      expect(invalidate).toHaveBeenCalledTimes(1)
    })

    it('未設定なら確かめない', () => {
      const { invalidate, wrapper } = setup()
      render(<UserAvatar user={{ displayName: 'あ', avatarUrl: null }} />, { wrapper })

      expect(created).toHaveLength(0)
      expect(invalidate).not.toHaveBeenCalled()
    })
  })
})
