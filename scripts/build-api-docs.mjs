// API 仕様書（docs/api/openapi.yaml）から、読みやすい HTML を作る（Redoc。API 設計書 5 章）
//   node scripts/build-api-docs.mjs
// 先に backend のテスト（./gradlew test）で openapi.yaml を書き出しておく。
// 作った HTML（docs/api/local/）は手元で見るためのもので、Git には入れない（.gitignore）
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const input = path.join(root, 'docs', 'api', 'openapi.yaml')
const output = path.join(root, 'docs', 'api', 'local', 'index.html')

// 版を固定して、毎回同じものを使う
const result = spawnSync('npx', ['--yes', '@redocly/cli@2.63.0', 'build-docs', input, '--output', output], {
  stdio: 'inherit',
  // Windows の npx は、シェルを通さないと起動できない
  shell: process.platform === 'win32',
})
if (result.status !== 0) {
  process.exit(result.status ?? 1)
}
console.log(`\n${path.relative(root, output)} を作りました。ブラウザで開いてください`)
