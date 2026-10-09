import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material'

type Props = {
  open: boolean
  title: string
  body?: string
  confirmLabel: string
  /** 削除など、取り消せない操作なら true（ボタンを赤にする）。 */
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/** 確認のダイアログ（画面設計書 5.6・6.2）。誤って実行しないよう、最初は「キャンセル」を選んだ状態にする。 */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  danger,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <Dialog open={open} onClose={onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      {body && <DialogContent>{body}</DialogContent>}
      <DialogActions>
        <Button onClick={onCancel} autoFocus>
          キャンセル
        </Button>
        <Button onClick={onConfirm} variant="contained" color={danger ? 'error' : 'primary'}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
