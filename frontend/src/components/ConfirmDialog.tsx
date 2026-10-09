import { Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import { useRef } from 'react'

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
  const cancelRef = useRef<HTMLButtonElement>(null)
  return (
    <Dialog
      open={open}
      onClose={onCancel}
      maxWidth="xs"
      fullWidth
      // 開き終わったら「キャンセル」に焦点を移す。autoFocus だけだと、ダイアログが焦点を自分の枠へ移してしまう
      slotProps={{ transition: { onEntered: () => cancelRef.current?.focus() } }}
    >
      <DialogTitle>{title}</DialogTitle>
      {body && <DialogContent>{body}</DialogContent>}
      <DialogActions>
        <Button ref={cancelRef} onClick={onCancel} variant="outlined" autoFocus>
          キャンセル
        </Button>
        <Button onClick={onConfirm} variant="contained" color={danger ? 'error' : 'primary'}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
