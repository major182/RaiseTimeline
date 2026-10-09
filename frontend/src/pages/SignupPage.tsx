import { Alert, Box, Button, InputAdornment, Link, Stack, TextField } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router'
import { signup, type SignupInput } from '../api/auth'
import { ApiError } from '../api/client'
import { ME_QUERY_KEY } from '../auth/useMe'
import { validateSignup, type SignupErrors } from '../auth/validation'
import { AuthLayout } from '../components/AuthLayout'
import { PasswordField } from '../components/PasswordField'
import { useNotify } from '../components/SnackbarProvider'

const FIELDS: (keyof SignupInput)[] = ['email', 'password', 'passwordConfirmation', 'username']

/** SC-02 利用者登録（画面設計書 5.2）。表示名は入力させず、ユーザー名と同じ値で登録される（BR-01）。 */
export function SignupPage() {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [form, setForm] = useState<SignupInput>({
    email: '',
    password: '',
    passwordConfirmation: '',
    username: '',
  })
  const [errors, setErrors] = useState<SignupErrors>({})

  const mutation = useMutation({
    mutationFn: signup,
    onSuccess: (me) => {
      notify('ようこそ、RaiseTimeline へ') // I-01
      // ログインしている利用者を書き換えると、PublicOnly がホームへ移動させる
      queryClient.setQueryData(ME_QUERY_KEY, me)
    },
    onError: (error) => {
      // サーバーの誤り（使われている：E-03・E-07 など）は、該当の入力欄の下に出す
      if (error instanceof ApiError && error.errors.length > 0) {
        setErrors(Object.fromEntries(FIELDS.map((f) => [f, error.fieldMessage(f)])))
      }
    },
  })

  // 入力欄に結び付かない失敗（通信の失敗など）は、入力欄の上に出す
  const alert =
    mutation.error instanceof ApiError && mutation.error.errors.length === 0
      ? mutation.error.message
      : undefined

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const next = validateSignup(form)
    setErrors(next)
    if (Object.keys(next).length > 0) {
      mutation.reset()
      return
    }
    mutation.mutate({ ...form, email: form.email.trim() })
  }

  const fieldProps = (name: keyof SignupInput) => ({
    value: form[name],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm({ ...form, [name]: e.target.value }),
    error: Boolean(errors[name]),
    fullWidth: true,
  })

  return (
    <AuthLayout title="アカウントを作成">
      <Box component="form" noValidate onSubmit={handleSubmit}>
        <Stack spacing={2.5}>
          {alert && <Alert severity="error">{alert}</Alert>}
          <TextField
            label="メールアドレス"
            type="email"
            autoComplete="email"
            helperText={errors.email}
            {...fieldProps('email')}
          />
          <PasswordField
            label="パスワード"
            autoComplete="new-password"
            helperText={errors.password ?? '8〜72 文字。英字と数字をそれぞれ 1 文字以上'}
            {...fieldProps('password')}
          />
          <PasswordField
            label="パスワード（確認）"
            autoComplete="new-password"
            helperText={errors.passwordConfirmation}
            {...fieldProps('passwordConfirmation')}
          />
          <TextField
            label="ユーザー名"
            autoComplete="username"
            helperText={errors.username ?? '4〜15 文字の半角英数字と「_」。後から変更できます'}
            slotProps={{
              input: { startAdornment: <InputAdornment position="start">@</InputAdornment> },
            }}
            {...fieldProps('username')}
          />
          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={mutation.isPending}
            fullWidth
          >
            登録
          </Button>
          <Box sx={{ textAlign: 'center' }}>
            <Link component={RouterLink} to="/login">
              ログインはこちら
            </Link>
          </Box>
        </Stack>
      </Box>
    </AuthLayout>
  )
}
