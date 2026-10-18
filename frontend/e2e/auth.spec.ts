// 認証の流れ（F-AU。テスト仕様書 4.9 の E2E-01・E2E-02）
import { expect, test } from '@playwright/test'
import { PASSWORD, login, signup } from './helpers'

test('E2E-01 登録するとホームに入り、ログアウトしてもう一度ログインできる', async ({ page }) => {
  const user = await signup(page)
  await expect(page.getByText('ようこそ、RaiseTimeline へ')).toBeVisible()

  await page.getByRole('link', { name: '設定' }).click()
  await page.getByRole('button', { name: 'ログアウト' }).click()
  await page
    .getByRole('dialog', { name: 'ログアウトしますか？' })
    .getByRole('button', { name: 'ログアウト' })
    .click()
  await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible()

  // ログアウトしたら、保護された画面は開けない（BR-06）
  await page.goto('/settings')
  await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible()

  // ログインし直すと、開こうとしていた画面（設定）に戻る
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByRole('heading', { name: '設定' })).toBeVisible()

  // ログインの画面から直接ログインしたときは、ホームに入る
  await page.context().clearCookies()
  await login(page, user.email)
})

test('E2E-01 ログインの失敗は、どちらが違うかを教えない', async ({ page }) => {
  const user = await signup(page)
  await page.context().clearCookies()

  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード', { exact: true }).fill(`${PASSWORD}x`)
  await page.getByRole('button', { name: 'ログイン' }).click()

  await expect(page.getByRole('alert')).toContainText('メールアドレスかパスワードが違います')
})

test('E2E-02 再読み込みしてもログインしたまま（アクセストークンはメモリだけ、Cookie で取り直す）', async ({
  page,
}) => {
  await signup(page)

  await page.reload()

  await expect(page.getByRole('heading', { name: 'ホーム' })).toBeVisible()
  // アクセストークンをブラウザの保存領域に置いていない（技術選定書 4.7 S-04）
  const stored = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))
  expect(stored).not.toContain('eyJ')
})

test('E2E-02 ログインしていなければ、保護された画面からログインの画面へ移る', async ({ page }) => {
  await page.goto('/search')

  await expect(page.getByRole('heading', { name: 'ログイン' })).toBeVisible()
})
