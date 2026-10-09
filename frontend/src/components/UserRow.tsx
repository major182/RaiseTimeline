import { Box, Link, Stack, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router'
import type { UserSummary } from '../api/types'
import { FollowButton } from './FollowButton'
import { UserAvatar } from './UserAvatar'

/** 利用者の行（画面設計書 4.2 UR-01）。検索・フォロー一覧・いいねした人・おすすめで使う。 */
export function UserRow({ user }: { user: UserSummary }) {
  const profile = `/users/${user.username}`
  return (
    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start', px: 2, py: 1.5 }}>
      <Link
        component={RouterLink}
        to={profile}
        aria-label={`${user.displayName}さんのプロフィール`}
      >
        <UserAvatar user={user} />
      </Link>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Link
          component={RouterLink}
          to={profile}
          underline="hover"
          color="inherit"
          sx={{ fontWeight: 'bold', display: 'block' }}
          noWrap
        >
          {user.displayName}
        </Link>
        <Typography color="text.secondary" noWrap>
          @{user.username}
        </Typography>
        {user.bio && (
          // 自己紹介は 1 行だけ。はみ出したら「…」
          <Typography variant="body2" noWrap>
            {user.bio}
          </Typography>
        )}
      </Box>
      <FollowButton user={user} />
    </Stack>
  )
}
