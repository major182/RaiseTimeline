// アクセストークンの置き場所（技術選定書 4.1・4.7 S-04）。
// JavaScript の変数（メモリ）にだけ置き、localStorage・sessionStorage には保存しない。
// 再読み込みで消えるので、画面は起動時にリフレッシュトークン（HttpOnly Cookie）で取り直す
let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}
