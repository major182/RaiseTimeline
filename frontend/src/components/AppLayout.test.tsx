import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { TEST_ME, authResponse, json, renderApp, stubApi } from '../test/render'

describe('画面の枠（画面設計書 1.1）', () => {
  it('ナビにホーム・プロフィール・設定を出す', async () => {
    stubApi({ 'POST /api/auth/refresh': authResponse(TEST_ME) })
    renderApp('/')
    const nav = await screen.findByRole('navigation', { name: 'メインのナビ' })
    expect(within(nav).getByRole('link', { name: 'ホーム' })).toHaveAttribute('href', '/')
    expect(within(nav).getByRole('link', { name: 'プロフィール' })).toHaveAttribute(
      'href',
      '/users/raise_me',
    )
    expect(within(nav).getByRole('link', { name: '設定' })).toHaveAttribute('href', '/settings')
  })

  it('名前の始まりが同じ他人のプロフィールでは、「プロフィール」を選んだ状態にしない', async () => {
    stubApi({
      'POST /api/auth/refresh': authResponse(TEST_ME),
      'GET /api/users/by-username/raise_me_2': json(404, {
        status: 404,
        code: 'NOT_FOUND',
        detail: '見つかりません',
      }),
    })
    renderApp('/users/raise_me_2')
    const nav = await screen.findByRole('navigation', { name: 'メインのナビ' })
    expect(within(nav).getByRole('link', { name: 'プロフィール' })).not.toHaveClass('Mui-selected')
  })
})
