import { Alert, Box, Button, InputAdornment, Skeleton, Stack, TextField } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import type { Me } from '../api/auth'
import { ApiError } from '../api/client'
import type { Profile } from '../api/types'
import { getUser, updateAvatar, updateProfile } from '../api/users'
import { ME_QUERY_KEY, useMe } from '../auth/useMe'
import {
  BIO_MAX_LENGTH,
  type ProfileErrors,
  type ProfileInput,
  validateProfile,
} from '../auth/validation'
import { CharCounter } from '../components/CharCounter'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { PageColumn } from '../components/PageColumn'
import { useNotify } from '../components/SnackbarProvider'
import { UserAvatar } from '../components/UserAvatar'
import { AVATAR_MAX_SIDE, IMAGE_TYPES, checkImage, prepareImage } from '../imageAttach'
import { MESSAGES } from '../messages'
import { QUERY_KEYS, updateUser } from '../queryCache'

const FIELDS: (keyof ProfileInput)[] = ['displayName', 'username', 'bio']

/** プロフィールの編集（画面設計書 5.9 SC-06）。今の値を読み込んでから、入力欄を出す。 */
export function ProfileEditPage() {
  const { data: me } = useMe()
  const query = useQuery({
    queryKey: ['users', 'edit', me?.id],
    queryFn: () => getUser(me!.id),
    enabled: !!me,
    // 入力の途中で読み直して、入力が消えないようにする
    staleTime: Infinity,
    gcTime: 0,
  })

  return (
    <PageColumn title="プロフィールを編集">
      {query.isError ? (
        <Alert severity="error" sx={{ m: 2 }}>
          {query.error.message}
        </Alert>
      ) : query.data ? (
        <ProfileForm profile={query.data} />
      ) : (
        <Box sx={{ p: 2 }} aria-busy="true" aria-label="読み込み中">
          <Skeleton variant="circular" width={80} height={80} />
          <Skeleton height={72} />
          <Skeleton height={72} />
          <Skeleton height={120} />
        </Box>
      )}
    </PageColumn>
  )
}

/** 新しいアイコン（選んだけれど、まだ保存していない）。 */
type PendingAvatar = { file: File; previewUrl: string }

function ProfileForm({ profile }: { profile: Profile }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const notify = useNotify()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const initial: ProfileInput = {
    displayName: profile.displayName,
    username: profile.username,
    bio: profile.bio,
  }
  const [form, setForm] = useState<ProfileInput>(initial)
  const [errors, setErrors] = useState<ProfileErrors>({})
  const [avatar, setAvatar] = useState<PendingAvatar>()
  const [avatarError, setAvatarError] = useState<string>()
  const [preparing, setPreparing] = useState(false)
  const [confirming, setConfirming] = useState(false)

  const profilePath = (username: string) => `/users/${username}`
  const changed = FIELDS.filter((f) => form[f] !== initial[f])
  const dirty = changed.length > 0 || !!avatar

  // 画面を離れたら、プレビューの URL を捨てる
  useEffect(() => () => avatar && URL.revokeObjectURL(avatar.previewUrl), [avatar])

  const mutation = useMutation({
    mutationFn: async () => {
      // 変えた項目だけを送る。誤りが出やすい項目を先に送り、通ってからアイコンを送る
      let saved = profile
      if (changed.length > 0) {
        saved = await updateProfile(Object.fromEntries(changed.map((f) => [f, form[f]])))
      }
      if (avatar) saved = await updateAvatar(avatar.file)
      return saved
    },
    onSuccess: (saved) => {
      // 自分の表示（ナビ・投稿の投稿者・一覧の行）を、読み直さずに書き換える
      queryClient.setQueryData<Me | null>(ME_QUERY_KEY, (me) =>
        me
          ? {
              ...me,
              displayName: saved.displayName,
              username: saved.username,
              avatarUrl: saved.avatarUrl,
            }
          : me,
      )
      updateUser(queryClient, saved.id, (u) => ({
        ...u,
        displayName: saved.displayName,
        username: saved.username,
        avatarUrl: saved.avatarUrl,
        bio: saved.bio,
      }))
      queryClient.setQueryData(QUERY_KEYS.profile(saved.username), saved)
      void queryClient.invalidateQueries({ queryKey: ['comments'] })
      notify('プロフィールを保存しました') // I-06
      // ユーザー名を変えたら、新しい URL のプロフィールへ
      void navigate(profilePath(saved.username), { replace: true })
    },
    onError: (error) => {
      // サーバーの誤り（使われている：E-07 など）は、該当の入力欄の下に出す
      if (error instanceof ApiError && error.errors.length > 0) {
        setErrors(Object.fromEntries(FIELDS.map((f) => [f, error.fieldMessage(f)])))
        const file = error.fieldMessage('file')
        if (file) setAvatarError(file)
      } else {
        notify(error.message, 'error')
      }
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const next = validateProfile(form)
    setErrors(next)
    if (Object.keys(next).length > 0) return
    mutation.mutate()
  }

  /** アイコンを選んだら確かめて、縮小・再エンコードしてプレビューに出す（保存するまで反映しない）。 */
  async function selectAvatar(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    const invalid = checkImage(file)
    setAvatarError(invalid)
    if (invalid) return
    setPreparing(true)
    try {
      const prepared = await prepareImage(file, AVATAR_MAX_SIDE)
      setAvatar({ file: prepared, previewUrl: URL.createObjectURL(prepared) })
    } catch (error) {
      setAvatarError(
        error instanceof Error && error.message === MESSAGES.imageTooLarge
          ? MESSAGES.imageTooLarge
          : MESSAGES.imageType,
      )
    } finally {
      setPreparing(false)
    }
  }

  function cancel() {
    if (dirty) setConfirming(true)
    else void navigate(profilePath(profile.username))
  }

  const fieldProps = (name: keyof ProfileInput) => ({
    value: form[name],
    onChange: (e: ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [name]: e.target.value })),
    error: Boolean(errors[name]),
    fullWidth: true,
  })

  return (
    <Box component="form" noValidate onSubmit={handleSubmit} sx={{ p: 2 }}>
      <Stack spacing={3}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
          <UserAvatar
            user={{
              displayName: form.displayName || profile.displayName,
              avatarUrl: avatar?.previewUrl ?? profile.avatarUrl,
            }}
            size={80}
          />
          <Box>
            <Button
              variant="outlined"
              onClick={() => fileInputRef.current?.click()}
              disabled={preparing || mutation.isPending}
              sx={{ borderRadius: 5 }}
            >
              アイコンを変更
            </Button>
            <Box
              component="input"
              ref={fileInputRef}
              type="file"
              accept={IMAGE_TYPES.join(',')}
              hidden
              data-testid="avatar-input"
              onChange={selectAvatar}
            />
            {avatarError && (
              <Box role="alert" sx={{ color: 'error.main', typography: 'body2', mt: 0.5 }}>
                {avatarError}
              </Box>
            )}
          </Box>
        </Stack>
        <TextField
          label="表示名"
          required
          helperText={errors.displayName}
          {...fieldProps('displayName')}
        />
        <TextField
          label="ユーザー名"
          required
          helperText={errors.username ?? '変更すると、プロフィールの URL も変わります'}
          slotProps={{
            input: { startAdornment: <InputAdornment position="start">@</InputAdornment> },
          }}
          {...fieldProps('username')}
        />
        <Box>
          <TextField
            label="自己紹介"
            multiline
            minRows={3}
            helperText={errors.bio}
            {...fieldProps('bio')}
          />
          <Stack direction="row" sx={{ justifyContent: 'flex-end', mt: 1 }}>
            <CharCounter text={form.bio} max={BIO_MAX_LENGTH} />
          </Stack>
        </Box>
        <Stack direction="row" spacing={1.5} sx={{ justifyContent: 'flex-end' }}>
          <Button onClick={cancel} disabled={mutation.isPending}>
            キャンセル
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={preparing || mutation.isPending}
            sx={{ borderRadius: 5 }}
          >
            保存
          </Button>
        </Stack>
      </Stack>
      <ConfirmDialog
        open={confirming}
        title="編集内容を破棄しますか？"
        body="入力した内容は保存されません。"
        confirmLabel="破棄"
        danger
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          void navigate(profilePath(profile.username))
        }}
      />
    </Box>
  )
}
