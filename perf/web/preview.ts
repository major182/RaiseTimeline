// 性能テスト用に、本番用のビルドの画面（frontend/dist）を配信する（性能テスト設計書 7 章）。scripts/perf.ts が起動する
// 画面の設定（frontend/vite.config.ts）は使わず、配信と転送の設定はすべてここで渡す（テストのために本体の設定を変えない）
//   環境変数：
//     FRONTEND_DIR      画面のフォルダ（dist と、Vite が入っている node_modules がある）
//     WEB_PORT          待ち受けるポート
//     API_ORIGIN        /api・/media の転送先（性能テスト用の API）
//     KEEP_ALIVE_SEC    使われない接続を閉じるまでの秒数。指定がなければ Node.js の既定（5 秒）のまま
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import type { Server } from 'node:http'

interface PreviewServer {
  httpServer: Server
}
interface ViteModule {
  preview: (config: object) => Promise<PreviewServer>
}

const frontendDir = process.env.FRONTEND_DIR ?? path.join('..', 'frontend')
const port = Number(process.env.WEB_PORT ?? 4192)
const apiOrigin = process.env.API_ORIGIN ?? 'http://localhost:8092'
const keepAliveSec = process.env.KEEP_ALIVE_SEC ? Number(process.env.KEEP_ALIVE_SEC) : undefined

// Vite は画面の側に入っているものを使う（画面のビルドと同じ版）
const vitePath = path.join(frontendDir, 'node_modules', 'vite', 'dist', 'node', 'index.js')
const { preview } = (await import(pathToFileURL(vitePath).href)) as ViteModule

const server = await preview({
  root: frontendDir,
  configFile: false,
  logLevel: 'info',
  preview: {
    port,
    strictPort: true,
    // k6 のブラウザは Docker の中から開くので、すべてのアドレスで待ち受け、host.docker.internal の名前を許す
    host: '0.0.0.0',
    allowedHosts: ['host.docker.internal'],
    // 画面から見て API が同じオリジンになるよう、/api・/media を性能テスト用の API に転送する
    proxy: { '/api': apiOrigin, '/media': apiOrigin },
  },
})

if (keepAliveSec !== undefined) {
  server.httpServer.keepAliveTimeout = keepAliveSec * 1000
  // 要求の見出しを待つ時間は、接続を保つ時間より長くする（Node.js の決まり）
  server.httpServer.headersTimeout = (keepAliveSec + 1) * 1000
}
console.log(
  `画面を配信しています（ポート ${port}、使われない接続を閉じるまで ${server.httpServer.keepAliveTimeout / 1000} 秒）`,
)
