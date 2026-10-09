import { Box, Button, Typography } from '@mui/material'
import { Link } from 'react-router'

/** SC-11 見つからない（画面設計書 5.14）。 */
export function NotFoundPage() {
  return (
    <Box sx={{ textAlign: 'center', mt: 8, p: 3 }}>
      <Typography variant="h6" component="h1" gutterBottom>
        ページが見つかりません
      </Typography>
      <Button variant="contained" component={Link} to="/">
        ホームへ
      </Button>
    </Box>
  )
}
