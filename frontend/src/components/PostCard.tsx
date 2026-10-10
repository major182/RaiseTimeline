import ChatBubbleOutline from '@mui/icons-material/ChatBubbleOutlineOutlined'
import FavoriteBorder from '@mui/icons-material/FavoriteBorder'
import { Box, Button, Link, Stack, Typography } from '@mui/material'
import { useLayoutEffect, useRef, useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import type { Post } from '../api/types'
import { formatCount, formatFullTime, formatPostTime, likedViaText } from '../format'
import { LikeButton } from './LikeButton'
import { PostImages } from './PostImages'
import { PostMenu } from './PostMenu'
import { UserAvatar } from './UserAvatar'

/** 一覧で本文を何行まで出すか（それを超えたら「続きを読む」）。 */
const MAX_LINES = 10

type Props = {
  post: Post
  /** 投稿の詳細（SC-04）で使うとき true。本文を全文・日時を「2026年10月9日 14:05」で出し、いいねした人へのリンクを出す。 */
  detail?: boolean
  /** 削除したあとに呼ぶ（詳細の画面からホームへ戻すため）。 */
  onDeleted?: () => void
}

/**
 * 投稿カード（画面設計書 4.1 PC-01）。タイムライン・投稿の詳細・プロフィールで使う。
 * 一覧では、カードの余白や本文を押すと投稿の詳細へ移る。
 * インプレッション数・リツイートのボタンは置かない（BR-40、BR-41）。
 * 利用者の文字（本文・表示名）は {text} で表示し、HTML として扱わない（XSS の対策。技術選定書 4.7）。
 */
export function PostCard({ post, detail = false, onDeleted }: Props) {
  const navigate = useNavigate()
  const profile = `/users/${post.author.username}`
  const detailPath = `/posts/${post.id}`
  const bodyRef = useRef<HTMLParagraphElement>(null)
  const [clamped, setClamped] = useState(false)

  // 本文が 10 行を超えて切られているかを、表示した大きさで調べる
  useLayoutEffect(() => {
    const el = bodyRef.current
    if (el) setClamped(el.scrollHeight > el.clientHeight + 1)
  }, [post.body])

  /** 余白や本文を押したら詳細へ。リンク・ボタン・文字を選んでいるとき・カードの外（メニューやダイアログ）は除く。 */
  const openDetail = (e: React.MouseEvent<HTMLElement>) => {
    const target = e.target as HTMLElement
    if (!e.currentTarget.contains(target)) return
    if (target.closest('a, button')) return
    if (window.getSelection()?.toString()) return
    void navigate(detailPath)
  }

  return (
    <Box
      component="article"
      aria-label={`${post.author.displayName}さんの投稿`}
      onClick={detail ? undefined : openDetail}
      sx={{
        px: 2,
        py: 1.5,
        borderBottom: 1,
        borderColor: 'divider',
        cursor: detail ? undefined : 'pointer',
        '&:hover': detail ? undefined : { bgcolor: 'action.hover' },
      }}
    >
      {post.likedVia && (
        <Stack
          direction="row"
          spacing={1}

          sx={{ alignItems: 'center', pl: 4, mb: 0.5, color: 'text.secondary' }}
        >
          <FavoriteBorder sx={{ fontSize: 16 }} />
          <Typography variant="body2">{likedViaText(post.likedVia)}</Typography>
        </Stack>
      )}
      <Stack direction="row" spacing={1.5}>
        <Link
          component={RouterLink}
          to={profile}
          aria-label={`${post.author.displayName}さんのプロフィール`}
        >
          <UserAvatar user={post.author} />
        </Link>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'baseline', minWidth: 0 }}>
            <Link
              component={RouterLink}
              to={profile}
              underline="hover"
              color="inherit"
              sx={{ fontWeight: 'bold' }}
              noWrap
            >
              {post.author.displayName}
            </Link>
            <Typography color="text.secondary" noWrap sx={{ flexShrink: 1 }}>
              @{post.author.username}
            </Typography>
            <Typography color="text.secondary" sx={{ flexShrink: 0 }}>
              ・
              <Link component={RouterLink} to={detailPath} underline="hover" color="inherit">
                <time dateTime={post.createdAt} title={formatFullTime(post.createdAt)}>
                  {detail ? formatFullTime(post.createdAt) : formatPostTime(post.createdAt)}
                </time>
              </Link>
              {post.editedAt && '・編集済み'}
            </Typography>
            <PostMenu post={post} onDeleted={onDeleted} />
          </Stack>
          {post.body && (
            <Typography
              ref={bodyRef}
              data-testid="post-body"
              sx={{
                whiteSpace: 'pre-wrap', // 改行をそのまま表示する
                overflowWrap: 'anywhere', // 長い英数字が枠からはみ出さないようにする
                // 一覧では 10 行で切る。詳細では全文を出す
                ...(detail
                  ? { fontSize: 18 }
                  : {
                      display: '-webkit-box',
                      WebkitLineClamp: MAX_LINES,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                    }),
              }}
            >
              {post.body}
            </Typography>
          )}
          {clamped && !detail && (
            <Link component={RouterLink} to={detailPath} variant="body2">
              続きを読む
            </Link>
          )}
          <PostImages images={post.images} />
          {detail && (
            // いいねの数を押したら、いいねした人の一覧（SC-09）へ
            <Link
              component={RouterLink}
              to={`${detailPath}/likes`}
              underline="hover"
              color="inherit"
              variant="body2"
              sx={{ display: 'inline-block', mt: 1 }}
            >
              <Box component="span" sx={{ fontWeight: 'bold' }}>
                {formatCount(post.likeCount)}
              </Box>{' '}
              件のいいね
            </Link>
          )}
          <Stack direction="row" spacing={3} sx={{ mt: 0.5, ml: -1 }}>
            <Button
              component={RouterLink}
              to={detailPath}
              size="small"
              startIcon={<ChatBubbleOutline />}
              aria-label={`コメント（${post.commentCount}）`}
              sx={{ minWidth: 0, color: 'text.secondary' }}
            >
              {formatCount(post.commentCount)}
            </Button>
            <LikeButton post={post} />
          </Stack>
        </Box>
      </Stack>
    </Box>
  )
}
