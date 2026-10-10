import Close from '@mui/icons-material/Close'
import { Box, IconButton } from '@mui/material'

export type Attachment = { id: number; file: File; previewUrl: string }

type Props = { attachments: Attachment[]; onRemove: (id: number) => void }

/** 添付する画像のプレビュー（画面設計書 5.4）。右上の「×」で外す。 */
export function AttachedImages({ attachments, onRemove }: Props) {
  if (attachments.length === 0) return null
  return (
    <Box
      data-testid="attached-images"
      sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1, mt: 1 }}
    >
      {attachments.map((a, i) => (
        <Box key={a.id} sx={{ position: 'relative' }}>
          <Box
            component="img"
            src={a.previewUrl}
            alt={`添付する画像 ${i + 1}`}
            sx={{
              width: '100%',
              aspectRatio: '16 / 9',
              objectFit: 'cover',
              display: 'block',
              borderRadius: 2,
            }}
          />
          <IconButton
            size="small"
            aria-label={`画像 ${i + 1} を外す`}
            onClick={() => onRemove(a.id)}
            sx={{
              position: 'absolute',
              top: 4,
              right: 4,
              color: 'common.white',
              bgcolor: 'rgba(0, 0, 0, 0.6)',
              '&:hover': { bgcolor: 'rgba(0, 0, 0, 0.8)' },
            }}
          >
            <Close fontSize="small" />
          </IconButton>
        </Box>
      ))}
    </Box>
  )
}
