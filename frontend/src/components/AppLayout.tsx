import Add from '@mui/icons-material/Add'
import Home from '@mui/icons-material/Home'
import Person from '@mui/icons-material/PersonOutlined'
import Settings from '@mui/icons-material/SettingsOutlined'
import {
  BottomNavigation,
  BottomNavigationAction,
  Box,
  Button,
  ButtonBase,
  Drawer,
  Fab,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Outlet, Link as RouterLink, useLocation, useNavigationType } from 'react-router'
import { useMe } from '../auth/useMe'
import { QUERY_KEYS } from '../queryCache'
import { PostComposer } from './PostComposer'
import { UserAvatar } from './UserAvatar'

/** PC のナビの幅。 */
const NAV_WIDTH = 240

type NavItem = { label: string; to: string; icon: ReactNode; selected: boolean }

/** path が base そのものか、base の下の画面か（大文字・小文字は区別しない。ユーザー名のため）。 */
function isUnder(path: string, base: string): boolean {
  const p = path.toLowerCase()
  const b = base.toLowerCase()
  return p === b || p.startsWith(`${b}/`)
}

/**
 * ログイン後の画面の枠（画面設計書 1.1）。
 * PC（幅 900px 以上）は左にナビ、スマホは下にナビを出し、真ん中に画面ごとの内容（Outlet）を出す。
 */
export function AppLayout() {
  const theme = useTheme()
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'))
  const location = useLocation()
  const queryClient = useQueryClient()
  const { data: me } = useMe()
  const [composing, setComposing] = useState(false)
  const navigationType = useNavigationType()
  const path = location.pathname

  // 別の画面へ移ったら、一番上から表示する。ブラウザの戻る・進む（POP）は、ブラウザが元の位置に戻すので触らない
  useEffect(() => {
    if (navigationType !== 'POP') window.scrollTo({ top: 0 })
  }, [path, navigationType])

  /** ホームにいるときに「ホーム」を押したら、一番上に戻ってタイムラインを読み込み直す（F-TL-04）。 */
  const onHome = () => {
    if (path !== '/') return
    window.scrollTo({ top: 0 })
    void queryClient.resetQueries({ queryKey: QUERY_KEYS.timeline })
  }

  const myProfile = me ? `/users/${me.username}` : undefined
  const items: NavItem[] = [
    { label: 'ホーム', to: '/', icon: <Home />, selected: path === '/' },
    ...(myProfile
      ? [
          {
            label: 'プロフィール',
            to: myProfile,
            icon: <Person />,
            selected: isUnder(path, myProfile),
          },
        ]
      : []),
    // プロフィールの編集（/settings/profile）も設定の中とみなす
    { label: '設定', to: '/settings', icon: <Settings />, selected: isUnder(path, '/settings') },
  ]
  const onClick = (item: NavItem) => (item.to === '/' ? onHome : undefined)

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {isDesktop && (
        <Drawer
          variant="permanent"
          sx={{ width: NAV_WIDTH, flexShrink: 0, '& .MuiDrawer-paper': { width: NAV_WIDTH } }}
        >
          <Typography
            variant="h6"
            component="div"
            color="primary"
            sx={{ px: 3, py: 2, fontWeight: 'bold' }}
          >
            RaiseTimeline
          </Typography>
          <List component="nav" aria-label="メインのナビ">
            {items.map((item) => (
              <ListItemButton
                key={item.to}
                component={RouterLink}
                to={item.to}
                selected={item.selected}
                onClick={onClick(item)}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
          </List>
          <Box sx={{ px: 2, mt: 1 }}>
            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={() => setComposing(true)}
              sx={{ borderRadius: 6 }}
            >
              投稿する
            </Button>
          </Box>
          {me && myProfile && (
            // 自分のアイコンと名前。押すと自分のプロフィールへ
            <ButtonBase
              component={RouterLink}
              to={myProfile}
              aria-label="自分のプロフィール"
              sx={{
                mt: 'auto',
                mx: 2,
                mb: 2,
                p: 1,
                borderRadius: 8,
                justifyContent: 'flex-start',
                gap: 1.5,
              }}
            >
              <UserAvatar user={me} />
              <Box sx={{ minWidth: 0, textAlign: 'left' }}>
                <Typography noWrap sx={{ fontWeight: 'bold' }}>
                  {me.displayName}
                </Typography>
                <Typography color="text.secondary" variant="body2" noWrap>
                  @{me.username}
                </Typography>
              </Box>
            </ButtonBase>
          )}
        </Drawer>
      )}

      <Box component="main" sx={{ flex: 1, minWidth: 0, pb: isDesktop ? 0 : 7 }}>
        <Outlet />
      </Box>

      {!isDesktop && (
        // スマホの投稿ボタン。下のナビの上に浮かべる
        <Fab
          color="primary"
          aria-label="投稿する"
          onClick={() => setComposing(true)}
          sx={{ position: 'fixed', right: 16, bottom: 72, zIndex: 'appBar' }}
        >
          <Add />
        </Fab>
      )}
      {!isDesktop && (
        <Paper
          sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 'appBar' }}
          elevation={3}
        >
          <BottomNavigation
            component="nav"
            aria-label="メインのナビ"
            showLabels
            value={items.find((i) => i.selected)?.to ?? false}
          >
            {items.map((item) => (
              <BottomNavigationAction
                key={item.to}
                label={item.label}
                value={item.to}
                icon={item.icon}
                component={RouterLink}
                to={item.to}
                onClick={onClick(item)}
              />
            ))}
          </BottomNavigation>
        </Paper>
      )}

      <PostComposer open={composing} onClose={() => setComposing(false)} />
    </Box>
  )
}
