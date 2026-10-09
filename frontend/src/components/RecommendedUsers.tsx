import { Box, Skeleton, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { getRecommendations } from '../api/timeline'
import { QUERY_KEYS } from '../queryCache'
import { UserRow } from './UserRow'

/** おすすめの利用者（画面設計書 5.3、F-US-04）。最大 5 人。いなければ何も出さない。 */
export function RecommendedUsers() {
  const { data, isPending, isError } = useQuery({
    queryKey: QUERY_KEYS.recommendations,
    queryFn: getRecommendations,
    staleTime: 5 * 60 * 1000, // フォローしても行が消えないよう、しばらくは取り直さない
  })
  if (isError || (data && data.items.length === 0)) return null
  return (
    <Box
      component="section"
      aria-labelledby="recommended-heading"
      sx={{ border: 1, borderColor: 'divider', borderRadius: 3, py: 1 }}
    >
      <Typography id="recommended-heading" variant="h6" component="h2" sx={{ px: 2, py: 1 }}>
        おすすめのユーザー
      </Typography>
      {isPending ? (
        <Box sx={{ px: 2 }}>
          <Skeleton height={56} />
          <Skeleton height={56} />
        </Box>
      ) : (
        data.items.map((user) => <UserRow key={user.id} user={user} />)
      )}
    </Box>
  )
}
