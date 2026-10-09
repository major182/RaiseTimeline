import { Alert, Box, Button, Link, Stack, TextField } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router'
import { login, type LoginInput } from '../api/auth'
import { ApiError } from '../api/client'
import { ME_QUERY_KEY } from '../auth/useMe'
import { AuthLayout } from '../components/AuthLayout'
import { PasswordField } from '../components/PasswordField'
import { MESSAGES } from '../messages'

type Errors = Partial<Record<keyof LoginInput, string>>

/** SC-01 ログイン（画面設計書 5.1）。 */
export function LoginPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<LoginInput>({ email: '', password: '' })
  const [errors, setErrors] = useState<Errors>({})

  const mutation = useMutation({
    mutationFn: login,
    // ログインしている利用者を書き換えると、PublicOnly が元の画面（なければホーム）へ移動させる
    onSuccess: (me) => queryClient.setQueryData(ME_QUERY_KEY, me),
    onError: (error) => {
      if (error instanceof ApiError && error.errors.length > 0) {
        setErrors({ email: error.fieldMessage('email'), password: error.fieldMessage('password') })
      }
    },
  })

  // 失敗（E-20・E-21）と通信の失敗は、入力欄の上にまとめて出す。どちらの欄が違うかは区別しない（BR-09）
  const alert =
    mutation.error instanceof ApiError && mutation.error.errors.length === 0
      ? mutation.error.message
      : undefined

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const next: Errors = {
      email: form.email.trim() ? undefined : MESSAGES.required,
      password: form.password ? undefined : MESSAGES.required,
    }
    setErrors(next)
    if (next.email || next.password) {
      mutation.reset()
      return
    }
    mutation.mutate({ email: form.email.trim(), password: form.password })
  }

  return (
    <AuthLayout title="ログイン">
      <Box component="form" noValidate onSubmit={handleSubmit}>
        <Stack spacing={2.5}>
          {alert && <Alert severity="error">{alert}</Alert>}
          <TextField
            label="メールアドレス"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={(e) => setForm((prev) => ({ ...prev, email: e.target.value }))}
            error={Boolean(errors.email)}
            helperText={errors.email}
            fullWidth
          />
          <PasswordField
            label="パスワード"
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))}
            error={Boolean(errors.password)}
            helperText={errors.password}
            fullWidth
          />
          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={mutation.isPending}
            fullWidth
          >
            ログイン
          </Button>
          <Box sx={{ textAlign: 'center' }}>
            <Link component={RouterLink} to="/signup">
              アカウントを作成
            </Link>
          </Box>
        </Stack>
      </Box>
    </AuthLayout>
  )
}
