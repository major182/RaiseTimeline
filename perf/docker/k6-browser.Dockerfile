# 性能テストのブラウザ（PT-07・PT-08）用の k6。公式のブラウザ入りイメージに日本語のフォントを足す
# 公式のイメージには日本語のフォントがなく、画面の日本語が描画されないため（文字の描画の重さが本番と変わる）
# scripts/perf.ts がビルドする（性能テスト設計書 2 章）
FROM grafana/k6:2.3.0-with-browser
USER root
RUN apk add --no-cache font-noto-cjk
USER k6
