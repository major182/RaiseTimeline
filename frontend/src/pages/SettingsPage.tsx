import ChevronRight from '@mui/icons-material/ChevronRight'
import {
  Box,
  Button,
  Divider,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router'
import { changePassword, logout, type PasswordChangeInput } from '../api/auth'
import { ApiError } from '../api/client'
import { ME_QUERY_KEY } from '../auth/useMe'
import { type PasswordChangeErrors, validatePasswordChange } from '../auth/validation'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { PageColumn } from '../components/PageColumn'
import { PasswordField } from '../components/PasswordField'
import { useNotify } from '../components/SnackbarProvider'

/** 設定（画面設計書 5.13 SC-10）。プロフィールの編集へのリンク・パスワードの変更・ログアウト。 */
export function SettingsPage() {
  return (
    <PageColumn title="設定" back={false}>
      <List disablePadding>
        <ListItemButton component={RouterLink} to="/settings/profile">
          <ListItemText primary="プロフィールを編集" />
          <ChevronRight color="action" />
        </ListItemButton>
      </List>
      <Divider />
      <PasswordForm />
      <Divider />
      <LogoutSection />
    </PageColumn>
  )
}

const EMPTY: PasswordChangeInput = {
  currentPassword: '',
  newPassword: '',
  newPasswordConfirmation: '',
}
const FIELDS = Object.keys(EMPTY) as (keyof PasswordChangeInput)[]

/** パスワードの変更（F-AU-05）。 */
function PasswordForm() {
  const notify = useNotify()
  const [form, setForm] = useState<PasswordChangeInput>(EMPTY)
  const [errors, setErrors] = useState<PasswordChangeErrors>({})

  const mutation = useMutation({
    mutationFn: changePassword,
    onSuccess: () => {
      setForm(EMPTY)
      notify('パスワードを変更しました') // I-07
    },
    onError: (error) => {
      // 今のパスワードが違う（E-22）などは、該当の入力欄の下に出す
      if (error instanceof ApiError && error.errors.length > 0) {
        setErrors(Object.fromEntries(FIELDS.map((f) => [f, error.fieldMessage(f)])))
      } else {
        notify(error.message, 'error')
      }
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const next = validatePasswordChange(form)
    setErrors(next)
    if (Object.keys(next).length > 0) return
    mutation.mutate(form)
  }

  const fieldProps = (name: keyof PasswordChangeInput) => ({
    value: form[name],
    onChange: (e: ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [name]: e.target.value })),
    error: Boolean(errors[name]),
    helperText: errors[name],
    fullWidth: true,
  })

  return (
    <Box
      component="form"
      noValidate
      onSubmit={handleSubmit}
      aria-labelledby="password-heading"
      sx={{ p: 2 }}
    >
      <Typography
        id="password-heading"
        variant="subtitle1"
        component="h2"
        sx={{ fontWeight: 'bold', mb: 2 }}
      >
        パスワードの変更
      </Typography>
      <Stack spacing={2.5}>
        <PasswordField
          label="現在のパスワード"
          autoComplete="current-password"
          {...fieldProps('currentPassword')}
        />
        <PasswordField
          label="新しいパスワード"
          autoComplete="new-password"
          {...fieldProps('newPassword')}
        />
        <PasswordField
          label="新しいパスワード（確認）"
          autoComplete="new-password"
          {...fieldProps('newPasswordConfirmation')}
        />
        <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
          <Button
            type="submit"
            variant="contained"
            disabled={mutation.isPending}
            sx={{ borderRadius: 5 }}
          >
            変更
          </Button>
        </Stack>
      </Stack>
    </Box>
  )
}

/** ログアウト（F-AU-03）。確認（C-04）してからログアウトし、ログイン画面へ移る。 */
function LogoutSection() {
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [confirming, setConfirming] = useState(false)

  const mutation = useMutation({
    mutationFn: logout,
    // 「ログインしていない」状態にすると、RequireAuth がログイン画面へ移動させる
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['posts'] })
      queryClient.removeQueries({ queryKey: ['users'] })
      queryClient.removeQueries({ queryKey: ['comments'] })
      queryClient.setQueryData(ME_QUERY_KEY, null)
    },
    onError: (error) => notify(error.message, 'error'),
  })

  return (
    <Box sx={{ p: 2 }}>
      <Button
        color="error"
        variant="outlined"
        onClick={() => setConfirming(true)}
        disabled={mutation.isPending}
        sx={{ borderRadius: 5 }}
      >
        ログアウト
      </Button>
      <ConfirmDialog
        open={confirming}
        title="ログアウトしますか？"
        confirmLabel="ログアウト"
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          mutation.mutate()
        }}
      />
    </Box>
  )
}
