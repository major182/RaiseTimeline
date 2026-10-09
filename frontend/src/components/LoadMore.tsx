import { Box, Button, CircularProgress, Typography } from '@mui/material'
import { useEffect, useRef } from 'react'

type Props = {
  hasNext: boolean
  loading: boolean
  onLoadMore: () => void
  /** 全部読み終えたときの文言。 */
  endText?: string
}

/**
 * 一覧の最後に置き、画面に見えたら続きを読み込む（画面設計書 1.6、F-TL-02）。
 * 見えたかどうかは IntersectionObserver（ブラウザの標準の仕組み）で調べる。使えないブラウザでは「さらに読み込む」ボタンを出す。
 */
export function LoadMore({
  hasNext,
  loading,
  onLoadMore,
  endText = 'これ以上の投稿はありません',
}: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const supported = typeof IntersectionObserver !== 'undefined'

  useEffect(() => {
    const el = ref.current
    if (!el || !supported || !hasNext || loading) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) onLoadMore()
      },
      { rootMargin: '400px' }, // 下に着く少し前から読み込み、待ち時間を短くする
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [supported, hasNext, loading, onLoadMore])

  return (
    <Box ref={ref} sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
      {loading ? (
        <CircularProgress size={28} aria-label="続きを読み込み中" />
      ) : hasNext ? (
        !supported && <Button onClick={onLoadMore}>さらに読み込む</Button>
      ) : (
        <Typography color="text.secondary" variant="body2">
          {endText}
        </Typography>
      )}
    </Box>
  )
}
