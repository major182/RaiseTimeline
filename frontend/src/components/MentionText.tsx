import { Link } from '@mui/material'
import { Link as RouterLink } from 'react-router'
import { splitMentions } from '../mention'

/**
 * 本文。`@ユーザー名` をプロフィールへのリンクにする（画面設計書 4.4、BR-60）。
 * 文字として組み立て、HTML としては扱わない（NF-SE-05）。
 */
export function MentionText({ text }: { text: string }) {
  return splitMentions(text).map((part, i) =>
    part.type === 'text' ? (
      part.text
    ) : (
      <Link
        key={i}
        component={RouterLink}
        to={`/users/${part.username}`}
        underline="hover"
        // 投稿カードの「押すと投稿の詳細へ移動」を起こさない
        onClick={(e) => e.stopPropagation()}
      >
        @{part.username}
      </Link>
    ),
  )
}
