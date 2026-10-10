import ChevronLeft from '@mui/icons-material/ChevronLeft'
import ChevronRight from '@mui/icons-material/ChevronRight'
import Close from '@mui/icons-material/Close'
import { Box, Dialog, IconButton } from '@mui/material'
import { useEffect, useRef, useState } from 'react'
import type { PostImage } from '../api/types'

type Props = {
  images: PostImage[]
  /** 最初に出す画像の番号（0 から）。 */
  initialIndex: number
  onClose: () => void
}

/** 横にこれだけ指を動かしたら、前後の画像に切り替える（px）。 */
const SWIPE_DISTANCE = 50

const navButtonSx = {
  position: 'absolute',
  top: '50%',
  transform: 'translateY(-50%)',
  color: 'common.white',
  bgcolor: 'rgba(0, 0, 0, 0.5)',
  '&:hover': { bgcolor: 'rgba(0, 0, 0, 0.7)' },
} as const

/**
 * 画像の拡大表示（画面設計書 4.3 IV-01）。画面全体を暗くし、画像を画面に収まる大きさで出す。
 * 複数枚なら左右の矢印・矢印キー・スワイプで切り替える。「×」・画像の外・Esc で閉じる。
 */
export function ImageViewer({ images, initialIndex, onClose }: Props) {
  const [index, setIndex] = useState(initialIndex)
  const touchStartX = useRef<number | null>(null)
  const count = images.length
  const hasPrev = index > 0
  const hasNext = index < count - 1
  const image = images[index]

  const move = (step: number) => setIndex((i) => Math.min(count - 1, Math.max(0, i + step)))

  // 矢印キーで切り替える。端の矢印のボタンが消えると焦点がダイアログの外に出るので、画面全体で受ける
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const step = e.key === 'ArrowLeft' ? -1 : e.key === 'ArrowRight' ? 1 : 0
      if (step) setIndex((i) => Math.min(count - 1, Math.max(0, i + step)))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [count])

  return (
    <Dialog
      open
      onClose={onClose}
      fullScreen
      slotProps={{
        paper: {
          'aria-label': `画像 ${index + 1}/${count}`,
          sx: { bgcolor: 'rgba(0, 0, 0, 0.9)', boxShadow: 'none' },
        },
      }}
    >
      {/* 画像の外（暗いところ）を押したら閉じる */}
      <Box
        data-testid="image-viewer-backdrop"
        onClick={(e) => {
          if (e.target === e.currentTarget) onClose()
        }}
        onTouchStart={(e) => (touchStartX.current = e.touches[0].clientX)}
        onTouchEnd={(e) => {
          const start = touchStartX.current
          touchStartX.current = null
          if (start === null) return
          const dx = e.changedTouches[0].clientX - start
          if (dx > SWIPE_DISTANCE) move(-1)
          if (dx < -SWIPE_DISTANCE) move(1)
        }}
        sx={{
          position: 'relative',
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          p: { xs: 0, sm: 6 },
        }}
      >
        <Box
          component="img"
          src={image.url}
          alt={`画像 ${index + 1}/${count}`}
          sx={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', display: 'block' }}
        />
        <IconButton
          aria-label="閉じる"
          onClick={onClose}
          sx={{ ...navButtonSx, top: 12, right: 12, transform: 'none' }}
        >
          <Close />
        </IconButton>
        {hasPrev && (
          <IconButton
            aria-label="前の画像"
            onClick={() => move(-1)}
            sx={{ ...navButtonSx, left: 12 }}
          >
            <ChevronLeft />
          </IconButton>
        )}
        {hasNext && (
          <IconButton
            aria-label="次の画像"
            onClick={() => move(1)}
            sx={{ ...navButtonSx, right: 12 }}
          >
            <ChevronRight />
          </IconButton>
        )}
      </Box>
    </Dialog>
  )
}
