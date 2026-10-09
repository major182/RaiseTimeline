import { Box } from '@mui/material'
import type { PostImage } from '../api/types'

/**
 * 投稿の画像の並べ方（画面設計書 4.1）。
 * 1 枚は横いっぱい、2 枚は左右、3 枚は左 1・右 2、4 枚は 2×2。
 * 押したときの拡大表示（4.3）は、投稿の詳細の Issue で作る。
 */
export function PostImages({ images }: { images: PostImage[] }) {
  if (images.length === 0) return null
  const count = images.length
  // 1 枚のときは元の縦横の比で出す（縦に長すぎる画像は 4:5 で切る）。読み込み前に枠の大きさを決め、表示のずれを防ぐ
  const ratio =
    count === 1
      ? `${images[0].width} / ${Math.min(images[0].height, images[0].width * 1.25)}`
      : '16 / 9'
  return (
    <Box
      data-testid="post-images"
      data-count={count}
      sx={{
        display: 'grid',
        gap: '2px',
        mt: 1,
        borderRadius: 2,
        overflow: 'hidden',
        border: 1,
        borderColor: 'divider',
        aspectRatio: ratio,
        gridTemplateColumns: count === 1 ? '1fr' : '1fr 1fr',
        gridTemplateRows: count <= 2 ? '1fr' : '1fr 1fr',
      }}
    >
      {images.map((image, i) => (
        <Box
          key={image.url}
          component="img"
          src={image.url}
          alt={`画像 ${i + 1}/${count}`}
          loading="lazy"
          sx={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            display: 'block',
            // 3 枚のときは、1 枚目を左の列いっぱいに広げる
            gridRow: count === 3 && i === 0 ? 'span 2' : undefined,
          }}
        />
      ))}
    </Box>
  )
}
