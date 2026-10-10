import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import { Alert, Box, Button, Link, Skeleton, Stack, Typography } from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import type { Profile } from '../api/types'
import { getUserPosts } from '../api/users'
import { FollowButton } from '../components/FollowButton'
import { EmptyMessage, PageColumn } from '../components/PageColumn'
import { PostList } from '../components/PostList'
import { UserAvatar } from '../components/UserAvatar'
import { formatCount, formatJoined } from '../format'
import { QUERY_KEYS } from '../queryCache'
import { isNotFound, useProfile } from './useProfile'

/** プロフィール（画面設計書 5.8 SC-05）。上に利用者の情報、下にこの人の投稿の一覧。 */
export function ProfilePage() {
  const username = useParams().username ?? ''
  const query = useProfile(username)
  const profile = query.data

  let content
  if (isNotFound(query.error)) {
    content = <EmptyMessage>このユーザーは見つかりません</EmptyMessage> // N-04
  } else if (query.isPending) {
    content = <ProfileSkeleton />
  } else if (query.isError) {
    content = (
      <Alert severity="error" sx={{ m: 2 }}>
        {query.error.message}
      </Alert>
    )
  } else {
    content = (
      <>
        <ProfileHeader profile={query.data} />
        <PostList
          // ユーザー名が変わっても同じ一覧を使えるよう、ID で覚える
          queryKey={QUERY_KEYS.userPosts(query.data.id)}
          fetchPage={(cursor) => getUserPosts(query.data.id, cursor)}
          emptyText="まだ投稿がありません" // N-03
        />
      </>
    )
  }

  return (
    <PageColumn
      title={profile?.displayName ?? 'プロフィール'}
      subtitle={profile && `@${profile.username}`}
    >
      {content}
    </PageColumn>
  )
}

function ProfileHeader({ profile }: { profile: Profile }) {
  const base = `/users/${profile.username}`
  return (
    <Box
      component="section"
      aria-label="プロフィール"
      sx={{ px: 2, py: 2, borderBottom: 1, borderColor: 'divider' }}
    >
      <Stack direction="row" sx={{ alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <UserAvatar user={profile} size={80} />
        {profile.isMe ? (
          <Button
            component={RouterLink}
            to="/settings/profile"
            variant="outlined"
            sx={{ borderRadius: 5 }}
          >
            プロフィールを編集
          </Button>
        ) : (
          <FollowButton user={profile} />
        )}
      </Stack>
      <Typography variant="h6" component="p" sx={{ fontWeight: 'bold', mt: 1.5, lineHeight: 1.3 }}>
        {profile.displayName}
      </Typography>
      <Typography color="text.secondary">@{profile.username}</Typography>
      {profile.bio && (
        <Typography
          data-testid="profile-bio"
          sx={{ mt: 1.5, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}
        >
          {profile.bio}
        </Typography>
      )}
      <Stack
        direction="row"
        spacing={0.5}
        sx={{ alignItems: 'center', mt: 1.5, color: 'text.secondary' }}
      >
        <CalendarMonthOutlined fontSize="small" />
        <Typography variant="body2">{formatJoined(profile.createdAt)}</Typography>
      </Stack>
      <Stack direction="row" spacing={2.5} sx={{ mt: 1 }}>
        <CountLink to={`${base}/following`} count={profile.followingCount} label="フォロー" />
        <CountLink to={`${base}/followers`} count={profile.followerCount} label="フォロワー" />
      </Stack>
    </Box>
  )
}

/** フォロー数・フォロワー数。押すと SC-08 の該当タブへ。 */
function CountLink({ to, count, label }: { to: string; count: number; label: string }) {
  return (
    <Link component={RouterLink} to={to} underline="hover" color="inherit" variant="body2">
      <Box component="span" sx={{ fontWeight: 'bold' }}>
        {formatCount(count)}
      </Box>{' '}
      <Box component="span" sx={{ color: 'text.secondary' }}>
        {label}
      </Box>
    </Link>
  )
}

function ProfileSkeleton() {
  return (
    <Box sx={{ px: 2, py: 2 }} aria-busy="true" aria-label="読み込み中">
      <Skeleton variant="circular" width={80} height={80} />
      <Skeleton width="40%" sx={{ mt: 1.5 }} />
      <Skeleton width="30%" />
      <Skeleton width="80%" sx={{ mt: 1.5 }} />
    </Box>
  )
}
