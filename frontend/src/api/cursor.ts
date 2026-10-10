/** 続きを取るときだけ ?cursor= を付ける。カーソルは中身を読まずにそのまま送る（API 設計書 2.5）。 */
export function withCursor(path: string, cursor: string | null): string {
  if (!cursor) return path
  return `${path}${path.includes('?') ? '&' : '?'}cursor=${encodeURIComponent(cursor)}`
}
