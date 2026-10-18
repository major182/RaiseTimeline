import MoreVert from '@mui/icons-material/MoreVert'
import { Box, Button, IconButton, Link, Menu, MenuItem, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import type { Comment } from '../api/types'
import { formatFullTime, formatPostTime } from '../format'
import { ConfirmDialog } from './ConfirmDialog'
import { MentionText } from './MentionText'
import { UserAvatar } from './UserAvatar'

type Props = {
  comment: Comment
  onDelete: (comment: Comment) => void
  /** 「返信」を押した（F-CM-05）。 */
  onReply: (comment: Comment) => void
}

/**
 * コメント・返信の行（画面設計書 5.7）。アイコン・表示名・ユーザー名・日時・本文・「返信」ボタン。
 * 削除のメニューは、書いた本人か投稿した本人にだけ出す（BR-35。サーバーが deletable で知らせる）。
 * 返信は、並べる側（CommentThread）が字下げする。
 */
export function CommentItem({ comment, onDelete, onReply }: Props) {
  const isReply = comment.parentId !== null
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const [confirming, setConfirming] = useState(false)
  const profile = `/users/${comment.author.username}`

  return (
    <Box
      component="article"
      aria-label={`${comment.author.displayName}さんの${isReply ? '返信' : 'コメント'}`}
      sx={{ py: 1.5 }}
    >
      <Stack direction="row" spacing={1.5}>
        <Link
          component={RouterLink}
          to={profile}
          aria-label={`${comment.author.displayName}さんのプロフィール`}
        >
          <UserAvatar user={comment.author} />
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
              {comment.author.displayName}
            </Link>
            <Typography color="text.secondary" noWrap sx={{ flexShrink: 1 }}>
              @{comment.author.username}
            </Typography>
            <Typography color="text.secondary" sx={{ flexShrink: 0 }}>
              ・
              <time dateTime={comment.createdAt} title={formatFullTime(comment.createdAt)}>
                {formatPostTime(comment.createdAt)}
              </time>
            </Typography>
            {comment.deletable && (
              <IconButton
                size="small"
                aria-label={isReply ? '返信のメニュー' : 'コメントのメニュー'}
                onClick={(e) => setAnchor(e.currentTarget)}
                sx={{ ml: 'auto', mt: -0.5 }}
              >
                <MoreVert fontSize="small" />
              </IconButton>
            )}
          </Stack>
          <Typography
            data-testid="comment-body"
            sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}
          >
            <MentionText text={comment.body} />
          </Typography>
          <Button
            size="small"
            color="inherit"
            onClick={() => onReply(comment)}
            sx={{ ml: -1, mt: 0.5, minWidth: 0, color: 'text.secondary' }}
          >
            返信
          </Button>
        </Box>
      </Stack>
      <Menu anchorEl={anchor} open={anchor !== null} onClose={() => setAnchor(null)}>
        <MenuItem
          onClick={() => {
            setAnchor(null)
            setConfirming(true)
          }}
          sx={{ color: 'error.main' }}
        >
          削除
        </MenuItem>
      </Menu>
      <ConfirmDialog
        open={confirming}
        // 返信があるコメントは C-05、ほかは（返信も）C-02（BR-38）
        title="コメントを削除しますか？"
        body={
          comment.replyCount > 0
            ? 'この操作は取り消せません。返信も削除されます。'
            : 'この操作は取り消せません。'
        }
        confirmLabel="削除"
        danger
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          onDelete(comment)
        }}
      />
    </Box>
  )
}
