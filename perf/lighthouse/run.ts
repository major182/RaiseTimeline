// PT-06 web：Lighthouse で画面の表示の速さを測る（性能テスト設計書 3 章・6 章）。scripts/perf.ts web から動かす
// ほかの負荷はかけず、1 人で開いたときの値を見る。画面ごとに 3 回測り、中央値で判定する。
// ログインした後の画面も測るため、Playwright で起動したブラウザでログインしてから、同じブラウザに Lighthouse をつなぐ
//   環境変数：WEB_URL（画面の場所）、RESULT_DIR（結果の置き場所）、DIST_DIR（ビルドした画面。最初の JS の大きさを測る）
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { gzipSync } from 'node:zlib'
import lighthouse from 'lighthouse'
import desktopConfig from 'lighthouse/core/config/desktop-config.js'
import { chromium } from 'playwright-core'

const WEB_URL = process.env.WEB_URL ?? 'http://localhost:4192'
const RESULT_DIR = process.env.RESULT_DIR ?? '.'
const DIST_DIR = process.env.DIST_DIR ?? path.join('..', 'frontend', 'dist')
const DEBUG_PORT = 9223
const RUNS = 3

// 測るときの利用者（テストデータ。API・ブラウザの負荷の利用者とは別の番号）
const EMAIL = 'perf-0999@perf.example.com'
const PASSWORD = 'pass1234'

// しきい値（性能テスト設計書 6 章）
const MIN_SCORE = 90
const MAX_LCP_MS = 2500
const MAX_CLS = 0.1
const MAX_INITIAL_JS_KB = 300

interface PageResult {
  name: string
  url: string
  score: number
  lcpMs: number
  tbtMs: number
  cls: number
  passed: boolean
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.floor(sorted.length / 2)]
}

async function measure(name: string, url: string): Promise<PageResult> {
  const runs: { score: number; lcpMs: number; tbtMs: number; cls: number }[] = []
  for (let i = 0; i < RUNS; i++) {
    // ログインの状態（Cookie）を消さないよう、保存領域の初期化を止める
    const result = await lighthouse(
      url,
      { port: DEBUG_PORT, output: 'json', logLevel: 'error', disableStorageReset: true },
      desktopConfig,
    )
    if (!result) throw new Error(`${name} を測れませんでした`)
    const { categories, audits } = result.lhr
    runs.push({
      score: Math.round((categories.performance?.score ?? 0) * 100),
      lcpMs: Math.round(audits['largest-contentful-paint']?.numericValue ?? 0),
      tbtMs: Math.round(audits['total-blocking-time']?.numericValue ?? 0),
      cls: Number((audits['cumulative-layout-shift']?.numericValue ?? 0).toFixed(3)),
    })
    if (i === RUNS - 1) {
      writeFileSync(path.join(RESULT_DIR, `lighthouse-${name}.json`), JSON.stringify(result.lhr))
    }
  }
  const score = median(runs.map((r) => r.score))
  const lcpMs = median(runs.map((r) => r.lcpMs))
  const cls = median(runs.map((r) => r.cls))
  return {
    name,
    url,
    score,
    lcpMs,
    tbtMs: median(runs.map((r) => r.tbtMs)),
    cls,
    passed: score >= MIN_SCORE && lcpMs < MAX_LCP_MS && cls < MAX_CLS,
  }
}

// 最初に読み込む JS（index.html が読む script と modulepreload）の gzip 後の大きさ
function initialJsKb(): number {
  const html = readFileSync(path.join(DIST_DIR, 'index.html'), 'utf8')
  const files = [...html.matchAll(/(?:src|href)="\/(assets\/[^"]+\.js)"/g)].map((m) => m[1])
  const bytes = files.reduce(
    (sum, file) => sum + gzipSync(readFileSync(path.join(DIST_DIR, file))).length,
    0,
  )
  return Math.round(bytes / 1024)
}

async function main(): Promise<void> {
  const userDataDir = mkdtempSync(path.join(os.tmpdir(), 'perf-lighthouse-'))
  // Lighthouse がつなぐ既定の画面と Cookie を共有するよう、保存領域つきで起動する
  const context = await chromium.launchPersistentContext(userDataDir, {
    headless: true,
    args: [`--remote-debugging-port=${DEBUG_PORT}`],
  })
  const results: PageResult[] = []
  try {
    // ログインの画面（ログインする前に測る）
    results.push(await measure('login', `${WEB_URL}/login`))

    const page = context.pages()[0] ?? (await context.newPage())
    await page.goto(`${WEB_URL}/login`)
    await page.getByLabel('メールアドレス').fill(EMAIL)
    await page.getByLabel('パスワード', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'ログイン' }).click()
    await page.getByRole('heading', { name: 'ホーム' }).waitFor()

    results.push(await measure('timeline', `${WEB_URL}/`))
    results.push(await measure('profile', `${WEB_URL}/users/perf_0001`))
  } finally {
    await context.close()
    rmSync(userDataDir, { recursive: true, force: true })
  }

  const jsKb = initialJsKb()
  const summary = { results, initialJsKb: jsKb, initialJsPassed: jsKb < MAX_INITIAL_JS_KB }
  writeFileSync(path.join(RESULT_DIR, 'lighthouse-summary.json'), JSON.stringify(summary, null, 2))

  console.table(results)
  console.log(`最初の JS（gzip）: ${jsKb}KB（しきい値 ${MAX_INITIAL_JS_KB}KB 未満）`)
  const passed = results.every((r) => r.passed) && summary.initialJsPassed
  console.log(
    passed
      ? 'しきい値をすべて満たしました'
      : `しきい値を満たさない画面があります（Performance ${MIN_SCORE} 以上・LCP ${MAX_LCP_MS}ms 未満・CLS ${MAX_CLS} 未満）`,
  )
  process.exit(passed ? 0 : 99)
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error)
  process.exit(1)
})
