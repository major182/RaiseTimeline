import { Box, Typography } from '@mui/material'
import type { ReactNode } from 'react'

/** ログイン・利用者登録の画面の骨組み。ナビを出さず、中央に入力欄だけを置く（画面設計書 1.1）。 */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Box sx={{ maxWidth: 420, mx: 'auto', px: 2, py: 5 }}>
      <Typography sx={{ fontSize: 30, fontWeight: 800, color: 'primary.main' }}>
        RaiseTimeline
      </Typography>
      <Typography variant="h5" component="h1" sx={{ fontWeight: 700, mt: 2, mb: 3 }}>
        {title}
      </Typography>
      {children}
    </Box>
  )
}
