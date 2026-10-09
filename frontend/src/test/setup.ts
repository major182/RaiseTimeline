// テストの共通の準備。toBeInTheDocument などの、画面の状態を確かめる書き方を使えるようにする
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setAccessToken } from '../api/tokenStore'

afterEach(() => {
  cleanup()
  setAccessToken(null) // メモリのアクセストークンをテストごとに捨てる
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  // テストで置いた Cookie を消す
  for (const cookie of document.cookie.split(';')) {
    const name = cookie.split('=')[0].trim()
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
})
