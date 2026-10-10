import { useQuery } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { getProfileByUsername } from '../api/users'
import { QUERY_KEYS } from '../queryCache'

/** プロフィールを取る。SC-05・SC-08 で使う。ユーザー名に当たる人がいなければ 404 の ApiError になる。 */
export function useProfile(username: string) {
  return useQuery({
    queryKey: QUERY_KEYS.profile(username),
    queryFn: () => getProfileByUsername(username),
  })
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
