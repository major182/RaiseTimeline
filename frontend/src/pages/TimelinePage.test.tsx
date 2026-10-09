import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { makePost } from '../test/fixtures'
import { installIntersectionObserver } from '../test/intersection'
import { TEST_ME, authResponse, json, renderApp, stubApi } from '../test/render'
import { TAB_STORAGE_KEY } from './TimelinePage'

const FOLLOWING = 'GET /api/timeline/following'
const ALL = 'GET /api/timeline/all'

function followingPage(
  items = [makePost()],
  highlights = [] as ReturnType<typeof makePost>[],
  nextCursor: string | null = null,
) {
  return json(200, { highlights, items, nextCursor })
}

function allPage(items = [makePost()], nextCursor: string | null = null) {
  return json(200, { items, nextCursor })
}

/** 一覧に出ている投稿の本文を、上から順に返す。 */
function listedBodies() {
  const list = screen.getByRole('region', { name: '投稿の一覧' })
  return within(list)
    .getAllByRole('article')
    .map((a) => within(a).getByTestId('post-body').textContent)
}

afterEach(() => localStorage.clear())

describe('タイムラインのタブ（F-TL-06、BR-51）', () => {
  it('最初はフォロー中タブを開く', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: followingPage([makePost({ body: 'フォロー中の投稿' })]),
    })
    renderApp('/')
    expect(await screen.findByText('フォロー中の投稿')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'フォロー中' })).toHaveAttribute('aria-selected', 'true')
  })

  it('全体タブに切り替えると全員の投稿を出し、選んだタブを覚える', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: followingPage([makePost({ body: 'フォロー中の投稿' })]),
      [ALL]: allPage([makePost({ id: 200, body: '全体の投稿' })]),
    })
    renderApp('/')
    const user = userEvent.setup()
    await screen.findByText('フォロー中の投稿')
    await user.click(screen.getByRole('tab', { name: '全体' }))
    expect(await screen.findByText('全体の投稿')).toBeInTheDocument()
    expect(screen.queryByText('フォロー中の投稿')).not.toBeInTheDocument()
    expect(localStorage.getItem(TAB_STORAGE_KEY)).toBe('all')
  })

  it('覚えたタブで開く', async () => {
    localStorage.setItem(TAB_STORAGE_KEY, 'all')
    const fetchMock = stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [ALL]: allPage([makePost({ body: '全体の投稿' })]),
    })
    renderApp('/')
    expect(await screen.findByText('全体の投稿')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '全体' })).toHaveAttribute('aria-selected', 'true')
    expect(fetchMock.mock.calls.some((c) => c[0] === '/api/timeline/following')).toBe(false)
  })
})

describe('一覧と続きの読み込み（F-TL-01・02・05、BR-53）', () => {
  it('下まで見たら、カーソルを付けて続きの 20 件を読み、後ろにつなげる', async () => {
    const io = installIntersectionObserver()
    const fetchMock = stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: followingPage([makePost({ id: 1, body: '1件目' })], [], 'next/cursor=='),
      [`${FOLLOWING}?cursor=next%2Fcursor%3D%3D`]: followingPage([
        makePost({ id: 2, body: '2件目' }),
      ]),
    })
    renderApp('/')
    await screen.findByText('1件目')
    io.showAll()
    expect(await screen.findByText('2件目')).toBeInTheDocument()
    expect(listedBodies()).toEqual(['1件目', '2件目'])
    expect(screen.getByText('これ以上の投稿はありません')).toBeInTheDocument()
    expect(
      fetchMock.mock.calls.filter((c) => String(c[0]).startsWith('/api/timeline/following')),
    ).toHaveLength(2)
  })

  it('いいね経由の投稿には、いいねした人を添える（F-TL-07）', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: followingPage([
        makePost({
          body: 'いいね経由',
          likedVia: { displayName: '鈴木', username: 'suzuki', othersCount: 1 },
        }),
      ]),
    })
    renderApp('/')
    expect(await screen.findByText('鈴木さん、ほか 1 人がいいねしました')).toBeInTheDocument()
  })

  it('読み込みに失敗したら、メッセージと再読み込みのボタンを出す', async () => {
    let calls = 0
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: () =>
        ++calls === 1
          ? json(500, {
              status: 500,
              code: 'INTERNAL_ERROR',
              detail: 'エラーが発生しました。時間をおいてもう一度お試しください',
            })
          : followingPage([makePost({ body: '読み込めた' })]),
    })
    renderApp('/')
    const user = userEvent.setup()
    expect(
      await screen.findByText('エラーが発生しました。時間をおいてもう一度お試しください'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '再読み込み' }))
    expect(await screen.findByText('読み込めた')).toBeInTheDocument()
  })
})

describe('一覧が空のとき（N-01・N-02）', () => {
  it('フォロー中タブは、利用者の検索へのボタンを出す', async () => {
    stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME), [FOLLOWING]: followingPage([]) })
    renderApp('/')
    expect(
      await screen.findByText('まだ投稿がありません。ほかのユーザーをフォローしてみましょう'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'ユーザーを探す' })).toHaveAttribute('href', '/search')
  })

  it('全体タブは、最初の投稿を促す', async () => {
    localStorage.setItem(TAB_STORAGE_KEY, 'all')
    stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME), [ALL]: allPage([]) })
    renderApp('/')
    expect(
      await screen.findByText('まだ投稿がありません。最初の投稿をしてみましょう'),
    ).toBeInTheDocument()
  })
})

describe('留守中のハイライト（F-TL-08）', () => {
  it('一覧の上に見出しを付けて出し、「×」で畳める', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: followingPage(
        [makePost({ id: 2, body: 'ふつうの投稿' })],
        [makePost({ id: 1, body: '反応の多い投稿' })],
      ),
    })
    renderApp('/')
    const user = userEvent.setup()
    const section = await screen.findByRole('region', { name: '留守中のハイライト' })
    expect(within(section).getByText('反応の多い投稿')).toBeInTheDocument()
    expect(listedBodies()).toEqual(['ふつうの投稿'])

    await user.click(screen.getByRole('button', { name: 'ハイライトを閉じる' }))
    expect(screen.queryByRole('region', { name: '留守中のハイライト' })).not.toBeInTheDocument()
    expect(screen.getByText('ふつうの投稿')).toBeInTheDocument()
  })

  it('ハイライトがなければ見出しも出さない', async () => {
    stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME), [FOLLOWING]: followingPage() })
    renderApp('/')
    await screen.findByRole('region', { name: '投稿の一覧' })
    expect(screen.queryByText('留守中のハイライト')).not.toBeInTheDocument()
  })

  it('投稿がなくてもハイライトがあれば、空の表示にしない', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: followingPage([], [makePost({ body: 'ハイライトだけ' })]),
    })
    renderApp('/')
    expect(await screen.findByText('ハイライトだけ')).toBeInTheDocument()
    expect(screen.queryByText(/フォローしてみましょう/)).not.toBeInTheDocument()
  })
})

describe('読み込み直し（F-TL-04、BR-54）', () => {
  it('ホームにいるときに「ホーム」を押すと、先頭から読み込み直す', async () => {
    let calls = 0
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: () => followingPage([makePost({ body: `読み込み ${++calls} 回目` })]),
    })
    renderApp('/')
    const user = userEvent.setup()
    await screen.findByText('読み込み 1 回目')
    await user.click(screen.getByRole('link', { name: 'ホーム' }))
    expect(await screen.findByText('読み込み 2 回目')).toBeInTheDocument()
  })

  it('画面にふたたび戻っただけでは読み込み直さない（ハイライトを消さないため）', async () => {
    const fetchMock = stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      [FOLLOWING]: followingPage(),
    })
    renderApp('/')
    await screen.findByRole('region', { name: '投稿の一覧' })
    window.dispatchEvent(new Event('focus'))
    await waitFor(() =>
      expect(fetchMock.mock.calls.filter((c) => c[0] === '/api/timeline/following')).toHaveLength(
        1,
      ),
    )
  })
})
