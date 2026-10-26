# 性能テスト

手で実行する性能テスト（CI では動かさない）。決まりごとと手順は [08 性能テスト設計書](../docs/08_performance-test.md) を見る。

```sh
node scripts/perf.ts smoke      # 実行し、終わったらテストデータと DB を消す
node scripts/perf.ts cleanup    # --keep-data で残したものを消す
```

| 場所               | 内容                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------------ |
| `k6/lib/`          | 共通の設定・しきい値（`config.ts`）、ログイン（`auth.ts`）、API の呼び出し（`actions.ts`） |
| `k6/scenarios/`    | シナリオ（PT-01〜）                                                                        |
| `seed/seed.sql`    | 目印つきのテストデータ                                                                     |
| `seed/cleanup.sql` | 目印のデータだけを消す                                                                     |
| `results/`         | 結果（コミットしない）                                                                     |

シナリオは TypeScript で書く。初回は `npm install` を行い、型と整形は `npm run check` で確かめる（`node scripts/check.ts` にも含まれる）。
