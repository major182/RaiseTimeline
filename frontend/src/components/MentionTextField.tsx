import {
  ListItemAvatar,
  ListItemText,
  MenuItem,
  MenuList,
  Paper,
  Popper,
  TextField,
  type TextFieldProps,
} from '@mui/material'
import { useForkRef } from '@mui/material/utils'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState, type KeyboardEvent, type Ref } from 'react'
import { searchUsers } from '../api/users'
import { findMentionQuery, insertMention } from '../mention'
import { QUERY_KEYS } from '../queryCache'
import { UserAvatar } from './UserAvatar'

/** 打つのをやめてから候補を探すまでの時間（画面設計書 4.5）。 */
export const MENTION_DEBOUNCE_MS = 300
/** 候補の数（BR-61）。 */
const MAX_CANDIDATES = 5

type Props = Omit<TextFieldProps, 'value' | 'onChange' | 'inputRef'> & {
  value: string
  onValueChange: (value: string) => void
  inputRef?: Ref<HTMLInputElement | HTMLTextAreaElement>
}

/**
 * メンションの候補（画面設計書 4.5 MC-01、F-MN-02）を出す入力欄。ほかは TextField と同じ。
 * カーソルの直前に `@…` を打つと、利用者の検索で候補を出し、選ぶと `@ユーザー名 ` を入れる。
 */
export function MentionTextField({ value, onValueChange, inputRef, onKeyDown, ...rest }: Props) {
  // 候補の位置合わせに描画で使うので、ref ではなく state で持つ
  const [input, setInput] = useState<HTMLInputElement | HTMLTextAreaElement | null>(null)
  const handleRef = useForkRef(inputRef, setInput)
  const [caret, setCaret] = useState(0)
  const [debounced, setDebounced] = useState<string | null>(null)
  /** 選んでいる行。候補（検索語）が変わったら最初の行に戻す。 */
  const [selection, setSelection] = useState({ query: debounced, index: 0 })
  const active = selection.query === debounced ? selection.index : 0
  /** Esc で閉じた `@` の位置。別の `@` を打つまで出さない。 */
  const [dismissed, setDismissed] = useState<number | null>(null)
  const [focused, setFocused] = useState(false)

  const mention = focused ? findMentionQuery(value, caret) : null
  const q = mention?.query ?? null

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(q), MENTION_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [q])

  const search = useQuery({
    queryKey: QUERY_KEYS.search(debounced ?? ''),
    queryFn: () => searchUsers(debounced!, null),
    enabled: debounced !== null,
    // 候補が取れなくても、メッセージは出さずに候補を閉じる（入力の邪魔をしない）
    retry: false,
  })
  const candidates =
    q !== null && debounced === q ? (search.data?.items ?? []).slice(0, MAX_CANDIDATES) : []
  const open = mention !== null && dismissed !== mention.start && candidates.length > 0

  const readCaret = () => setCaret(input?.selectionStart ?? 0)

  const choose = (username: string) => {
    if (!mention) return
    const next = insertMention(value, mention, username)
    onValueChange(next.text)
    setCaret(next.caret)
    // 書き換えた本文が描かれた後に、カーソルを入れたユーザー名の後ろへ移す
    requestAnimationFrame(() => input?.setSelectionRange(next.caret, next.caret))
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (open) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault()
        const step = e.key === 'ArrowDown' ? 1 : -1
        setSelection({
          query: debounced,
          index: (active + step + candidates.length) % candidates.length,
        })
        return
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        // 候補が開いている間の Enter は、送信・改行に使わない
        e.preventDefault()
        choose(candidates[active].username)
        return
      }
      if (e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation() // ダイアログを閉じない
        setDismissed(mention.start)
        return
      }
    }
    onKeyDown?.(e)
  }

  return (
    <>
      <TextField
        {...rest}
        value={value}
        inputRef={handleRef}
        onChange={(e) => {
          onValueChange(e.target.value)
          setCaret(e.target.selectionStart ?? e.target.value.length)
        }}
        onSelect={readCaret}
        onKeyDown={handleKeyDown}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
      />
      <Popper
        open={open}
        anchorEl={input}
        placement="bottom-start"
        sx={{ zIndex: (theme) => theme.zIndex.modal + 1 }}
      >
        <Paper elevation={4}>
          <MenuList role="listbox" aria-label="メンションの候補" dense>
            {candidates.map((user, i) => (
              <MenuItem
                key={user.id}
                role="option"
                aria-selected={i === active}
                selected={i === active}
                // 押しても入力欄から焦点を外さない（外すと候補が閉じる）
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choose(user.username)}
              >
                <ListItemAvatar sx={{ minWidth: 40 }}>
                  <UserAvatar user={user} size={28} />
                </ListItemAvatar>
                <ListItemText primary={user.displayName} secondary={`@${user.username}`} />
              </MenuItem>
            ))}
          </MenuList>
        </Paper>
      </Popper>
    </>
  )
}
