import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { createQueryClient } from '../auth/session'
import { QUERY_KEYS } from '../queryCache'
import { makeUser } from '../test/fixtures'
import { json, noContent, renderWithProviders, stubApi } from '../test/render'
import { RecommendedUsers } from './RecommendedUsers'

/** おすすめの一覧を通して、利用者の行とフォローのボタンを確かめる。 */
function setup(
  users = [makeUser({ id: 5, displayName: '鈴木', username: 'suzuki', bio: '山が好き' })],
) {
  const queryClient = createQueryClient()
  queryClient.setQueryData(QUERY_KEYS.recommendations, { items: users })
  renderWithProviders(<RecommendedUsers />, queryClient)
  return userEvent.setup()
}

describe('利用者の行とフォローのボタン（画面設計書 4.2）', () => {
  it('表示名・ユーザー名・自己紹介とプロフィールへのリンクを出す', () => {
    setup()
    expect(screen.getByRole('heading', { name: 'おすすめのユーザー' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '鈴木' })).toHaveAttribute('href', '/users/suzuki')
    expect(screen.getByText('@suzuki')).toBeInTheDocument()
    expect(screen.getByText('山が好き')).toBeInTheDocument()
  })

  it('押したら返事を待たずに「フォロー中」にする', async () => {
    const fetchMock = stubApi({ 'PUT /api/users/5/follow': noContent() })
    const user = setup()
    await user.click(screen.getByRole('button', { name: '鈴木さんをフォロー' }))
    const button = screen.getByRole('button', { name: '鈴木さんのフォローを解除' })
    await user.unhover(button) // 押した直後はマウスが乗っているので「解除」と出る
    expect(button).toHaveTextContent('フォロー中')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/users/5/follow',
      expect.objectContaining({ method: 'PUT' }),
    )
  })

  it('「フォロー中」にマウスを乗せると「解除」になり、押すとフォローをやめる', async () => {
    const fetchMock = stubApi({ 'DELETE /api/users/5/follow': noContent() })
    const user = setup([makeUser({ id: 5, displayName: '鈴木', followedByMe: true })])
    const button = screen.getByRole('button', { name: '鈴木さんのフォローを解除' })
    await user.hover(button)
    expect(button).toHaveTextContent('解除')
    await user.click(button)
    expect(await screen.findByRole('button', { name: '鈴木さんをフォロー' })).toHaveTextContent(
      'フォロー',
    )
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/users/5/follow',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('失敗したら元に戻し、メッセージを出す', async () => {
    stubApi({
      'PUT /api/users/5/follow': json(404, {
        status: 404,
        code: 'NOT_FOUND',
        detail: '見つかりません',
      }),
    })
    const user = setup()
    await user.click(screen.getByRole('button', { name: '鈴木さんをフォロー' }))
    expect(await screen.findByText('見つかりません')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '鈴木さんをフォロー' })).toBeInTheDocument(),
    )
  })

  it('自分の行にはフォローのボタンを出さない', () => {
    setup([makeUser({ id: 1, displayName: '自分', isMe: true })])
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

describe('おすすめの利用者（F-US-04）', () => {
  it('サーバーから取って出す', async () => {
    stubApi({
      'GET /api/users/recommendations': json(200, { items: [makeUser({ displayName: '田中' })] }),
    })
    renderWithProviders(<RecommendedUsers />)
    expect(await screen.findByRole('link', { name: '田中' })).toBeInTheDocument()
  })

  it('おすすめがいなければ、欄ごと出さない', async () => {
    const fetchMock = stubApi({ 'GET /api/users/recommendations': json(200, { items: [] }) })
    renderWithProviders(<RecommendedUsers />)
    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'おすすめのユーザー' })).not.toBeInTheDocument(),
    )
  })
})
