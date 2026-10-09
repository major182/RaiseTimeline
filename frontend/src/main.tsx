import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import './index.css'
import App from './App.tsx'

// Material UI のテーマ。色や文字の大きさは画面設計で決めてから、ここで設定する
const theme = createTheme()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      {/* ブラウザごとの見た目の差をなくす、Material UI 標準の下地の CSS */}
      <CssBaseline />
      <App />
    </ThemeProvider>
  </StrictMode>,
)
