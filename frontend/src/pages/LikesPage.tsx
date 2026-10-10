import { useParams } from 'react-router'
import { getLikes } from '../api/posts'
import { PageColumn } from '../components/PageColumn'
import { UserList } from '../components/UserList'
import { QUERY_KEYS } from '../queryCache'

/** いいねした人の一覧（画面設計書 5.12 SC-09）。いいねした時刻の新しい順。 */
export function LikesPage() {
  const postId = Number(useParams().postId)
  return (
    <PageColumn title="いいねしたユーザー">
      <UserList
        queryKey={QUERY_KEYS.likes(postId)}
        fetchPage={(cursor) => getLikes(postId, cursor)}
        emptyText="まだいいねはありません" // N-09
        notFoundText="この投稿は削除されたか、見つかりません" // N-05
      />
    </PageColumn>
  )
}
