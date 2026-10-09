import { Visibility, VisibilityOff } from '@mui/icons-material'
import { IconButton, InputAdornment, TextField, type TextFieldProps } from '@mui/material'
import { useState } from 'react'

/** パスワードの入力欄。右端のボタンで、入力した文字の表示・非表示を切り替えられる（画面設計書 5.1・5.2）。 */
export function PasswordField(props: TextFieldProps) {
  const [visible, setVisible] = useState(false)
  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      slotProps={{
        input: {
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={visible ? 'パスワードを隠す' : 'パスワードを表示'}
                onClick={() => setVisible((v) => !v)}
                edge="end"
              >
                {visible ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  )
}
