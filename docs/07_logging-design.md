# ログ設計書：タイムライン型 SNS（RaiseTimeline）

| 項目 | 内容 |
|---|---|
| 文書番号 | 07 |
| 版数 | 0.1 |
| 作成日 | 2026-10-18 |
| 作成者 | major182 |
| 前提となる文書 | [02 技術選定書](02_tech-stack.md)、[05 API 設計書](05_api-design.md) |

運用監視のための、サーバー（Spring Boot）のログの決まりごと。何を・どの重大度で・どんな形で・どこへ出すかを書く。
送り先には Datadog を想定し、Datadog が設定なしで読める属性の名前にそろえる（今は Datadog を使っていない）。

---

## 1. 方針

| 項目 | 内容 |
|---|---|
| 形式 | 1 件を 1 行の JSON にする（2 章）。開発（`dev` プロファイル）だけは、人が読みやすい今までの形式 |
| 出し先 | 標準出力だけ。ファイルには書かない。集めるのは動かす環境の側（7 章） |
| 重大度 | syslog の **Alert〜Info** の 6 段階（3 章）。Emergency と Debug は使わない |
| 作り方 | Spring Boot の構造化ログ（`logging.structured.format.console`）に、独自のフォーマッタ `DatadogJsonFormatter` を指定する |

## 2. JSON の属性

名前に「.」を含む属性は、入れ子のオブジェクトにする（例：`usr.id` → `{"usr":{"id":7}}`）。値がないときは属性ごと出さない。

| 属性 | 内容 | 例 |
|---|---|---|
| `timestamp` | 出した時刻（ISO 8601、UTC） | `2026-10-18T03:00:00.123Z` |
| `status` | 重大度（3 章） | `notice` |
| `message` | 人が読むための文 | `ログインに失敗しました: wrong_password` |
| `service` | サービスの名前（`spring.application.name`） | `raisetimeline` |
| `env` | 環境の名前（環境変数 `APP_ENV`。既定は `local`） | `prod` |
| `version` | アプリの版（環境変数 `APP_VERSION`。ないときは出さない） | `1.0.0` |
| `logger.name`・`logger.thread_name` | ログを出したクラス・スレッド | |
| `request_id` | リクエストの ID（4 章） | UUID |
| `usr.id` | 利用者の ID（ログインしていれば） | `7` |
| `http.method`・`http.url_details.path`・`http.status_code` | アクセスログ（4 章） | `GET`・`/api/posts`・`200` |
| `duration` | 処理にかかった時間（ナノ秒） | `12345678` |
| `network.client.ip` | 利用者の IP（ALB の `X-Forwarded-For` から） | |
| `evt.name`・`evt.outcome`・`evt.reason` | 出来事の種類・結果・理由（5 章） | `authentication`・`failure`・`wrong_password` |
| `error.kind`・`error.message`・`error.stack` | 例外の種類・メッセージ・スタックトレース | |

`service`・`env`・`version` は Datadog の統合タグ。Datadog の Agent 側でも付けられるが、アプリからも出しておく。

## 3. 重大度

SLF4J のレベルは ERROR・WARN・INFO までしかないため、Alert・Critical・Notice は印（`LogMarkers`）を付けて表す。
`status` の値は Datadog がそのまま重大度として読める。

| `status` | 書き方 | いつ使うか | 監視での扱い（Datadog を使うとき） |
|---|---|---|---|
| `alert` | `LOG.error(LogMarkers.ALERT, …)` | 直ちに人が対応する（乗っ取りの疑い） | 1 件で通知 |
| `critical` | `LOG.error(LogMarkers.CRITICAL, …)` | サービスの主要な機能が止まっている | 1 件で通知 |
| `error` | `LOG.error(…)` | 1 つのリクエストが想定外の理由で失敗した | 件数が増えたら通知 |
| `warning` | `LOG.warn(…)` | 処理は続けたが、後で確かめる | 日々の確認 |
| `notice` | `LOG.info(LogMarkers.NOTICE, …)` | 正常だが、監査のために残す | 件数の急増を見る（総当たりの検知） |
| `info` | `LOG.info(…)` | 普段の動き | 調べるときに見る |

Debug は出さない（`logging.level.root: INFO`）。

## 4. リクエストの ID とアクセスログ

- **リクエストの ID**：`X-Request-Id` ヘッダーがあればそれを使い、なければ UUID を作る。英数字と `.`・`_`・`-` の 64 文字までだけを受け取り、それ以外（改行など）は作り直す。応答の `X-Request-Id` ヘッダーで返し、そのリクエストの間のログすべてに `request_id` として付ける（`RequestLoggingFilter`）
- **利用者の ID**：アクセストークンを確かめた後に `usr.id` を付ける（`UserIdLoggingFilter`。Spring Security のフィルタの中に置く）
- **アクセスログ**：1 リクエストに 1 行、`info` で出す。`/actuator/health` は ALB が数秒ごとに呼ぶので出さない

## 5. 出す出来事

| `status` | 出来事 | `evt.name` | 出す場所 |
|---|---|---|---|
| `alert` | 使い終わったリフレッシュトークンが使われた（盗まれた疑い。その利用者のトークンをすべて無効にする） | `refresh_token_reuse` | `RefreshTokenService` |
| `critical` | 画像を保存先（S3）に書けない | ― | `ImageUploads` |
| `error` | 想定外の例外（500） | ― | `GlobalExceptionHandler` |
| `warning` | 画像のファイルを消せない（後で掃除する）、`FieldErrorCode` にない message | ― | `ImageUploads`、`GlobalExceptionHandler` |
| `notice` | ログインの失敗。`evt.reason` は `unknown_email`（登録のないメールアドレス）・`wrong_password`・`lock_started`（この失敗で一時停止になった）・`locked`（一時停止中） | `authentication`（`evt.outcome: failure`） | `AuthService` |
| `notice` | パスワードの変更 | `password_change` | `AuthService` |
| `info` | ログインの成功 | `authentication`（`evt.outcome: success`） | `AuthService` |
| `info` | 利用者の登録、ログアウト | `signup`、`logout` | `AuthService`、`RefreshTokenService` |
| `info` | アクセスログ（4 章）、古いリフレッシュトークンの削除（毎日） | ― | `RequestLoggingFilter`、`RefreshTokenService` |

## 6. 出さない情報

公開されたり、ログを見られる人が増えたりしても困らないよう、次のものは出さない。テスト（IT-CO-07）で確かめる。

| 出さないもの | 代わりに出すもの |
|---|---|
| パスワード、アクセストークン、リフレッシュトークン、Cookie、`Authorization` ヘッダー | なし |
| メールアドレス（個人情報） | `usr.id`。登録のないメールアドレスでのログインの失敗は、利用者が分からないので出さない |
| 投稿・コメントの本文 | 必要なら投稿の ID |

## 7. 環境ごとのログの行き先

| 環境 | 形式 | 行き先 |
|---|---|---|
| 開発（`./gradlew bootRun`） | 人が読む形式 | ターミナル。残さない |
| テスト（CI） | JSON | GitHub Actions の実行ログ（公開されるので 6 章を守る） |
| デプロイ後（検証） | JSON | EC2 の systemd → journald → CloudWatch Agent → CloudWatch Logs |
| リリース後（本番） | JSON | 検証と同じ。Datadog を使うときは、EC2 に Datadog Agent を入れて journald から送る（または CloudWatch Logs から Datadog Forwarder で送る） |

- 検証・本番での集め方（CloudWatch Agent・ロググループと保持期間・IAM）は、インフラ（Terraform）の作業で決める
- Datadog の API キーは秘密の置き場（パラメータストア）に置き、Agent の設定からだけ参照する。文書・リポジトリには書かない
- 環境変数 `APP_ENV`・`APP_VERSION` はデプロイのときに渡す

## 8. 改訂履歴

| 版数 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-10-18 | 初版（#78） |
