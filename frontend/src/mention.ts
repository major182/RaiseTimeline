// 本文のメンション（@ユーザー名）の見つけ方と入れ方（画面設計書 4.4・4.5、BR-60・BR-61）

/** 本文の一部。文字のままの部分か、メンション。 */
export type BodyPart = { type: 'text'; text: string } | { type: 'mention'; username: string }

/**
 * リンクにするメンション。ユーザー名の形（BR-02：半角英数字と「_」で 4〜15 文字）で、
 * 前後に英数字・「_」が続かないもの（a@example.com や 16 文字以上は除く）。
 */
const MENTION = /(?<![A-Za-z0-9_])@([A-Za-z0-9_]{4,15})(?![A-Za-z0-9_])/g

/** 本文を、文字の部分とメンションに分ける。 */
export function splitMentions(text: string): BodyPart[] {
  const parts: BodyPart[] = []
  let last = 0
  for (const match of text.matchAll(MENTION)) {
    if (match.index > last) parts.push({ type: 'text', text: text.slice(last, match.index) })
    parts.push({ type: 'mention', username: match[1] })
    last = match.index + match[0].length
  }
  if (last < text.length) parts.push({ type: 'text', text: text.slice(last) })
  return parts
}

/** 入力中のメンション。`@` の位置と、`@` の後に打った文字。 */
export type MentionQuery = { start: number; query: string }

/** カーソルの直前が「`@` + 1〜15 文字」で、`@` の前が文の始めか英数字・「_」以外のとき。 */
const TYPING = /(?:^|[^A-Za-z0-9_])@([A-Za-z0-9_]{1,15})$/

/** カーソルの位置で入力中のメンションを返す。なければ null（BR-61）。 */
export function findMentionQuery(text: string, caret: number): MentionQuery | null {
  const before = text.slice(0, caret)
  const match = TYPING.exec(before)
  if (!match) return null
  const query = match[1]
  return { start: caret - query.length - 1, query }
}

/** 打った `@…` を `@ユーザー名 ` に置き換え、置き換えた本文と、その後ろのカーソルの位置を返す。 */
export function insertMention(
  text: string,
  mention: MentionQuery,
  username: string,
): { text: string; caret: number } {
  const inserted = `@${username} `
  const end = mention.start + 1 + mention.query.length
  return {
    text: text.slice(0, mention.start) + inserted + text.slice(end),
    caret: mention.start + inserted.length,
  }
}
