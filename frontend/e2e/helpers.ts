// E2E テストの共通の操作。テストどうしが影響しないよう、テストごとに新しい利用者を作る
import { type Page, expect } from '@playwright/test'

export const PASSWORD = 'pass1234'

/** 開発用のテストデータの利用者（DB 設計書 7 章）。読むだけにして、状態は変えない。 */
export const SUZUKI = { username: 'suzuki_ken', displayName: '鈴木 健' }

export type NewUser = { username: string; email: string }

/** ほかのテスト・前の実行とぶつからない利用者（ユーザー名は 15 文字まで。BR-02）。 */
export function newUser(): NewUser {
  const suffix = `${Date.now().toString(36).slice(-6)}${Math.floor(Math.random() * 36 ** 3)
    .toString(36)
    .padStart(3, '0')}`
  return { username: `e2e_${suffix}`, email: `e2e_${suffix}@example.com` }
}

/** 利用者登録の画面から登録し、ホームが出るまで待つ。 */
export async function signup(page: Page, user: NewUser = newUser()): Promise<NewUser> {
  await page.goto('/signup')
  await page.getByLabel('メールアドレス', { exact: true }).fill(user.email)
  await page.getByLabel('パスワード', { exact: true }).fill(PASSWORD)
  await page.getByLabel('パスワード（確認）').fill(PASSWORD)
  await page.getByLabel('ユーザー名', { exact: true }).fill(user.username)
  await page.getByRole('button', { name: '登録' }).click()
  await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
  return user
}

/** ログインの画面からログインし、ホームが出るまで待つ。 */
export async function login(page: Page, email: string) {
  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(email)
  await page.getByLabel('パスワード', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
}

/** 投稿の作成のダイアログから投稿する。 */
export async function createPost(page: Page, body: string) {
  await page.getByRole('button', { name: '投稿する' }).first().click()
  const dialog = page.getByRole('dialog', { name: '投稿を作成' })
  await dialog.getByRole('textbox', { name: '本文' }).fill(body)
  await dialog.getByRole('button', { name: '投稿する' }).click()
  await expect(dialog).toBeHidden()
}

/** 本文で投稿カードを探す。 */
export function postCard(page: Page, body: string) {
  return page.getByRole('article').filter({ hasText: body })
}
