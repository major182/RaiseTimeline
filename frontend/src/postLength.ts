// 投稿・コメントの文字数（画面設計書 1.5、DB 設計書 D-7）

/** 投稿・コメントの文字数の上限（BR-10、BR-30）。 */
export const BODY_MAX_LENGTH = 280

/**
 * 文字数をコードポイント（文字の番号）の数で数える。サーバー（Java の codePointCount）・DB（char_length）と同じ数え方。
 * JavaScript の length は UTF-16 の数なので、絵文字が 2 文字に数えられてしまう。Array.from はコードポイントごとに分ける。
 */
export function countChars(text: string): number {
  return Array.from(text).length
}
