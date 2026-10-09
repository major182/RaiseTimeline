import ChatBubbleOutline from '@mui/icons-material/ChatBubbleOutlineOutlined'
import FavoriteBorder from '@mui/icons-material/FavoriteBorder'
import { Box, Button, Link, Stack, Typography } from '@mui/material'
import { useLayoutEffect, useRef, useState } from 'react'
import { Link as RouterLink } from 'react-router'
import type { Post } from '../api/types'
import { formatCount, formatFullTime, formatPostTime, likedViaText } from '../format'
import { LikeButton } from './LikeButton'
import { PostImages } from './PostImages'
import { PostMenu } from './PostMenu'
import { UserAvatar } from './UserAvatar'

/** 一覧で本文を何行まで出すか（それを超えたら「続きを読む」）。 */
const MAX_LINES = 10

/**
 * 投稿カード（画面設計書 4.1 PC-01）。タイムライン・投稿の詳細・プロフィールで使う。
 * インプレッション数・リツイートのボタンは置かない（BR-40、BR-41）。
 * 利用者の文字（本文・表示名）は {text} で表示し、HTML として扱わない（XSS の対策。技術選定書 4.7）。
 */
export function PostCard({ post }: { post: Post }) {
  const profile = `/users/${post.author.username}`
  const detail = `/posts/${post.id}`
  const bodyRef = useRef<HTMLParagraphElement>(null)
  const [clamped, setClamped] = useState(false)

  // 本文が 10 行を超えて切られているかを、表示した大きさで調べる
  useLayoutEffect(() => {
    const el = bodyRef.current
    if (el) setClamped(el.scrollHeight > el.clientHeight + 1)
  }, [post.body])

  return (
    <Box
      component="article"
      aria-label={`${post.author.displayName}さんの投稿`}
      sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
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
              <Link component={RouterLink} to={detail} underline="hover" color="inherit">
                <time dateTime={post.createdAt} title={formatFullTime(post.createdAt)}>
                  {formatPostTime(post.createdAt)}
                </time>
              </Link>
              {post.editedAt && '・編集済み'}
            </Typography>
            <PostMenu post={post} />
          </Stack>
          {post.body && (
            <Typography
              ref={bodyRef}
              data-testid="post-body"
              sx={{
                whiteSpace: 'pre-wrap', // 改行をそのまま表示する
                overflowWrap: 'anywhere', // 長い英数字が枠からはみ出さないようにする
                display: '-webkit-box',
                WebkitLineClamp: MAX_LINES,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {post.body}
            </Typography>
          )}
          {clamped && (
            <Link component={RouterLink} to={detail} variant="body2">
              続きを読む
            </Link>
          )}
          <PostImages images={post.images} />
          <Stack direction="row" spacing={3} sx={{ mt: 0.5, ml: -1 }}>
            <Button
              component={RouterLink}
              to={detail}
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
