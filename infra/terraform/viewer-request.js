// CloudFront Functions（デプロイ設計書 1 章）。開発者の IP だけを通し、画面の URL を index.html に書き換える
function handler(event) {
  var request = event.request
  if (event.viewer.ip !== '${allowed_ip}') {
    return { statusCode: 403, statusDescription: 'Forbidden' }
  }
  // /api/ と、拡張子のあるファイル（/assets/index.js など）はそのまま
  if (!request.uri.startsWith('/api/') && request.uri.indexOf('.') === -1) {
    request.uri = '/index.html'
  }
  return request
}
