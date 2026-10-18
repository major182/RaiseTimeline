// 投稿・いいね・コメントの流れ（F-PO・F-LK・F-CM。テスト仕様書 4.9 の E2E-03・E2E-04）
import { expect, test } from '@playwright/test'
import { createPost, newUser, postCard, signup } from './helpers'

test('E2E-03 投稿するとタイムラインに出て、編集すると「編集済み」になり、削除すると消える', async ({
  page,
}) => {
  await signup(page)
  const body = `E2E の投稿 ${newUser().username}`

  await createPost(page, body)
  const card = postCard(page, body)
  await expect(card).toBeVisible()

  await card.getByRole('button', { name: '投稿のメニュー' }).click()
  await page.getByRole('menuitem', { name: '編集' }).click()
  const editor = page.getByRole('dialog', { name: '投稿を編集' })
  await editor.getByRole('textbox', { name: '本文' }).fill(`${body}（直した）`)
  await editor.getByRole('button', { name: '保存' }).click()
  const edited = postCard(page, `${body}（直した）`)
  await expect(edited).toContainText('編集済み')

  // 再読み込みしても残っている（DB に保存された）
  await page.reload()
  await expect(postCard(page, `${body}（直した）`)).toContainText('編集済み')

  await postCard(page, `${body}（直した）`).getByRole('button', { name: '投稿のメニュー' }).click()
  await page.getByRole('menuitem', { name: '削除' }).click()
  await page
    .getByRole('dialog', { name: '投稿を削除しますか？' })
    .getByRole('button', { name: '削除' })
    .click()
  await expect(page.getByText('投稿を削除しました')).toBeVisible()
  await expect(postCard(page, body)).toHaveCount(0)
})

test('E2E-04 他人の投稿にいいね・コメントすると数が増え、再読み込みしても残る', async ({
  browser,
}) => {
  // 投稿する人と反応する人の 2 人を、別々のブラウザ（Cookie を分ける）で動かす
  const authorPage = await (await browser.newContext()).newPage()
  const author = await signup(authorPage)
  const body = `いいねされる投稿 ${author.username}`
  await createPost(authorPage, body)

  const page = await (await browser.newContext()).newPage()
  await signup(page)
  await page.getByRole('tab', { name: '全体' }).click()
  const card = postCard(page, body)
  await card.getByRole('button', { name: 'いいね（0）' }).click()
  await expect(card.getByRole('button', { name: 'いいねを取り消す（1）' })).toBeVisible()

  await card.getByRole('link', { name: 'コメント（0）' }).click()
  await page.getByRole('textbox', { name: 'コメント' }).fill('いい投稿ですね')
  await page.getByRole('button', { name: 'コメントする' }).click()
  await expect(page.getByRole('region', { name: 'コメントの一覧' })).toContainText('いい投稿ですね')

  // 投稿した人の画面でも、再読み込みすると数が増えている
  await authorPage.reload()
  const mine = postCard(authorPage, body)
  await expect(mine.getByRole('button', { name: 'いいね（1）' })).toBeVisible()
  await expect(mine.getByRole('link', { name: 'コメント（1）' })).toBeVisible()
})
