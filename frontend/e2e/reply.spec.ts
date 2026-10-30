// コメントへの返信とメンションの流れ（F-CM-05・06、F-MN。テスト仕様書 4.9 の E2E-06）
import { expect, test } from '@playwright/test'
import { createPost, postCard, signup } from './helpers'

test('E2E-06 自分の投稿のコメントに、候補から選んだメンションつきで返信すると、字下げして出て、再読み込みしても残る', async ({
  browser,
}) => {
  // 投稿する人とコメントする人の 2 人を、別々のブラウザ（Cookie を分ける）で動かす
  const authorPage = await (await browser.newContext()).newPage()
  const author = await signup(authorPage)
  const body = `返信される投稿 ${author.username}`
  await createPost(authorPage, body)

  const page = await (await browser.newContext()).newPage()
  const commenter = await signup(page)
  await page.getByRole('tab', { name: '全体' }).click()
  await postCard(page, body).getByRole('link', { name: 'コメント 0' }).click()
  await page.getByRole('textbox', { name: 'コメント' }).fill('はじめまして')
  await page.getByRole('button', { name: 'コメントする' }).click()
  await expect(page.getByRole('region', { name: 'コメントの一覧' })).toContainText('はじめまして')

  // 投稿した人が、コメントに返信する。@ に続けて打つと候補が出る
  await authorPage.reload()
  await postCard(authorPage, body).getByRole('link', { name: 'コメント 1' }).click()
  await authorPage.getByRole('button', { name: '返信' }).click()
  const input = authorPage.getByRole('textbox', { name: '返信' })
  await input.pressSequentially(`@${commenter.username.slice(0, 8)}`)
  await authorPage
    .getByRole('listbox', { name: 'メンションの候補' })
    .getByRole('option', { name: new RegExp(commenter.username) })
    .click()
  await expect(input).toHaveValue(`@${commenter.username} `)
  await input.pressSequentially('ありがとうございます')
  await authorPage.getByRole('button', { name: '返信する' }).click()

  const replies = authorPage.getByRole('region', { name: '返信の一覧' })
  await expect(replies).toContainText('ありがとうございます')
  await expect(authorPage.getByRole('link', { name: 'コメント 2' })).toBeVisible()

  // 再読み込みすると、返信は閉じた状態で数だけ出る。開くと、メンションがプロフィールへのリンクになっている
  await authorPage.reload()
  await authorPage.getByRole('button', { name: '返信を表示（1 件）' }).click()
  const mention = authorPage
    .getByRole('region', { name: '返信の一覧' })
    .getByRole('link', { name: `@${commenter.username}` })
  await mention.click()
  await expect(authorPage).toHaveURL(new RegExp(`/users/${commenter.username}$`))
})
