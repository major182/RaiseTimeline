import { Alert, Tab, Tabs } from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import { getFollowers, getFollowing } from '../api/users'
import { EmptyMessage, PageColumn } from '../components/PageColumn'
import { UserList } from '../components/UserList'
import { QUERY_KEYS } from '../queryCache'
import { isNotFound, useProfile } from './useProfile'

export type FollowTab = 'following' | 'followers'

/**
 * フォロー／フォロワー一覧（画面設計書 5.11 SC-08）。
 * タブごとに URL を分け、ブラウザの戻るで前のタブに戻れるようにする（画面設計書 3 章）。
 */
export function FollowListPage({ tab }: { tab: FollowTab }) {
  const username = useParams().username ?? ''
  const query = useProfile(username)
  const profile = query.data
  const base = `/users/${username}`

  let content
  if (isNotFound(query.error)) {
    content = <EmptyMessage>このユーザーは見つかりません</EmptyMessage> // N-04
  } else if (query.isError) {
    content = (
      <Alert severity="error" sx={{ m: 2 }}>
        {query.error.message}
      </Alert>
    )
  } else if (profile) {
    content =
      tab === 'following' ? (
        <UserList
          key="following"
          queryKey={QUERY_KEYS.following(profile.id)}
          fetchPage={(cursor) => getFollowing(profile.id, cursor)}
          emptyText="まだ誰もフォローしていません" // N-07
        />
      ) : (
        <UserList
          key="followers"
          queryKey={QUERY_KEYS.followers(profile.id)}
          fetchPage={(cursor) => getFollowers(profile.id, cursor)}
          emptyText="まだフォロワーはいません" // N-08
        />
      )
  }

  return (
    <PageColumn
      title={profile?.displayName ?? ''}
      subtitle={profile && `@${profile.username}`}
      headerExtra={
        <Tabs value={tab} variant="fullWidth">
          <Tab label="フォロー" value="following" component={RouterLink} to={`${base}/following`} />
          <Tab
            label="フォロワー"
            value="followers"
            component={RouterLink}
            to={`${base}/followers`}
          />
        </Tabs>
      }
    >
      {content}
    </PageColumn>
  )
}
