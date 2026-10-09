import { Box, Typography } from '@mui/material'
import { useMe } from '../auth/useMe'

/** 仮のホーム画面。タイムライン（SC-03）を作るまでの間、ログインできたことだけを示す。 */
export function HomePage() {
  const { data: me } = useMe()
  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', p: 3 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        ようこそ、{me?.displayName}さん
      </Typography>
      <Typography color="text.secondary">@{me?.username}</Typography>
    </Box>
  )
}
