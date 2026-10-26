// 性能テストを手元で実行する（性能テスト設計書 7 章）。毎回の CI では動かさず、必要なときに実行する
//   node scripts/perf.mjs smoke              PT-01（1 人で 1 分）
//   node scripts/perf.mjs smoke --keep-data  終わってもテストデータと DB を残す（調査用）
//   node scripts/perf.mjs cleanup            残したテストデータと DB を消す
// 流れ：性能テスト用の DB（compose の db-perf）を起動 → API を本番に近い設定で起動（テーブルを作る）→ テストデータを入れる
//       → k6（Docker）でシナリオを実行 → 結果を perf/results/ に保存 → テストデータを消し、DB を volume ごと消す
// 成功・失敗・中断（Ctrl+C）のどれでも、最後に削除まで行う。先に Docker Desktop を起動しておく
import { spawn, spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { createWriteStream, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const isWindows = process.platform === 'win32'

const K6_IMAGE = 'grafana/k6:2.3.0'
const K6_CONTAINER = 'raisetimeline-perf-k6'
const API_PORT = 8092
const DB_PORT = 5434
const MEDIA_DIR = path.join(root, 'backend', 'build', 'perf-media')
const SCENARIOS = ['smoke']

const args = process.argv.slice(2)
const scenario = args.find((a) => !a.startsWith('--'))
const keepData = args.includes('--keep-data')

if (scenario !== 'cleanup' && !SCENARIOS.includes(scenario)) {
  console.error(`使い方: node scripts/perf.mjs <${[...SCENARIOS, 'cleanup'].join('|')}> [--keep-data]`)
  process.exit(2)
}

// Ctrl+C を受けても、すぐには終わらず削除まで行う（子のプロセスにも Ctrl+C は届き、それぞれ止まる）
let interrupted = false
process.on('SIGINT', () => {
  if (interrupted) return
  interrupted = true
  console.log('\n中断を受け付けました。テストデータを削除してから終わります')
})

function run(command, commandArgs, options = {}) {
  const result = spawnSync(command, commandArgs, { cwd: root, stdio: 'inherit', ...options })
  if (result.status !== 0 && !options.allowFailure) {
    throw new Error(`${command} ${commandArgs.join(' ')} が失敗しました（終了コード ${result.status}）`)
  }
  return result
}

function psql(file) {
  // SQL のファイルを標準入力で DB のコンテナの psql に渡す
  return run('docker', ['compose', '--profile', 'perf', 'exec', '-T', 'db-perf', 'psql', '-U', 'raisetimeline', '-d', 'raisetimeline', '-v', 'ON_ERROR_STOP=1'], {
    input: readFileSync(path.join(root, file)),
    stdio: ['pipe', 'inherit', 'inherit'],
  })
}

function dbVolume() {
  const result = spawnSync('docker', ['volume', 'ls', '-q', '--filter', 'label=com.docker.compose.volume=db-perf-data'], { cwd: root, encoding: 'utf8' })
  return result.stdout.trim().split('\n').filter(Boolean)
}

// 性能テスト用の DB が動いていて、テーブルができているか（API の起動前に止めたときは、消すデータがない）
function dbHasTables() {
  const result = spawnSync(
    'docker',
    ['compose', '--profile', 'perf', 'exec', '-T', 'db-perf', 'psql', '-U', 'raisetimeline', '-d', 'raisetimeline', '-tAc', "SELECT to_regclass('users') IS NOT NULL"],
    { cwd: root, encoding: 'utf8' },
  )
  return result.status === 0 && result.stdout.trim() === 't'
}

function startApi(logFile) {
  console.log(`\n=== API を起動（ポート ${API_PORT}。ログは ${path.relative(root, logFile)}） ===`)
  const log = createWriteStream(logFile)
  // 本番に近い設定で起動する。bootRun は dev を有効にするので、起動の引数で perf に置き換える
  // （JSON のログ・開発用のテストデータなし）。DevTools の再起動の見張りも止める。署名の鍵は毎回ランダムに作る
  const child = spawn(isWindows ? path.join(root, 'backend', 'gradlew.bat') : './gradlew', ['bootRun', '--args=--spring.profiles.active=perf'], {
    cwd: path.join(root, 'backend'),
    shell: isWindows,
    env: {
      ...process.env,
      SERVER_PORT: String(API_PORT),
      DB_URL: `jdbc:postgresql://localhost:${DB_PORT}/raisetimeline`,
      JWT_SECRET: randomBytes(32).toString('base64'),
      // 手元は HTTP なので、Cookie の Secure を外す
      APP_AUTH_COOKIESECURE: 'false',
      STORAGE_LOCAL_DIR: MEDIA_DIR,
      APP_ENV: 'perf',
      SPRING_DEVTOOLS_RESTART_ENABLED: 'false',
    },
  })
  child.stdout.pipe(log)
  child.stderr.pipe(log)
  return child
}

function stopApi(child) {
  if (!child || child.exitCode !== null) return
  if (isWindows) {
    // gradlew.bat の下の Java まで止める
    spawnSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
  } else {
    child.kill('SIGTERM')
  }
}

async function waitForApi(child, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (interrupted) throw new Error('中断しました')
    if (child.exitCode !== null) throw new Error('API が途中で止まりました。ログを確かめてください')
    try {
      const res = await fetch(`http://localhost:${API_PORT}/actuator/health`)
      if (res.ok) return
    } catch {
      // まだ起動していない
    }
    await new Promise((resolve) => setTimeout(resolve, 2000))
  }
  throw new Error('API が時間内に起動しませんでした')
}

function cleanup() {
  console.log('\n=== テストデータの削除 ===')
  spawnSync('docker', ['rm', '-f', K6_CONTAINER], { stdio: 'ignore' })
  if (dbHasTables()) {
    // 目印のデータだけを消す SQL を流し、残りが 0 件であることを確かめる（共有の環境と同じ手順を手元でも通す）
    psql('perf/seed/cleanup.sql')
  }
  // 性能テスト用の DB は volume ごと捨てる（開発用の db・E2E 用の db-e2e には触れない）
  run('docker', ['compose', '--profile', 'perf', 'rm', '--stop', '--force', 'db-perf'], { allowFailure: true })
  for (const volume of dbVolume()) {
    run('docker', ['volume', 'rm', volume], { allowFailure: true })
  }
  rmSync(MEDIA_DIR, { recursive: true, force: true })
  const left = dbVolume()
  if (left.length > 0) {
    throw new Error(`性能テスト用の DB の volume が残っています: ${left.join(', ')}`)
  }
  console.log('性能テスト用の DB（volume）と画像のフォルダを削除しました')
}

function recordEnvironment(file) {
  const commit = spawnSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: root, encoding: 'utf8' }).stdout.trim()
  writeFileSync(
    file,
    JSON.stringify(
      {
        scenario,
        startedAt: new Date().toISOString(),
        commit,
        os: `${os.type()} ${os.release()}`,
        cpu: os.cpus()[0]?.model,
        cpuCount: os.cpus().length,
        memoryGb: Math.round(os.totalmem() / 1024 ** 3),
        k6: K6_IMAGE,
      },
      null,
      2,
    ),
  )
}

async function main() {
  if (scenario === 'cleanup') {
    cleanup()
    return
  }
  if (dbVolume().length > 0) {
    throw new Error('前回の性能テストの DB が残っています。node scripts/perf.mjs cleanup で消してから実行してください')
  }

  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const resultDir = path.join(root, 'perf', 'results', `${stamp}-${scenario}`)
  mkdirSync(resultDir, { recursive: true })
  recordEnvironment(path.join(resultDir, 'environment.json'))

  let api
  let exitCode = 0
  try {
    console.log('\n=== 性能テスト用の DB を起動 ===')
    run('docker', ['compose', '--profile', 'perf', 'up', '-d', '--wait', 'db-perf'])

    api = startApi(path.join(resultDir, 'backend.log'))
    await waitForApi(api, 300_000)

    console.log('\n=== テストデータを投入 ===')
    psql('perf/seed/seed.sql')

    if (interrupted) throw new Error('中断しました')
    console.log(`\n=== k6 でシナリオ ${scenario} を実行 ===`)
    const k6 = run(
      'docker',
      [
        'run', '--rm', '--name', K6_CONTAINER,
        // Linux でも host.docker.internal で手元の API に届くようにする
        '--add-host', 'host.docker.internal:host-gateway',
        '-v', `${path.join(root, 'perf', 'k6')}:/scripts:ro`,
        '-v', `${resultDir}:/results`,
        '-e', `BASE_URL=http://host.docker.internal:${API_PORT}`,
        K6_IMAGE, 'run',
        '--summary-export', '/results/summary.json',
        `/scripts/scenarios/${scenario}.ts`,
      ],
      { allowFailure: true },
    )
    // k6 はしきい値を超えると 99 で終わる
    exitCode = k6.status ?? 1
    console.log(exitCode === 0 ? '\nしきい値をすべて満たしました' : `\nしきい値を満たさなかったか、実行に失敗しました（終了コード ${exitCode}）`)
    console.log(`結果: ${path.relative(root, resultDir)}`)
  } catch (error) {
    console.error(`\n${error.message}`)
    exitCode = 1
  } finally {
    stopApi(api)
    if (keepData) {
      console.log(`\n--keep-data のため、テストデータを残しました（DB: localhost:${DB_PORT}）。消すときは node scripts/perf.mjs cleanup`)
    } else {
      try {
        cleanup()
      } catch (error) {
        console.error(`\n削除に失敗しました: ${error.message}\nnode scripts/perf.mjs cleanup でやり直してください`)
        exitCode = exitCode || 1
      }
    }
  }
  process.exit(interrupted ? 130 : exitCode)
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
