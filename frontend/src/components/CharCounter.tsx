import { Box, CircularProgress, Typography } from '@mui/material'
import { countChars } from '../postLength'

type Props = { text: string; max: number }

/** 残りが何文字から数を出すか（画面設計書 1.5）。 */
const WARN_FROM = 20

/**
 * 文字数のカウンター（画面設計書 1.5）。
 * 残り 21 文字以上は円のゲージだけ、残り 20〜0 文字は黄色で残りの数、超えたら赤で「-5」のように出す。
 */
export function CharCounter({ text, max }: Props) {
  const remaining = max - countChars(text)
  const over = remaining < 0
  const warn = remaining <= WARN_FROM
  const color = over ? 'error' : warn ? 'warning' : 'primary'
  const value = Math.min(100, ((max - remaining) / max) * 100)
  return (
    <Box
      role="status"
      aria-label={over ? `${-remaining} 文字超えています` : `残り ${remaining} 文字`}
      sx={{ display: 'flex', alignItems: 'center', gap: 1 }}
    >
      {warn && (
        <Typography
          variant="body2"
          color={over ? 'error' : 'warning.main'}
          data-testid="char-remaining"
        >
          {remaining}
        </Typography>
      )}
      <CircularProgress variant="determinate" value={value} size={24} thickness={5} color={color} />
    </Box>
  )
}
