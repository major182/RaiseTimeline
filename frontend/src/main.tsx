import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router'
import { App } from './App'
import { SessionExpiryHandler } from './auth/SessionExpiryHandler'
import { createQueryClient } from './auth/session'
import { SnackbarProvider } from './components/SnackbarProvider'

// Material UI のテーマ。色や文字の大きさは画面設計で決めてから、ここで設定する
const theme = createTheme()

// サーバーのデータの取得・キャッシュ（TanStack Query）。ログインの期限切れの扱いもここで決める
const queryClient = createQueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      {/* ブラウザごとの見た目の差をなくす、Material UI 標準の下地の CSS */}
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <SnackbarProvider>
          <SessionExpiryHandler />
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </SnackbarProvider>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
)
