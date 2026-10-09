import { Box, Button, Typography } from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { logout } from '../api/auth'
import { ME_QUERY_KEY, useMe } from '../auth/useMe'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useNotify } from '../components/SnackbarProvider'

/** 仮のホーム画面。タイムライン（SC-03）を作るまでの間、ログインできたことと、ログアウトだけを置く。 */
export function HomePage() {
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const notify = useNotify()
  const [confirming, setConfirming] = useState(false)

  const mutation = useMutation({
    mutationFn: logout,
    // 「ログインしていない」状態にすると、RequireAuth がログイン画面へ移動させる
    onSuccess: () => queryClient.setQueryData(ME_QUERY_KEY, null),
    onError: (error) => notify(error.message, 'error'),
  })

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', p: 3 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        ようこそ、{me?.displayName}さん
      </Typography>
      <Typography color="text.secondary" gutterBottom>
        @{me?.username}
      </Typography>
      <Button
        variant="outlined"
        onClick={() => setConfirming(true)}
        disabled={mutation.isPending}
        sx={{ mt: 2 }}
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
