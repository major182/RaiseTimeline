import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { makeUser } from '../test/fixtures'
import { TEST_ME, authResponse, json, renderApp, stubApi } from '../test/render'

const page = <T,>(items: T[]) => json(200, { items, nextCursor: null })

function openSearch(path = '/search', extra: Parameters<typeof stubApi>[0] = {}) {
  const fetchMock = stubApi({
    'POST /api/auth/refresh': authResponse(TEST_ME),
    'GET /api/users/recommendations': json(200, {
      items: [makeUser({ id: 5, username: 'osusume', displayName: 'おすすめの人' })],
    }),
    ...extra,
  })
  renderApp(path)
  return { fetchMock, user: userEvent.setup() }
}

const searchCalls = (fetchMock: ReturnType<typeof stubApi>) =>
  fetchMock.mock.calls.map((c) => c[0] as string).filter((p) => p.startsWith('/api/users/search'))

describe('利用者の検索（SC-07）', () => {
  it('検索語が空なら、おすすめの利用者を出す', async () => {
    openSearch()
    const section = await screen.findByRole('region', { name: 'おすすめのユーザー' })
    expect(await within(section).findByText('おすすめの人')).toBeInTheDocument()
  })

  it('入力をやめてから検索する', async () => {
    const { user, fetchMock } = openSearch('/search', {
      'GET /api/users/search?q=%E5%B1%B1': page([makeUser({ displayName: '山田' })]),
    })
    await user.type(await screen.findByRole('searchbox', { name: '検索' }), '山')
    expect(await screen.findByText('山田')).toBeInTheDocument()
    expect(searchCalls(fetchMock)).toEqual(['/api/users/search?q=%E5%B1%B1'])
  })

  it('打っている途中では検索しない（最後の語だけを 1 回検索する）', async () => {
    const { user, fetchMock } = openSearch('/search', {
      'GET /api/users/search?q=yamada': page([makeUser()]),
    })
    await user.type(await screen.findByRole('searchbox', { name: '検索' }), 'yamada')
    await screen.findByText('@yamada')
    expect(searchCalls(fetchMock)).toEqual(['/api/users/search?q=yamada'])
  })

  it('URL の q から検索語を入れて開く。0 件なら N-06', async () => {
    openSearch('/search?q=nobody', { 'GET /api/users/search?q=nobody': page([]) })
    expect(await screen.findByRole('searchbox', { name: '検索' })).toHaveValue('nobody')
    expect(
      await screen.findByText('「nobody」に一致するユーザーは見つかりませんでした'),
    ).toBeInTheDocument()
  })

  it('50 文字を超えたら、検索せずに誤りを出す', async () => {
    const { user, fetchMock } = openSearch()
    await user.click(await screen.findByRole('searchbox', { name: '検索' }))
    await user.paste('あ'.repeat(51))
    expect(screen.getByText('1〜50 文字で入力してください')).toBeInTheDocument()
    await new Promise((r) => setTimeout(r, 400))
    expect(searchCalls(fetchMock)).toEqual([])
  })

  it('ナビの「検索」から開ける', async () => {
    const { user } = openSearch('/')
    const nav = await screen.findByRole('navigation', { name: 'メインのナビ' })
    await user.click(within(nav).getByRole('link', { name: '検索' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/search'))
  })
})
