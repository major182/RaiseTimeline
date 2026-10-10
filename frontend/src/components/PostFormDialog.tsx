import Close from '@mui/icons-material/Close'
import {
  Box,
  Button,
  Dialog,
  DialogContent,
  IconButton,
  Stack,
  TextField,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { useRef, useState, type ReactNode } from 'react'
import { useMe } from '../auth/useMe'
import { BODY_MAX_LENGTH, countChars } from '../postLength'
import { CharCounter } from './CharCounter'
import { ConfirmDialog } from './ConfirmDialog'
import { UserAvatar } from './UserAvatar'

type Props = {
  open: boolean
  /** ダイアログの名前（読み上げ用）。 */
  title: string
  body: string
  onBodyChange: (body: string) => void
  /** 送るボタンの文言。 */
  submitLabel: string
  /** 送るボタンを押せるか（文字数の超過は、この部品が別に確かめる）。 */
  canSubmit: boolean
  submitting: boolean
  onSubmit: () => void
  /** 閉じる前に確認するか（入力の途中なら true）。 */
  dirty: boolean
  /** 閉じる（確認のあと、または入力がないとき）。 */
  onClose: () => void
  /** 入力欄の下の誤り（サーバーから返った E-13 など）。 */
  error?: string
  /** 入力欄の下に出すもの（画像など）。 */
  children?: ReactNode
  /** 下の段の左に出すもの（画像の添付のボタンなど）。 */
  tools?: ReactNode
}

/**
 * 投稿作成（MD-01）・投稿編集（MD-02）のモーダルの共通の形（画面設計書 5.4・5.5）。
 * 本文の入力欄・文字数のカウンター（1.5）・入力の途中で閉じるときの確認（C-03）を持つ。スマホでは全画面にする。
 */
export function PostFormDialog({
  open,
  title,
  body,
  onBodyChange,
  submitLabel,
  canSubmit,
  submitting,
  onSubmit,
  dirty,
  onClose,
  error,
  children,
  tools,
}: Props) {
  const theme = useTheme()
  const fullScreen = useMediaQuery(theme.breakpoints.down('md'))
  const { data: me } = useMe()
  const [confirming, setConfirming] = useState(false)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  /** 入力欄に焦点を移し、カーソルを本文の末尾に置く（編集で続きを書きやすいように）。 */
  const focusAtEnd = () => {
    const input = inputRef.current
    if (!input) return
    input.focus()
    input.setSelectionRange(input.value.length, input.value.length)
  }
  const over = countChars(body) > BODY_MAX_LENGTH

  const requestClose = () => {
    if (submitting) return
    if (dirty) setConfirming(true)
    else onClose()
  }

  return (
    <>
      <Dialog
        open={open}
        onClose={requestClose}
        fullScreen={fullScreen}
        fullWidth
        maxWidth="sm"
        slotProps={{
          paper: { 'aria-label': title },
          // 開き終わったら入力欄に焦点を移す（開いたらすぐ入力できるように。画面設計書 5.4・5.5）。
          // autoFocus だけだと、ダイアログが焦点を自分の枠へ移してしまう
          transition: { onEntered: focusAtEnd },
        }}
      >
        <Stack
          direction="row"
          sx={{ alignItems: 'center', justifyContent: 'space-between', px: 1, pt: 1 }}
        >
          <IconButton aria-label="閉じる" onClick={requestClose}>
            <Close />
          </IconButton>
          <Button
            variant="contained"
            onClick={onSubmit}
            disabled={!canSubmit || over || submitting}
            sx={{ borderRadius: 5, mr: 1 }}
          >
            {submitLabel}
          </Button>
        </Stack>
        <DialogContent sx={{ pt: 1 }}>
          <Stack direction="row" spacing={1.5}>
            {me && <UserAvatar user={me} />}
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <TextField
                value={body}
                onChange={(e) => onBodyChange(e.target.value)}
                placeholder="いまどうしてる？"
                multiline
                minRows={3}
                fullWidth
                autoFocus
                inputRef={inputRef}
                variant="standard"
                error={Boolean(error)}
                helperText={error}
                slotProps={{
                  input: { disableUnderline: true, sx: { fontSize: 20 } },
                  htmlInput: { 'aria-label': '本文' },
                }}
              />
              {children}
            </Box>
          </Stack>
          <Stack
            direction="row"
            sx={{ alignItems: 'center', justifyContent: 'space-between', mt: 2 }}
          >
            <Box>{tools}</Box>
            <CharCounter text={body} max={BODY_MAX_LENGTH} />
          </Stack>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={confirming}
        title="編集内容を破棄しますか？"
        body="入力した内容は保存されません。"
        confirmLabel="破棄"
        danger
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          onClose()
        }}
      />
    </>
  )
}
