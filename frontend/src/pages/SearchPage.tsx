import Search from '@mui/icons-material/Search'
import { Box, InputAdornment, TextField } from '@mui/material'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { searchUsers } from '../api/users'
import { PageColumn } from '../components/PageColumn'
import { RecommendedUsers } from '../components/RecommendedUsers'
import { UserList } from '../components/UserList'
import { countChars } from '../postLength'
import { QUERY_KEYS } from '../queryCache'

/** 入力をやめてから検索するまでの時間（ミリ秒。画面設計書 5.10）。 */
const DEBOUNCE_MS = 300
/** 検索語の長さの上限（サーバーと同じ）。 */
const QUERY_MAX_LENGTH = 50

/**
 * 利用者の検索（画面設計書 5.10 SC-07）。検索語は URL の q に入れ、再読み込みや戻るでも同じ結果を出す。
 * 検索語が空なら、おすすめの利用者を出す（スマホでおすすめを見る場所を兼ねる）。
 */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const [input, setInput] = useState(q)
  // ブラウザの戻る・進むで URL の q が変わったら、入力欄もそろえる（描画の途中で前の値と比べる。React の推奨の書き方）
  const [prevQ, setPrevQ] = useState(q)
  if (q !== prevQ) {
    setPrevQ(q)
    setInput(q)
  }
  const term = q.trim()
  const tooLong = countChars(input.trim()) > QUERY_MAX_LENGTH

  // 入力をやめて 0.3 秒たったら、URL の q を変えて検索する。履歴を増やさないよう置き換える
  useEffect(() => {
    if (input === q || tooLong) return
    const timer = setTimeout(
      () => setParams(input ? { q: input } : {}, { replace: true }),
      DEBOUNCE_MS,
    )
    return () => clearTimeout(timer)
  }, [input, q, tooLong, setParams])

  return (
    <PageColumn
      title="検索"
      back={false}
      headerExtra={
        <Box sx={{ px: 2, pb: 1.5 }}>
          <TextField
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="ユーザー名・表示名で検索"
            fullWidth
            size="small"
            type="search"
            error={tooLong}
            helperText={tooLong ? '1〜50 文字で入力してください' : undefined}
            slotProps={{
              htmlInput: { 'aria-label': '検索' },
              input: {
                sx: { borderRadius: 5 },
                startAdornment: (
                  <InputAdornment position="start">
                    <Search />
                  </InputAdornment>
                ),
              },
            }}
          />
        </Box>
      }
    >
      {term ? (
        <UserList
          key={term}
          queryKey={QUERY_KEYS.search(term)}
          fetchPage={(cursor) => searchUsers(term, cursor)}
          emptyText={`「${term}」に一致するユーザーは見つかりませんでした`} // N-06
        />
      ) : (
        <Box sx={{ p: 2 }}>
          <RecommendedUsers />
        </Box>
      )}
    </PageColumn>
  )
}
