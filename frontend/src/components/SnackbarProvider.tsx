import { Alert, Snackbar } from '@mui/material'
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

type Severity = 'success' | 'error'
type Notice = { message: string; severity: Severity; key: number }
type Notify = (message: string, severity?: Severity) => void

const SnackbarContext = createContext<Notify>(() => {})

/**
 * 完了・失敗の知らせ（画面設計書 1.2・6.1・6.6）を、画面の下に数秒出す。
 * 画面を移動しても消えないよう、画面の切り替えより外側に置く。
 */
export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [notice, setNotice] = useState<Notice | null>(null)
  const notify = useCallback<Notify>((message, severity = 'success') => {
    setNotice({ message, severity, key: Date.now() })
  }, [])

  return (
    <SnackbarContext.Provider value={notify}>
      {children}
      <Snackbar
        key={notice?.key}
        open={notice !== null}
        autoHideDuration={4000}
        onClose={(_, reason) => reason !== 'clickaway' && setNotice(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {notice ? (
          <Alert severity={notice.severity} variant="filled" onClose={() => setNotice(null)}>
            {notice.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </SnackbarContext.Provider>
  )
}

/** 知らせを出す関数を受け取る。 */
// eslint-disable-next-line react-refresh/only-export-components -- 部品と、その部品の値を使うための関数を同じファイルに置く
export function useNotify(): Notify {
  return useContext(SnackbarContext)
}
