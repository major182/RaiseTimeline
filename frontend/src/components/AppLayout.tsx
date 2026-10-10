import Add from '@mui/icons-material/Add'
import Home from '@mui/icons-material/Home'
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
  Menu,
  MenuItem,
  Paper,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Outlet, Link as RouterLink, useLocation, useNavigationType } from 'react-router'
import { logout } from '../api/auth'
import { ME_QUERY_KEY, useMe } from '../auth/useMe'
import { QUERY_KEYS } from '../queryCache'
import { ConfirmDialog } from './ConfirmDialog'
import { PostComposer } from './PostComposer'
import { useNotify } from './SnackbarProvider'
import { UserAvatar } from './UserAvatar'

/** PC のナビの幅。 */
const NAV_WIDTH = 240

/**
 * ログイン後の画面の枠（画面設計書 1.1）。
 * PC（幅 900px 以上）は左にナビ、スマホは下にナビを出し、真ん中に画面ごとの内容（Outlet）を出す。
 * ナビの項目は、画面ができたものから足していく。ログアウトは、設定（SC-10）ができるまで自分のアイコンのメニューに置く。
 */
export function AppLayout() {
  const theme = useTheme()
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'))
  const location = useLocation()
  const queryClient = useQueryClient()
  const { data: me } = useMe()
  const notify = useNotify()
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [composing, setComposing] = useState(false)
  const navigationType = useNavigationType()

  // 別の画面へ移ったら、一番上から表示する。ブラウザの戻る・進む（POP）は、ブラウザが元の位置に戻すので触らない
  useEffect(() => {
    if (navigationType !== 'POP') window.scrollTo({ top: 0 })
  }, [location.pathname, navigationType])

  const logoutMutation = useMutation({
    mutationFn: logout,
    // 「ログインしていない」状態にすると、RequireAuth がログイン画面へ移動させる
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['posts'] })
      queryClient.removeQueries({ queryKey: ['users'] })
      queryClient.setQueryData(ME_QUERY_KEY, null)
    },
    onError: (error) => notify(error.message, 'error'),
  })

  /** ホームにいるときに「ホーム」を押したら、一番上に戻ってタイムラインを読み込み直す（F-TL-04）。 */
  const onHome = () => {
    if (location.pathname !== '/') return
    window.scrollTo({ top: 0 })
    void queryClient.resetQueries({ queryKey: QUERY_KEYS.timeline })
  }

  const openMenu = (e: React.MouseEvent<HTMLElement>) => setMenuAnchor(e.currentTarget)
  const accountMenu = (
    <Menu anchorEl={menuAnchor} open={menuAnchor !== null} onClose={() => setMenuAnchor(null)}>
      <MenuItem
        onClick={() => {
          setMenuAnchor(null)
          setConfirming(true)
        }}
        disabled={logoutMutation.isPending}
      >
        ログアウト
      </MenuItem>
    </Menu>
  )

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
            <ListItemButton
              component={RouterLink}
              to="/"
              selected={location.pathname === '/'}
              onClick={onHome}
            >
              <ListItemIcon>
                <Home />
              </ListItemIcon>
              <ListItemText primary="ホーム" />
            </ListItemButton>
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
          {me && (
            <ButtonBase
              onClick={openMenu}
              aria-label="アカウントのメニュー"
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
            value={location.pathname}
          >
            <BottomNavigationAction
              label="ホーム"
              value="/"
              icon={<Home />}
              component={RouterLink}
              to="/"
              onClick={onHome}
            />
            {me && (
              <BottomNavigationAction
                label="アカウント"
                value="account"
                icon={<UserAvatar user={me} size={24} />}
                onClick={openMenu}
                aria-label="アカウントのメニュー"
              />
            )}
          </BottomNavigation>
        </Paper>
      )}

      {accountMenu}
      <PostComposer open={composing} onClose={() => setComposing(false)} />
      <ConfirmDialog
        open={confirming}
        title="ログアウトしますか？"
        confirmLabel="ログアウト"
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          logoutMutation.mutate()
        }}
      />
    </Box>
  )
}
