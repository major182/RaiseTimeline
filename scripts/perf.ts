// 性能テストを手元で実行する（性能テスト設計書 7 章）。毎回の CI では動かさず、必要なときに実行する
//   node scripts/perf.ts smoke              PT-01（1 人で 1 分）
//   node scripts/perf.ts load|stress|spike|soak  PT-02〜05
//   node scripts/perf.ts load --short       時間を 1/10 に縮める。スクリプトが動くかの確認だけで、性能の合否には使わない
//   node scripts/perf.ts smoke --keep-data  終わってもテストデータと DB を残す（調査用）
//   node scripts/perf.ts cleanup            残したテストデータと DB を消す
// 流れ：性能テスト用の DB（compose の db-perf）を起動 → API を本番に近い設定で起動（テーブルを作る）→ テストデータを入れる
//       → k6（Docker）でシナリオを実行 → 結果を perf/results/ に保存 → テストデータを消し、DB を volume ごと消す
// 成功・失敗・中断（Ctrl+C）のどれでも、最後に削除まで行う。先に Docker Desktop を起動しておく
import {
  type ChildProcess,
  spawn,
  spawnSync,
  type SpawnSyncOptions,
  type SpawnSyncReturns,
} from 'node:child_process'
import { randomBytes } from 'node:crypto'
import {
  appendFileSync,
  createWriteStream,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs'
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
const SCENARIOS = ['smoke', 'load', 'stress', 'spike', 'soak'] as const

type Scenario = (typeof SCENARIOS)[number]
type Command = Scenario | 'cleanup'

function isCommand(value: string | undefined): value is Command {
  return value === 'cleanup' || SCENARIOS.some((s) => s === value)
}

const args = process.argv.slice(2)
const command = args.find((a) => !a.startsWith('--'))
const keepData = args.includes('--keep-data')
const short = args.includes('--short')

if (!isCommand(command)) {
  console.error(
    `使い方: node scripts/perf.ts <${[...SCENARIOS, 'cleanup'].join('|')}> [--keep-data] [--short]`,
  )
  process.exit(2)
}

// Ctrl+C を受けても、すぐには終わらず削除まで行う（子のプロセスにも Ctrl+C は届き、それぞれ止まる）
let interrupted = false
process.on('SIGINT', () => {
  if (interrupted) return
  interrupted = true
  console.log('\n中断を受け付けました。テストデータを削除してから終わります')
})

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

function run(
  cmd: string,
  cmdArgs: string[],
  { allowFailure = false, ...options }: SpawnSyncOptions & { allowFailure?: boolean } = {},
): SpawnSyncReturns<string | Buffer> {
  const result = spawnSync(cmd, cmdArgs, { cwd: root, stdio: 'inherit', ...options })
  if (result.status !== 0 && !allowFailure) {
    throw new Error(`${cmd} ${cmdArgs.join(' ')} が失敗しました（終了コード ${result.status}）`)
  }
  return result
}

const PSQL = [
  'compose',
  '--profile',
  'perf',
  'exec',
  '-T',
  'db-perf',
  'psql',
  '-U',
  'raisetimeline',
  '-d',
  'raisetimeline',
]

function psql(file: string): void {
  // SQL のファイルを標準入力で DB のコンテナの psql に渡す
  run('docker', [...PSQL, '-v', 'ON_ERROR_STOP=1'], {
    input: readFileSync(path.join(root, file)),
    stdio: ['pipe', 'inherit', 'inherit'],
  })
}

function psqlCommand(sql: string): string {
  const result = spawnSync('docker', [...PSQL, '-v', 'ON_ERROR_STOP=1', '-c', sql], {
    cwd: root,
    encoding: 'utf8',
  })
  if (result.status !== 0) {
    throw new Error(`SQL が失敗しました: ${result.stderr}`)
  }
  return result.stdout
}

// 時間のかかる k6 は、実行中にサーバーの状態を記録できるよう、待つ間も処理を止めずに動かす
function runAsync(cmd: string, cmdArgs: string[]): Promise<number> {
  return new Promise((resolve) => {
    const child = spawn(cmd, cmdArgs, { cwd: root, stdio: 'inherit' })
    child.on('exit', (code) => resolve(code ?? 1))
    child.on('error', () => resolve(1))
  })
}

// 実行中に記録するサーバーの状態（Actuator の metrics。perf プロファイルだけ公開）
const SAMPLED_METRICS: { key: string; name: string; tag?: string }[] = [
  { key: 'dbActive', name: 'hikaricp.connections.active' },
  { key: 'dbPending', name: 'hikaricp.connections.pending' },
  { key: 'dbMax', name: 'hikaricp.connections.max' },
  { key: 'heapUsedMb', name: 'jvm.memory.used', tag: 'area:heap' },
  { key: 'threads', name: 'jvm.threads.live' },
  { key: 'cpu', name: 'process.cpu.usage' },
]

async function sampleMetrics(): Promise<Record<string, number | string>> {
  const sample: Record<string, number | string> = { at: new Date().toISOString() }
  for (const metric of SAMPLED_METRICS) {
    const query = metric.tag ? `?tag=${metric.tag}` : ''
    const res = await fetch(`http://localhost:${API_PORT}/actuator/metrics/${metric.name}${query}`)
    if (!res.ok) continue
    const body = (await res.json()) as { measurements: { value: number }[] }
    const value = body.measurements[0]?.value ?? 0
    sample[metric.key] = metric.key === 'heapUsedMb' ? Math.round(value / 1024 ** 2) : value
  }
  return sample
}

// 15 秒ごとに 1 行の JSON を追記する（長時間のシナリオで、メモリ・DB の接続の増え方を見る）
function startMetricsSampler(file: string): NodeJS.Timeout {
  return setInterval(() => {
    sampleMetrics()
      .then((sample) => appendFileSync(file, `${JSON.stringify(sample)}\n`))
      .catch(() => {
        // API が止まっていれば記録しない（k6 の結果に失敗が出る）
      })
  }, 15_000)
}

// 合計の時間が長い SQL の上位 20 件（遅い API の原因を探す入り口。性能テスト設計書 7 章）
function saveSlowQueries(file: string): void {
  const sql = `SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time::numeric, 2) AS mean_ms,
                      round(max_exec_time::numeric, 2) AS max_ms, rows, left(regexp_replace(query, '\\s+', ' ', 'g'), 300) AS query
               FROM pg_stat_statements
               WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
               ORDER BY total_exec_time DESC LIMIT 20`
  try {
    writeFileSync(file, psqlCommand(sql))
  } catch (error) {
    console.error(`遅い SQL を保存できませんでした: ${message(error)}`)
  }
}

function dbVolumes(): string[] {
  const result = spawnSync(
    'docker',
    ['volume', 'ls', '-q', '--filter', 'label=com.docker.compose.volume=db-perf-data'],
    { cwd: root, encoding: 'utf8' },
  )
  return result.stdout.trim().split('\n').filter(Boolean)
}

// 性能テスト用の DB が動いていて、テーブルができているか（API の起動前に止めたときは、消すデータがない）
function dbHasTables(): boolean {
  const result = spawnSync('docker', [...PSQL, '-tAc', "SELECT to_regclass('users') IS NOT NULL"], {
    cwd: root,
    encoding: 'utf8',
  })
  return result.status === 0 && result.stdout.trim() === 't'
}

function startApi(logFile: string): ChildProcess {
  console.log(`\n=== API を起動（ポート ${API_PORT}。ログは ${path.relative(root, logFile)}） ===`)
  const log = createWriteStream(logFile)
  // 本番に近い設定で起動する。bootRun は dev を有効にするので、起動の引数で perf に置き換える
  // （JSON のログ・開発用のテストデータなし）。DevTools の再起動の見張りも止める。署名の鍵は毎回ランダムに作る
  const child = spawn(
    isWindows ? path.join(root, 'backend', 'gradlew.bat') : './gradlew',
    ['bootRun', '--args=--spring.profiles.active=perf'],
    {
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
    },
  )
  child.stdout.pipe(log)
  child.stderr.pipe(log)
  return child
}

function stopApi(child: ChildProcess | undefined): void {
  if (!child || child.exitCode !== null) return
  if (isWindows) {
    // gradlew.bat の下の Java まで止める
    spawnSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
  } else {
    child.kill('SIGTERM')
  }
}

async function waitForApi(child: ChildProcess, timeoutMs: number): Promise<void> {
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

function cleanup(): void {
  console.log('\n=== テストデータの削除 ===')
  spawnSync('docker', ['rm', '-f', K6_CONTAINER], { stdio: 'ignore' })
  if (dbHasTables()) {
    // 目印のデータだけを消す SQL を流し、残りが 0 件であることを確かめる（共有の環境と同じ手順を手元でも通す）
    psql('perf/seed/cleanup.sql')
  }
  // 性能テスト用の DB は volume ごと捨てる（開発用の db・E2E 用の db-e2e には触れない）
  run('docker', ['compose', '--profile', 'perf', 'rm', '--stop', '--force', 'db-perf'], {
    allowFailure: true,
  })
  for (const volume of dbVolumes()) {
    run('docker', ['volume', 'rm', volume], { allowFailure: true })
  }
  rmSync(MEDIA_DIR, { recursive: true, force: true })
  const left = dbVolumes()
  if (left.length > 0) {
    throw new Error(`性能テスト用の DB の volume が残っています: ${left.join(', ')}`)
  }
  console.log('性能テスト用の DB（volume）と画像のフォルダを削除しました')
}

function recordEnvironment(file: string, scenario: Scenario): void {
  const commit = spawnSync('git', ['rev-parse', '--short', 'HEAD'], {
    cwd: root,
    encoding: 'utf8',
  }).stdout.trim()
  const environment = {
    scenario,
    startedAt: new Date().toISOString(),
    commit,
    os: `${os.type()} ${os.release()}`,
    cpu: os.cpus()[0]?.model,
    cpuCount: os.cpus().length,
    memoryGb: Math.round(os.totalmem() / 1024 ** 3),
    k6: K6_IMAGE,
    // true なら時間を縮めた動作確認。性能の合否・記録には使わない
    short,
  }
  writeFileSync(file, JSON.stringify(environment, null, 2))
}

async function runScenario(scenario: Scenario): Promise<number> {
  if (dbVolumes().length > 0) {
    throw new Error(
      '前回の性能テストの DB が残っています。node scripts/perf.ts cleanup で消してから実行してください',
    )
  }

  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const resultDir = path.join(root, 'perf', 'results', `${stamp}-${scenario}`)
  mkdirSync(resultDir, { recursive: true })
  recordEnvironment(path.join(resultDir, 'environment.json'), scenario)

  let api: ChildProcess | undefined
  let exitCode = 0
  try {
    console.log('\n=== 性能テスト用の DB を起動 ===')
    run('docker', ['compose', '--profile', 'perf', 'up', '-d', '--wait', 'db-perf'])

    api = startApi(path.join(resultDir, 'backend.log'))
    await waitForApi(api, 300_000)

    console.log('\n=== テストデータを投入 ===')
    psql('perf/seed/seed.sql')
    // データの投入の SQL を集計から外し、シナリオの SQL だけを数える
    psqlCommand(
      'CREATE EXTENSION IF NOT EXISTS pg_stat_statements; SELECT pg_stat_statements_reset();',
    )

    if (interrupted) throw new Error('中断しました')
    console.log(
      `\n=== k6 でシナリオ ${scenario} を実行${short ? '（--short：時間を 1/10 に縮める）' : ''} ===`,
    )
    const sampler = startMetricsSampler(path.join(resultDir, 'metrics.jsonl'))
    // prettier-ignore
    const k6Status = await runAsync('docker', [
      'run', '--rm', '--name', K6_CONTAINER,
      // Linux でも host.docker.internal で手元の API に届くようにする
      '--add-host', 'host.docker.internal:host-gateway',
      '-v', `${path.join(root, 'perf', 'k6')}:/scripts:ro`,
      '-v', `${resultDir}:/results`,
      '-e', `BASE_URL=http://host.docker.internal:${API_PORT}`,
      '-e', `TIME_SCALE=${short ? 0.1 : 1}`,
      K6_IMAGE, 'run',
      '--summary-export', '/results/summary.json',
      `/scripts/scenarios/${scenario}.ts`,
    ])
    clearInterval(sampler)
    saveSlowQueries(path.join(resultDir, 'slow-queries.txt'))
    // k6 はしきい値を超えると 99 で終わる
    exitCode = k6Status
    console.log(
      exitCode === 0
        ? '\nしきい値をすべて満たしました'
        : `\nしきい値を満たさなかったか、実行に失敗しました（終了コード ${exitCode}）`,
    )
    if (short) {
      console.log(
        '--short は動作確認です。時間が短く負荷のたまり方が本来と違うため、性能の合否・記録には使わないでください',
      )
    }
    console.log(`結果: ${path.relative(root, resultDir)}`)
  } catch (error) {
    console.error(`\n${message(error)}`)
    exitCode = 1
  } finally {
    stopApi(api)
    if (keepData) {
      console.log(
        `\n--keep-data のため、テストデータを残しました（DB: localhost:${DB_PORT}）。消すときは node scripts/perf.ts cleanup`,
      )
    } else {
      try {
        cleanup()
      } catch (error) {
        console.error(
          `\n削除に失敗しました: ${message(error)}\nnode scripts/perf.ts cleanup でやり直してください`,
        )
        exitCode = exitCode || 1
      }
    }
  }
  return exitCode
}

async function main(cmd: Command): Promise<void> {
  if (cmd === 'cleanup') {
    cleanup()
    return
  }
  const exitCode = await runScenario(cmd)
  process.exit(interrupted ? 130 : exitCode)
}

main(command).catch((error: unknown) => {
  console.error(message(error))
  process.exit(1)
})
