import { Box, CircularProgress } from '@mui/material'
import { useEffect, useRef, useState, type ReactNode } from 'react'

/** これだけ引っ張って離したら読み込み直す（px。指の動きの半分だけ表示が動く）。 */
export const PULL_THRESHOLD = 64
/** 表示が下がる最大の長さ（px）。 */
const PULL_MAX = 96

type Props = {
  /** 読み込み直す。終わるまでくるくる回る表示を出す。 */
  onRefresh: () => Promise<unknown>
  children: ReactNode
}

/**
 * 一覧の一番上で下に引っ張ると読み込み直す（画面設計書 1.6、F-TL-04）。スマホ向け（指で触ったときだけ動く）。
 * ブラウザ自体の「引っ張って再読み込み」（ページ全体の読み直し）は、この部品を出している間だけ止める。
 */
export function PullToRefresh({ onRefresh, children }: Props) {
  const startY = useRef<number | null>(null)
  const [pull, setPull] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  // 指で動かしている間は、表示を指に合わせてすぐ動かす（離したらなめらかに戻す）
  const [dragging, setDragging] = useState(false)

  useEffect(() => {
    const root = document.documentElement
    const before = root.style.overscrollBehaviorY
    root.style.overscrollBehaviorY = 'contain'
    return () => {
      root.style.overscrollBehaviorY = before
    }
  }, [])

  const end = async () => {
    const pulled = pull
    startY.current = null
    setDragging(false)
    setPull(0)
    if (pulled < PULL_THRESHOLD || refreshing) return
    setRefreshing(true)
    try {
      await onRefresh()
    } finally {
      setRefreshing(false)
    }
  }

  const height = refreshing ? 48 : pull
  return (
    <Box
      data-testid="pull-to-refresh"
      onTouchStart={(e) => {
        // 一番上にいるときだけ始める（途中なら、ふつうのスクロール）
        startY.current = window.scrollY <= 0 && !refreshing ? e.touches[0].clientY : null
        setDragging(startY.current !== null)
      }}
      onTouchMove={(e) => {
        if (startY.current === null) return
        const dy = e.touches[0].clientY - startY.current
        setPull(dy > 0 ? Math.min(PULL_MAX, dy / 2) : 0)
      }}
      onTouchEnd={() => void end()}
      onTouchCancel={() => {
        startY.current = null
        setDragging(false)
        setPull(0)
      }}
    >
      <Box
        sx={{
          height,
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: dragging ? undefined : 'height 0.2s',
        }}
      >
        {refreshing ? (
          <CircularProgress size={28} aria-label="読み込み直し中" />
        ) : (
          pull > 0 && (
            <CircularProgress
              variant="determinate"
              size={28}
              value={Math.min(100, (pull / PULL_THRESHOLD) * 100)}
              aria-label={
                pull >= PULL_THRESHOLD ? '離すと読み込み直します' : '引っ張って読み込み直す'
              }
            />
          )
        )}
      </Box>
      {children}
    </Box>
  )
}
