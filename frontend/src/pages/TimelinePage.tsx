import Close from '@mui/icons-material/Close'
import {
  Alert,
  Box,
  Button,
  IconButton,
  Stack,
  Tab,
  Tabs,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { useInfiniteQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import { getAllTimeline, getFollowingTimeline } from '../api/timeline'
import type { CursorPage, FollowingTimeline, Post } from '../api/types'
import { LoadMore } from '../components/LoadMore'
import { PostCard } from '../components/PostCard'
import { PostListSkeleton } from '../components/PostListSkeleton'
import { RecommendedUsers } from '../components/RecommendedUsers'
import { QUERY_KEYS } from '../queryCache'

export type TimelineTab = 'following' | 'all'

/** 選んだタブを覚えておく、端末（ブラウザ）の保存場所の名前（BR-51）。 */
export const TAB_STORAGE_KEY = 'raisetimeline.timelineTab'

/** 覚えておいたタブ。読めなければ（初めて・保存が禁止されている）フォロー中。 */
function loadTab(): TimelineTab {
  try {
    return localStorage.getItem(TAB_STORAGE_KEY) === 'all' ? 'all' : 'following'
  } catch {
    return 'following'
  }
}

function saveTab(tab: TimelineTab) {
  try {
    localStorage.setItem(TAB_STORAGE_KEY, tab)
  } catch {
    // 保存できなくても、タブの切り替えはできる
  }
}

/** タブごとの一覧の読み込み。2 つのタブの形の違い（ハイライトの有無）を、ここでそろえる。 */
function useTimeline(tab: TimelineTab) {
  return useInfiniteQuery<FollowingTimeline | CursorPage<Post>>({
    queryKey: tab === 'following' ? QUERY_KEYS.followingTimeline : QUERY_KEYS.allTimeline,
    queryFn: ({ pageParam }) =>
      tab === 'following'
        ? getFollowingTimeline(pageParam as string | null)
        : getAllTimeline(pageParam as string | null),
    initialPageParam: null,
    getNextPageParam: (last) => last.nextCursor,
    // 新しい投稿は、開き直すか「ホーム」を押したときだけ出す（BR-54）。
    // 勝手に読み直すと、フォロー中タブの「最後に開いた時刻」が変わり、ハイライトが消えてしまうため
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })
}

/** タイムライン（画面設計書 5.3 SC-03）。 */
export function TimelinePage() {
  const theme = useTheme()
  const showAside = useMediaQuery(theme.breakpoints.up('lg'))
  const [tab, setTab] = useState<TimelineTab>(loadTab)

  return (
    <Stack direction="row" spacing={3} sx={{ justifyContent: 'center', px: { lg: 3 } }}>
      <Box
        sx={{
          width: '100%',
          maxWidth: 600,
          minHeight: '100vh',
          borderLeft: { sm: 1 },
          borderRight: { sm: 1 },
          borderColor: { sm: 'divider' },
        }}
      >
        <Box
          sx={{
            position: 'sticky',
            top: 0,
            zIndex: 1,
            bgcolor: 'background.paper',
            borderBottom: 1,
            borderColor: 'divider',
          }}
        >
          <Typography variant="h6" component="h1" sx={{ px: 2, pt: 1.5 }}>
            ホーム
          </Typography>
          <Tabs
            value={tab}
            onChange={(_, value: TimelineTab) => {
              setTab(value)
              saveTab(value)
              window.scrollTo({ top: 0 })
            }}
            variant="fullWidth"
          >
            <Tab label="フォロー中" value="following" />
            <Tab label="全体" value="all" />
          </Tabs>
        </Box>
        {/* key でタブごとに作り直し、切り替えたら一覧の先頭から表示する */}
        <TimelineList key={tab} tab={tab} />
      </Box>
      {showAside && (
        <Box component="aside" sx={{ width: 320, flexShrink: 0, pt: 2 }}>
          <Box sx={{ position: 'sticky', top: 16 }}>
            <RecommendedUsers />
          </Box>
        </Box>
      )}
    </Stack>
  )
}

function TimelineList({ tab }: { tab: TimelineTab }) {
  const query = useTimeline(tab)
  const [highlightsClosed, setHighlightsClosed] = useState(false)

  if (query.isPending) return <PostListSkeleton />
  if (query.isError) {
    return (
      <Alert
        severity="error"
        sx={{ m: 2 }}
        action={
          <Button color="inherit" size="small" onClick={() => void query.refetch()}>
            再読み込み
          </Button>
        }
      >
        {query.error.message}
      </Alert>
    )
  }

  const pages = query.data.pages
  const highlights = (pages[0] as Partial<FollowingTimeline>).highlights ?? []
  const posts = pages.flatMap((p) => p.items)

  if (posts.length === 0 && highlights.length === 0) return <EmptyTimeline tab={tab} />

  return (
    <>
      {highlights.length > 0 && !highlightsClosed && (
        <Box
          component="section"
          aria-labelledby="highlights-heading"
          sx={{ borderBottom: 4, borderColor: 'divider' }}
        >
          <Stack direction="row" sx={{ alignItems: 'center', px: 2, pt: 1.5 }}>
            <Typography
              id="highlights-heading"
              variant="subtitle1"
              component="h2"
              sx={{ fontWeight: 'bold', flex: 1 }}
            >
              留守中のハイライト
            </Typography>
            <IconButton
              size="small"
              aria-label="ハイライトを閉じる"
              onClick={() => setHighlightsClosed(true)}
            >
              <Close fontSize="small" />
            </IconButton>
          </Stack>
          {highlights.map((post) => (
            <PostCard key={post.id} post={post} />
          ))}
        </Box>
      )}
      <Box component="section" aria-label="投稿の一覧">
        {posts.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </Box>
      <LoadMore
        hasNext={query.hasNextPage}
        loading={query.isFetchingNextPage}
        onLoadMore={() => void query.fetchNextPage()}
      />
    </>
  )
}

/** 一覧が空のとき（画面設計書 5.3、N-01・N-02）。 */
function EmptyTimeline({ tab }: { tab: TimelineTab }) {
  return (
    <Box sx={{ textAlign: 'center', px: 2, py: 6 }}>
      {tab === 'following' ? (
        <>
          <Typography gutterBottom>
            まだ投稿がありません。ほかのユーザーをフォローしてみましょう
          </Typography>
          <Button component={RouterLink} to="/search" variant="contained" sx={{ mt: 1 }}>
            ユーザーを探す
          </Button>
        </>
      ) : (
        <Typography>まだ投稿がありません。最初の投稿をしてみましょう</Typography>
      )}
    </Box>
  )
}
