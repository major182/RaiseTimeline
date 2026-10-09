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
