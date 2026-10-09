// backend と frontend のチェックをまとめて実行する（コミット・プッシュの前に使う）
//   node scripts/check.mjs
// backend：整形（Spotless）・lint（Checkstyle）・テスト。テストは Docker を使うので、先に Docker Desktop を起動しておく
// frontend：型・lint（ESLint）・整形（Prettier）
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const isWindows = process.platform === 'win32'

const steps = [
  { name: 'backend', cwd: 'backend', command: isWindows ? path.join(root, 'backend', 'gradlew.bat') : './gradlew', args: ['check'] },
  { name: 'frontend', cwd: 'frontend', command: 'npm', args: ['run', 'check'] },
]

for (const step of steps) {
  console.log(`\n=== ${step.name} ===`)
  // Windows の .bat と npm は、シェルを通さないと起動できない
  const result = spawnSync(step.command, step.args, {
    cwd: path.join(root, step.cwd),
    stdio: 'inherit',
    shell: isWindows,
  })
  if (result.status !== 0) {
    console.error(`\n${step.name} のチェックが失敗しました`)
    process.exit(result.status ?? 1)
  }
}
console.log('\nすべてのチェックが通りました')
