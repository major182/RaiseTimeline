import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { CursorPage, UserSummary } from '../api/types'
import { makeUser } from '../test/fixtures'
import { installIntersectionObserver } from '../test/intersection'
import { renderWithProviders } from '../test/render'
import { UserList } from './UserList'

type FetchPage = (cursor: string | null) => Promise<CursorPage<UserSummary>>

function renderList(fetchPage: FetchPage, notFoundText?: string) {
  return renderWithProviders(
    <UserList
      queryKey={['users', 'test']}
      fetchPage={fetchPage}
      emptyText="まだ誰もいません"
      notFoundText={notFoundText}
    />,
  )
}

describe('利用者の一覧（UR-01、画面設計書 1.6）', () => {
  it('UT-FL-01 0 人なら、0 人のときの文言を出す', async () => {
    renderList(() => Promise.resolve({ items: [], nextCursor: null }))

    expect(await screen.findByText('まだ誰もいません')).toBeInTheDocument()
  })

  it('UT-FL-01 対象が見つからない（404）なら、見つからないときの文言を出す', async () => {
    renderList(
      () => Promise.reject(new ApiError(404, 'NOT_FOUND', '見つかりません')),
      'このユーザーは存在しません',
    )

    expect(await screen.findByText('このユーザーは存在しません')).toBeInTheDocument()
  })

  it('UT-FL-01 見つからないときの文言がなければ、404 もほかの誤りと同じくメッセージを出す', async () => {
    renderList(() => Promise.reject(new ApiError(404, 'NOT_FOUND', '見つかりません')))

    expect(await screen.findByRole('alert')).toHaveTextContent('見つかりません')
  })

  it('UT-FL-01 ほかの誤りなら、そのメッセージを出す', async () => {
    renderList(
      () => Promise.reject(new ApiError(500, 'INTERNAL_ERROR', 'エラーが発生しました')),
      'このユーザーは存在しません',
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('エラーが発生しました')
  })

  it('UT-CO-01 下まで来たら、前の返事の nextCursor で続きを読み、つなげて出す', async () => {
    const io = installIntersectionObserver()
    const fetchPage = vi.fn<FetchPage>((cursor) =>
      Promise.resolve(
        cursor === null
          ? { items: [makeUser({ id: 2, displayName: '一人目' })], nextCursor: 'next-1' }
          : { items: [makeUser({ id: 3, displayName: '二人目' })], nextCursor: null },
      ),
    )
    renderList(fetchPage)
    expect(await screen.findByText('一人目')).toBeInTheDocument()

    io.showAll()

    expect(await screen.findByText('二人目')).toBeInTheDocument()
    expect(screen.getByText('一人目')).toBeInTheDocument()
    expect(fetchPage.mock.calls.map((c) => c[0])).toEqual([null, 'next-1'])
  })
})
