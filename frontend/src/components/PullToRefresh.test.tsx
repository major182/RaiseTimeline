import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { makePost } from '../test/fixtures'
import { TEST_ME, authResponse, json, renderApp, stubApi } from '../test/render'

afterEach(() => localStorage.clear())

const timelineCalls = (fetchMock: ReturnType<typeof stubApi>) =>
  fetchMock.mock.calls.filter((c) => c[0] === '/api/timeline/following').length

/** 一番上で、指を from から to まで下ろして離す。 */
function pull(from: number, to: number) {
  const area = screen.getByTestId('pull-to-refresh')
  fireEvent.touchStart(area, { touches: [{ clientY: from }] })
  fireEvent.touchMove(area, { touches: [{ clientY: to }] })
  fireEvent.touchEnd(area, { changedTouches: [{ clientY: to }] })
}

async function openTimeline() {
  const fetchMock = stubApi({
    'POST /api/auth/refresh': authResponse(TEST_ME),
    'GET /api/timeline/following': json(200, {
      highlights: [],
      items: [makePost({ body: '投稿' })],
      nextCursor: null,
    }),
  })
  renderApp('/')
  await screen.findByText('投稿')
  return fetchMock
}

describe('引っ張って読み込み直す（F-TL-04、画面設計書 1.6）', () => {
  it('十分に引っ張って離したら、タイムラインを読み込み直す', async () => {
    const fetchMock = await openTimeline()
    expect(timelineCalls(fetchMock)).toBe(1)
    pull(100, 300) // 指で 200px（表示は半分の 100px）
    await waitFor(() => expect(timelineCalls(fetchMock)).toBe(2))
    expect(await screen.findByText('投稿')).toBeInTheDocument()
  })

  it('少しだけなら読み込み直さない', async () => {
    const fetchMock = await openTimeline()
    pull(100, 150)
    await new Promise((r) => setTimeout(r, 50))
    expect(timelineCalls(fetchMock)).toBe(1)
  })

  it('一番上でなければ、ふつうのスクロールとして扱う', async () => {
    const fetchMock = await openTimeline()
    window.scrollY = 300
    try {
      pull(100, 400)
      await new Promise((r) => setTimeout(r, 50))
      expect(timelineCalls(fetchMock)).toBe(1)
    } finally {
      window.scrollY = 0
    }
  })

  it('タイムラインを出している間は、ブラウザ自体の引っ張って再読み込みを止める', async () => {
    await openTimeline()
    expect(document.documentElement.style.overscrollBehaviorY).toBe('contain')
  })
})
