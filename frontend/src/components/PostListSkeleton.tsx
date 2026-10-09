import { Box, Skeleton, Stack } from '@mui/material'

/** 一覧の最初の読み込み中に出す、投稿カードの形の仮の表示を 3 つ（画面設計書 1.6）。 */
export function PostListSkeleton() {
  return (
    <Box aria-busy="true" aria-label="読み込み中">
      {[0, 1, 2].map((i) => (
        <Stack
          key={i}
          direction="row"
          spacing={1.5}
          sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
        >
          <Skeleton variant="circular" width={40} height={40} />
          <Box sx={{ flex: 1 }}>
            <Skeleton width="40%" />
            <Skeleton />
            <Skeleton width="80%" />
          </Box>
        </Stack>
      ))}
    </Box>
  )
}
