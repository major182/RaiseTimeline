// URL で読み込む外部のモジュールの型（k6 が実行のときに取ってくる。npm には入っていない）
declare module 'https://raw.githubusercontent.com/benc-uk/k6-reporter/3.0.4/dist/bundle.js' {
  export function htmlReport(data: object, options?: { title?: string; theme?: string }): string
}

declare module 'https://jslib.k6.io/k6-summary/0.1.0/index.js' {
  export function textSummary(
    data: object,
    options?: { indent?: string; enableColors?: boolean },
  ): string
}
