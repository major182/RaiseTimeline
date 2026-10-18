import { useQuery } from '@tanstack/react-query'
import { getMe } from '../api/auth'

/** ログインしている利用者のキャッシュの名前。ログイン・ログアウトの後に書き換える。 */
export const ME_QUERY_KEY = ['me'] as const

/**
 * ログインしている利用者（ログインしていなければ null）。
 * 一度取ったら、ログイン・ログアウトで書き換えるまで取り直さない。
 */
export function useMe() {
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
    staleTime: Infinity,
    retry: false,
  })
}

/**
 * 自分でログアウトした印（ログアウトで true、期限切れで消す）。
 * RequireAuth がログイン画面へ移すとき、今の画面を「ログインの後に戻る先」にしないために使う。
 * そうしないと、ログアウトした画面（設定）が、次に（別のアカウントで）ログインしたときに開いてしまう。
 */
export const LOGGED_OUT_QUERY_KEY = ['loggedOut'] as const
