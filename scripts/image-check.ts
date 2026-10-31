// 本番のイメージを作り、PostgreSQL につないで起動を確かめる（デプロイ設計書 5 章）。CI の image ジョブでも動かす
//   node scripts/image-check.ts
import { spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const IMAGE = 'raisetimeline-api:check'
const NETWORK = 'raisetimeline-image-check'
const DB = 'raisetimeline-image-check-db'
const APP = 'raisetimeline-image-check-app'
const PORT = 8093
const DB_PASSWORD = randomBytes(16).toString('hex')

function docker(args: string[], quiet = false): number {
  const result = spawnSync('docker', args, { stdio: quiet ? 'ignore' : 'inherit' })
  return result.status ?? 1
}

function mustDocker(args: string[]): void {
  if (docker(args) !== 0) throw new Error(`docker ${args[0]} に失敗しました`)
}

function appArgs(extra: string[]): string[] {
  // prettier-ignore
  return [
    '--network', NETWORK,
    '-e', `DB_URL=jdbc:postgresql://${DB}:5432/raisetimeline`,
    '-e', 'DB_USER=raisetimeline',
    '-e', `DB_PASSWORD=${DB_PASSWORD}`,
    '-e', `JWT_SECRET=${randomBytes(32).toString('base64')}`,
    '-e', 'AWS_REGION=ap-northeast-1',
    // 起動時に CSP 用の署名つき URL を作るので、形だけの値を渡す（本番はタスクロール。AWS にはつながない）
    '-e', 'AWS_ACCESS_KEY_ID=check',
    '-e', 'AWS_SECRET_ACCESS_KEY=check',
    ...extra,
    IMAGE,
  ]
}

async function waitHealthy(): Promise<void> {
  for (let i = 0; i < 90; i++) {
    try {
      const res = await fetch(`http://localhost:${PORT}/actuator/health`)
      if (res.ok) return
    } catch {
      // 起動中はつながらない
    }
    await new Promise((resolve) => setTimeout(resolve, 2000))
  }
  throw new Error('API が起動しませんでした')
}

function cleanup(): void {
  docker(['rm', '-f', APP, DB], true)
  docker(['network', 'rm', NETWORK], true)
}

try {
  cleanup()
  mustDocker(['build', '-t', IMAGE, path.join(root, 'backend')])
  mustDocker(['network', 'create', NETWORK])
  // prettier-ignore
  mustDocker([
    'run', '-d', '--name', DB, '--network', NETWORK,
    '-e', 'POSTGRES_DB=raisetimeline', '-e', 'POSTGRES_USER=raisetimeline', '-e', `POSTGRES_PASSWORD=${DB_PASSWORD}`,
    'postgres:18.6',
  ])
  for (
    let i = 0;
    i < 30 && docker(['exec', DB, 'pg_isready', '-U', 'raisetimeline'], true) !== 0;
    i++
  ) {
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }

  // S3_BUCKET を渡し忘れたら起動しない
  if (docker(['run', '--rm', ...appArgs([])], true) === 0) {
    throw new Error('S3_BUCKET がないのに起動が止まりませんでした')
  }
  console.log('OK: S3_BUCKET がないと起動しない')

  mustDocker([
    'run',
    '-d',
    '--name',
    APP,
    '-p',
    `${PORT}:8080`,
    ...appArgs(['-e', 'S3_BUCKET=check']),
  ])
  await waitHealthy()
  console.log('OK: 起動し、Flyway が反映され、ヘルスチェックが通る')

  // 画面は入れない
  const index = await fetch(`http://localhost:${PORT}/index.html`)
  if (index.ok) throw new Error('イメージに画面が入っています')
  console.log('OK: 画面が入っていない')
} catch (error) {
  docker(['logs', '--tail', '40', APP])
  console.error(error)
  process.exitCode = 1
} finally {
  cleanup()
}
