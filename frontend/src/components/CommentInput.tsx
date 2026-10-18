import { Box, Button, Stack } from '@mui/material'
import { useEffect, useRef } from 'react'
import { BODY_MAX_LENGTH, countChars } from '../postLength'
import { CharCounter } from './CharCounter'
import { MentionTextField } from './MentionTextField'

type Props = {
  value: string
  onChange: (value: string) => void
  /** フォームの名前（読み上げ用）。 */
  formLabel: string
  /** 入力欄の名前（読み上げ用）。 */
  label: string
  placeholder: string
  submitLabel: string
  submitting: boolean
  onSubmit: () => void
  /** 入力欄の下に出す誤り（E-13 など）。 */
  error?: string
  /** あれば「キャンセル」ボタンを出す（返信の入力欄）。 */
  onCancel?: () => void
  /** 開いたら入力できる状態にし、カーソルを最後に置く（返信先の @ユーザー名 の後ろから打てるように）。 */
  autoFocus?: boolean
}

/**
 * コメント・返信の入力欄（画面設計書 5.7）。文字数のカウンター（1.5）とメンションの候補（4.5）つき。
 * 空か空白だけ・280 文字を超えたら送れない（サーバーと同じ。BR-30）。
 */
export function CommentInput({
  value,
  onChange,
  formLabel,
  label,
  placeholder,
  submitLabel,
  submitting,
  onSubmit,
  error,
  onCancel,
  autoFocus,
}: Props) {
  const over = countChars(value) > BODY_MAX_LENGTH
  const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement>(null)

  useEffect(() => {
    const input = inputRef.current
    if (!autoFocus || !input) return
    input.focus()
    input.setSelectionRange(input.value.length, input.value.length)
    // 開いたときだけ行う
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <Box
      component="form"
      aria-label={formLabel}
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit()
      }}
      sx={{ flex: 1, minWidth: 0 }}
    >
      <MentionTextField
        value={value}
        onValueChange={onChange}
        placeholder={placeholder}
        multiline
        fullWidth
        inputRef={inputRef}
        variant="standard"
        error={Boolean(error)}
        helperText={error}
        slotProps={{ htmlInput: { 'aria-label': label } }}
      />
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: 'center', justifyContent: 'flex-end', mt: 1 }}
      >
        {value !== '' && <CharCounter text={value} max={BODY_MAX_LENGTH} />}
        {onCancel && (
          <Button onClick={onCancel} color="inherit" sx={{ borderRadius: 5 }}>
            キャンセル
          </Button>
        )}
        <Button
          type="submit"
          variant="contained"
          disabled={value.trim() === '' || over || submitting}
          sx={{ borderRadius: 5 }}
        >
          {submitLabel}
        </Button>
      </Stack>
    </Box>
  )
}
