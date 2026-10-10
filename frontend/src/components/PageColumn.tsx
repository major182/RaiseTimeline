import ArrowBack from '@mui/icons-material/ArrowBack'
import { Box, IconButton, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'

type Props = {
  /** 上の帯の画面の名前。 */
  title: ReactNode
  /** 名前の下に小さく出す文字（@ユーザー名など）。 */
  subtitle?: ReactNode
  /** 戻るボタンを出すか。 */
  back?: boolean
  /** 上の帯の下に続けて出すもの（タブなど）。 */
  headerExtra?: ReactNode
  children: ReactNode
}

/**
 * 画面の真ん中の列（幅 600px。画面設計書 1.1）と、上に貼り付く帯（画面の名前・戻る）。
 * 戻るボタンは、アプリの中から来たならひとつ前の画面へ、URL を直接開いたならホームへ戻る。
 */
export function PageColumn({ title, subtitle, back = true, headerExtra, children }: Props) {
  const navigate = useNavigate()
  const location = useLocation()
  // 最初に開いた画面の key は 'default'（戻る先がアプリの外になる）
  const goBack = () => (location.key === 'default' ? navigate('/') : navigate(-1))

  return (
    <Box
      sx={{
        width: '100%',
        maxWidth: 600,
        minHeight: '100vh',
        mx: 'auto',
        borderLeft: { sm: 1 },
        borderRight: { sm: 1 },
        borderColor: { sm: 'divider' },
      }}
    >
      <Box
        sx={{
          position: 'sticky',
          top: 0,
          zIndex: 2,
          bgcolor: 'background.paper',
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', px: 1, minHeight: 53 }}>
          {back && (
            <IconButton aria-label="戻る" onClick={() => void goBack()}>
              <ArrowBack />
            </IconButton>
          )}
          <Box sx={{ minWidth: 0, pl: back ? 0 : 1 }}>
            <Typography variant="h6" component="h1" noWrap sx={{ lineHeight: 1.3 }}>
              {title}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="text.secondary" noWrap>
                {subtitle}
              </Typography>
            )}
          </Box>
        </Stack>
        {headerExtra}
      </Box>
      {children}
    </Box>
  )
}

/** 一覧が空のとき・見つからないときの文言（画面設計書 6.5）。 */
export function EmptyMessage({ children }: { children: ReactNode }) {
  return <Box sx={{ textAlign: 'center', px: 2, py: 6, color: 'text.secondary' }}>{children}</Box>
}
