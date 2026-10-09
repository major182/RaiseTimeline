import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { makePost, makeUser } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { likedViaText } from '../format'
import { PostCard } from './PostCard'

describe('投稿カード（画面設計書 4.1）', () => {
  it('表示名・ユーザー名・投稿日時・本文・数を出す', () => {
    renderWithProviders(
      <PostCard post={makePost({ body: '1行目\n2行目', likeCount: 12_345, commentCount: 3 })} />,
    )
    const card = screen.getByRole('article', { name: '山田さんの投稿' })
    expect(within(card).getByText('山田')).toBeInTheDocument()
    expect(within(card).getByText('@yamada')).toBeInTheDocument()
    expect(within(card).getByText('3時間')).toBeInTheDocument()
    expect(within(card).getByText(/1行目\s2行目/)).toBeInTheDocument()
    expect(within(card).getByRole('button', { name: 'いいね（12345）' })).toHaveTextContent('1.2万')
    expect(within(card).getByRole('link', { name: 'コメント（3）' })).toHaveTextContent('3')
  })

  it('編集した投稿には「編集済み」を出す', () => {
    renderWithProviders(<PostCard post={makePost({ editedAt: new Date().toISOString() })} />)
    expect(screen.getByText(/・編集済み/)).toBeInTheDocument()
  })

  it('未編集の投稿には「編集済み」を出さない', () => {
    renderWithProviders(<PostCard post={makePost()} />)
    expect(screen.queryByText(/編集済み/)).not.toBeInTheDocument()
  })

  it('名前とアイコンはプロフィールへ、日時とコメントは投稿の詳細へのリンク', () => {
    renderWithProviders(<PostCard post={makePost({ id: 7 })} />)
    expect(screen.getByRole('link', { name: '山田さんのプロフィール' })).toHaveAttribute(
      'href',
      '/users/yamada',
    )
    expect(screen.getByRole('link', { name: '山田' })).toHaveAttribute('href', '/users/yamada')
    expect(screen.getByRole('link', { name: '3時間' })).toHaveAttribute('href', '/posts/7')
    expect(screen.getByRole('link', { name: 'コメント（0）' })).toHaveAttribute('href', '/posts/7')
  })

  it('本文の HTML は文字のまま出し、動かさない（XSS の対策）', () => {
    renderWithProviders(
      <PostCard post={makePost({ body: '<img src=x onerror=alert(1)><b>太字</b>' })} />,
    )
    expect(screen.getByText('<img src=x onerror=alert(1)><b>太字</b>')).toBeInTheDocument()
    expect(document.querySelector('article b')).toBeNull()
  })

  it('インプレッション数・リツイートのボタンは置かない（BR-40、BR-41）', () => {
    renderWithProviders(<PostCard post={makePost()} />)
    expect(
      screen.queryByText(/インプレッション|表示回数|リツイート|リポスト/),
    ).not.toBeInTheDocument()
    // ボタンは「いいね」だけ（コメントはリンク）
    expect(screen.getAllByRole('button').map((b) => b.getAttribute('aria-label'))).toEqual([
      'いいね（0）',
    ])
  })

  it('いいね経由のときだけ、いいねした人を出す', () => {
    renderWithProviders(
      <PostCard
        post={makePost({ likedVia: { displayName: '佐藤', username: 'sato', othersCount: 0 } })}
      />,
    )
    expect(screen.getByText('佐藤さんがいいねしました')).toBeInTheDocument()
  })

  it('いいね経由でなければ、いいねした人の行は出さない', () => {
    renderWithProviders(<PostCard post={makePost()} />)
    expect(screen.queryByText(/いいねしました/)).not.toBeInTheDocument()
  })

  it('画像は枚数に合わせて並べ、代わりの文字を付ける', () => {
    const images = [1, 2, 3].map((i) => ({ url: `/media/${i}.png`, width: 100, height: 100 }))
    renderWithProviders(<PostCard post={makePost({ images })} />)
    expect(screen.getByTestId('post-images')).toHaveAttribute('data-count', '3')
    expect(screen.getAllByRole('img', { name: /^画像 \d\/3$/ })).toHaveLength(3)
  })

  it('画像がなければ画像の枠を出さない', () => {
    renderWithProviders(<PostCard post={makePost()} />)
    expect(screen.queryByTestId('post-images')).not.toBeInTheDocument()
  })

  it('アイコンが未設定なら、表示名の最初の文字を出す', () => {
    renderWithProviders(
      <PostCard post={makePost({ author: makeUser({ displayName: '😀ニコ' }) })} />,
    )
    expect(screen.getByText('😀')).toBeInTheDocument()
  })
})

describe('いいね経由の文言', () => {
  it('ほかにもいれば人数を添える', () => {
    expect(likedViaText({ displayName: '山田', username: 'y', othersCount: 2 })).toBe(
      '山田さん、ほか 2 人がいいねしました',
    )
    expect(likedViaText({ displayName: '山田', username: 'y', othersCount: 0 })).toBe(
      '山田さんがいいねしました',
    )
  })
})
