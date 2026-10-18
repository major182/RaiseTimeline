// 検索・フォロー・フォロー中タブの流れ（F-US・F-FL・F-TL。テスト仕様書 4.9 の E2E-05）
import { expect, test } from '@playwright/test'
import { SUZUKI, signup } from './helpers'

/** フォロー中タブに、自分の投稿もフォロー中の人の投稿もないときの文言（画面設計書 N-01）。 */
const EMPTY_FOLLOWING = 'まだ投稿がありません。ほかのユーザーをフォローしてみましょう'

test('E2E-05 検索した人をフォローすると、フォロー中タブにその人の投稿が出て、外すと出なくなる', async ({
  page,
}) => {
  // 登録したばかりで誰もフォローしていないので、フォロー中タブは空
  await signup(page)
  await expect(page.getByText(EMPTY_FOLLOWING)).toBeVisible()

  // 入力をやめると自動で検索する（画面設計書 5.10）
  await page.getByRole('link', { name: '検索' }).click()
  await page.getByRole('searchbox', { name: '検索' }).fill(SUZUKI.username)
  const results = page.getByRole('region', { name: 'ユーザーの一覧' })
  await results.getByRole('button', { name: `${SUZUKI.displayName}さんをフォロー` }).click()
  await expect(
    results.getByRole('button', { name: `${SUZUKI.displayName}さんのフォローを解除` }),
  ).toBeVisible()

  await page.getByRole('link', { name: 'ホーム' }).click()
  await page.getByRole('tab', { name: 'フォロー中' }).click()
  await expect(
    page.getByRole('article', { name: `${SUZUKI.displayName}さんの投稿` }).first(),
  ).toBeVisible()

  // プロフィールからフォローを外すと、フォロー中タブから消える（BR-42）
  await page.goto(`/users/${SUZUKI.username}`)
  await page.getByRole('button', { name: `${SUZUKI.displayName}さんのフォローを解除` }).click()
  await expect(
    page.getByRole('button', { name: `${SUZUKI.displayName}さんをフォロー` }),
  ).toBeVisible()
  await page.goto('/')
  await page.getByRole('tab', { name: 'フォロー中' }).click()
  await expect(page.getByText(EMPTY_FOLLOWING)).toBeVisible()
})
