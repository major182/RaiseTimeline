// アクセシビリティの自動チェック（テスト仕様書 4.9 の E2E-07）。主な画面を開き、axe-core で WCAG 2.1 の A・AA の決まりに
// 反していないかを調べる（色のコントラスト・ラベル・見出し・ARIA の使い方など）。
// 自動で分かるのは一部なので、キーボードだけでの操作や読み上げは別に手で確かめる
import AxeBuilder from '@axe-core/playwright'
import { type Page, expect, test } from '@playwright/test'
import { SUZUKI, createPost, postCard, signup } from './helpers'

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

/** 今の画面を axe で調べ、違反がないことを確かめる。違反があれば、決まりの ID・重さ・該当の要素を出す。 */
async function expectNoViolations(page: Page, include?: string) {
  let builder = new AxeBuilder({ page }).withTags(WCAG_TAGS)
  if (include) builder = builder.include(include)
  const { violations } = await builder.analyze()
  const summary = violations.map((v) => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    targets: v.nodes.map((n) => n.target.join(' ')),
  }))
  expect(summary).toEqual([])
}

test.describe('E2E-07 ログインしていない画面', () => {
  test('ログイン', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByRole('button', { name: 'ログイン' })).toBeVisible()
    await expectNoViolations(page)
  })

  test('利用者登録', async ({ page }) => {
    await page.goto('/signup')
    await expect(page.getByRole('button', { name: '登録' })).toBeVisible()
    await expectNoViolations(page)
  })

  test('入力の誤りを表示したログイン', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page.getByText('入力してください').first()).toBeVisible()
    await expectNoViolations(page)
  })

  test('ページが見つからない', async ({ page }) => {
    await page.goto('/no-such-page')
    await expect(page.getByRole('heading').first()).toBeVisible()
    await expectNoViolations(page)
  })
})

test.describe('E2E-07 ログインした後の画面', () => {
  test('タイムライン（フォロー中・全体）と投稿の作成のダイアログ', async ({ page }) => {
    const user = await signup(page)
    await createPost(page, `アクセシビリティ ${user.username}`)
    await expect(postCard(page, `アクセシビリティ ${user.username}`)).toBeVisible()
    await expectNoViolations(page)

    await page.getByRole('tab', { name: '全体' }).click()
    await expect(page.getByRole('article').first()).toBeVisible()
    await expectNoViolations(page)

    await page.getByRole('button', { name: '投稿する' }).first().click()
    await expect(page.getByRole('dialog', { name: '投稿を作成' })).toBeVisible()
    await expectNoViolations(page)
  })

  test('投稿の詳細といいねした人の一覧', async ({ page }) => {
    const user = await signup(page)
    const body = `詳細のアクセシビリティ ${user.username}`
    await createPost(page, body)
    const card = postCard(page, body)
    await card.getByRole('button', { name: 'いいね 0' }).click()
    await card.getByRole('link', { name: 'コメント 0' }).click()
    await page.getByRole('textbox', { name: 'コメント' }).fill('コメントです')
    await page.getByRole('button', { name: 'コメントする' }).click()
    await expect(page.getByRole('region', { name: 'コメントの一覧' })).toContainText('コメントです')
    await expectNoViolations(page)

    await page.goto(`${new URL(page.url()).pathname}/likes`)
    await expect(page.getByText(user.username).first()).toBeVisible()
    await expectNoViolations(page)
  })

  test('プロフィールとフォロー一覧', async ({ page }) => {
    await signup(page)
    await page.goto(`/users/${SUZUKI.username}`)
    await expect(page.getByRole('article').first()).toBeVisible()
    await expectNoViolations(page)

    await page.goto(`/users/${SUZUKI.username}/followers`)
    await expect(page.getByRole('tab', { name: 'フォロワー' })).toBeVisible()
    await expectNoViolations(page)
  })

  test('検索', async ({ page }) => {
    await signup(page)
    await page.goto('/search')
    await page.getByRole('searchbox', { name: '検索' }).fill(SUZUKI.username)
    await expect(page.getByRole('region', { name: 'ユーザーの一覧' })).toContainText(
      SUZUKI.displayName,
    )
    await expectNoViolations(page)
  })

  test('設定とプロフィールの編集', async ({ page }) => {
    await signup(page)
    await page.goto('/settings')
    await expect(page.getByRole('heading').first()).toBeVisible()
    await expectNoViolations(page)

    await page.goto('/settings/profile')
    await expect(page.getByRole('button', { name: '保存' })).toBeVisible()
    await expectNoViolations(page)
  })
})
