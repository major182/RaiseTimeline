// ブラウザでの一連の操作（性能テスト設計書 3 章の PT-07・PT-08）。本番用のビルドの画面を、実際のブラウザで操作する
// 画面の要素は E2E テスト（frontend/e2e/）と同じく、役割（role）と名前で探す
import { browser, type Page } from 'k6/browser'
import { check } from 'k6'
import { Trend } from 'k6/metrics'
import { PASSWORD, USER_COUNT } from './config.ts'
import { userEmail } from './auth.ts'

// 画面の場所（scripts/perf.ts が vite preview で配信する）
export const WEB_URL: string = __ENV.WEB_URL || 'http://host.docker.internal:4173'

// ブラウザの利用者は、API の負荷の利用者（1 番から）とぶつからないよう、後ろの番号を使う
const BROWSER_USER_OFFSET = 900

// 手順ごとの所要時間（操作してから、結果が画面に出るまで）
const steps = {
  login: new Trend('step_login', true),
  tab: new Trend('step_tab', true),
  loadMore: new Trend('step_load_more', true),
  post: new Trend('step_post', true),
  like: new Trend('step_like', true),
  comment: new Trend('step_comment', true),
  mention: new Trend('step_mention', true),
  profile: new Trend('step_profile', true),
}

// ブラウザの操作の合否のしきい値（性能テスト設計書 6 章）
export const browserThresholds = {
  browser_web_vital_lcp: ['p(95)<2500'],
  browser_web_vital_inp: ['p(95)<200'],
  browser_web_vital_cls: ['p(95)<0.1'],
  step_login: ['p(95)<3000'],
  step_post: ['p(95)<1000'],
}

async function timed(trend: Trend, action: () => Promise<void>): Promise<void> {
  const start = Date.now()
  await action()
  trend.add(Date.now() - start)
}

async function articleCount(page: Page): Promise<number> {
  return page.locator('article').count()
}

async function flow(page: Page): Promise<void> {
  const n = BROWSER_USER_OFFSET + ((__VU - 1) % (USER_COUNT - BROWSER_USER_OFFSET)) + 1

  // 1. ログイン → ホーム
  await page.goto(`${WEB_URL}/login`)
  await timed(steps.login, async () => {
    await page.getByLabel('メールアドレス').fill(userEmail(n))
    await page.getByLabel('パスワード', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'ログイン' }).click()
    await page.getByRole('heading', { name: 'ホーム' }).waitFor()
    await page.locator('article').first().waitFor()
  })

  // 2. フォロー中タブへ切り替え
  await timed(steps.tab, async () => {
    await page.getByRole('tab', { name: 'フォロー中' }).click()
    await page.locator('article').first().waitFor()
  })

  // 3. 下までスクロールして、続きの読み込み（無限スクロール）
  const before = await articleCount(page)
  await timed(steps.loadMore, async () => {
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    await page.waitForFunction(
      (count: number) => document.querySelectorAll('article').length > count,
      { timeout: 10_000 },
      before,
    )
  })
  await page.evaluate(() => window.scrollTo(0, 0))

  // 4. 投稿 → 一覧に出るまで
  const body = `ブラウザの性能テスト VU ${__VU} 回 ${__ITER} ${Date.now()}`
  const card = page.locator('article', { hasText: body })
  await timed(steps.post, async () => {
    await page.getByRole('button', { name: '投稿する' }).first().click()
    const dialog = page.getByRole('dialog', { name: '投稿を作成' })
    await dialog.getByRole('textbox', { name: '本文' }).fill(body)
    await dialog.getByRole('button', { name: '投稿する' }).click()
    await card.waitFor()
  })

  // 5. いいね → 表示が変わるまで
  await timed(steps.like, async () => {
    await card.getByRole('button', { name: 'いいね 0' }).click()
    await card.getByRole('button', { name: 'いいねを取り消す 1' }).waitFor()
  })

  // 6. 投稿の詳細でコメント → 一覧に出るまで
  await card.getByRole('link', { name: 'コメント 0' }).click()
  await timed(steps.comment, async () => {
    await page.getByRole('textbox', { name: 'コメント' }).fill('ブラウザの性能テストのコメント')
    await page.getByRole('button', { name: 'コメントする' }).click()
    await page
      .getByRole('region', { name: 'コメントの一覧' })
      .locator('article', { hasText: 'ブラウザの性能テストのコメント' })
      .waitFor()
  })

  // 7. 返信で @ に続けて打ち、メンションの候補が出るまで
  await page.getByRole('button', { name: '返信' }).click()
  await timed(steps.mention, async () => {
    await page.getByRole('textbox', { name: '返信' }).pressSequentially('@perf_00')
    await page
      .getByRole('listbox', { name: 'メンションの候補' })
      .getByRole('option')
      .first()
      .waitFor()
  })

  // 8. 自分のプロフィールへ
  await timed(steps.profile, async () => {
    await page.getByRole('link', { name: 'プロフィール', exact: true }).first().click()
    await page.locator('article').first().waitFor()
  })
}

// 1 回の繰り返し：新しいブラウザの画面（Cookie も新しい）で一連の操作を行う
export async function browserIteration(): Promise<void> {
  const context = await browser.newContext({ locale: 'ja-JP', timezoneID: 'Asia/Tokyo' })
  const page = await context.newPage()
  let completed = false
  try {
    await flow(page)
    completed = true
  } catch (error) {
    // 原因を調べられるよう、失敗したときの画面を結果のフォルダ（scripts/perf.ts が /results に渡す）に残す
    await page
      .screenshot({ path: `/results/failure-vu${__VU}-iter${__ITER}.png`, fullPage: true })
      .catch(() => undefined)
    console.error(`ブラウザの操作が途中で止まりました（VU ${__VU} 回 ${__ITER}）: ${String(error)}`)
  } finally {
    check(null, { ブラウザの一連の操作が最後まで進む: () => completed })
    await page.close()
    await context.close()
  }
}
