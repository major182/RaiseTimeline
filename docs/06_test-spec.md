# テスト仕様書：タイムライン型 SNS（RaiseTimeline）

| 項目 | 内容 |
|---|---|
| 文書番号 | 06 |
| 版数 | 0.5 |
| 作成日 | 2026-10-18 |
| 作成者 | major182 |
| 前提となる文書 | [01 要件定義書](01_requirements.md)、[03 DB 設計書](03_db-design.md)、[04 画面設計書](04_screen-design.md)、[05 API 設計書](05_api-design.md) |

**何を、どの種類のテストで、どんな考え方で確かめるか**を決める文書。
一つひとつの入力値と期待する結果は、テストのコードに書く（コードが正）。ここには、テストの区分・観点の出し方・観点の一覧と、どの要件をどのテストで確かめるかの対応を書く。

---

## 1. テストの区分

| 区分 | 何を確かめるか | 差し替えるもの | backend | frontend |
|---|---|---|---|---|
| **単体テスト（UT）** | 1 つのクラス・関数・コンポーネントの中の**処理の分かれ道と計算** | DB・他のクラス・API の呼び出し（モック） | JUnit 5 + Mockito（`@ExtendWith(MockitoExtension.class)`）。Spring を起動しない | Vitest + Testing Library。API は `vi.mock` で差し替える |
| **結合テスト（IT）** | 層をつないだときに、**設計書どおりの外から見える動き**になるか | 差し替えない（本物の DB を使う）。外部サービス（S3）だけ差し替える | `@SpringBootTest` + MockMvc + Testcontainers（PostgreSQL）。HTTP の要求から DB まで通す | ページ単位で、複数のコンポーネント・TanStack Query・ルーターをつなぎ、API の応答だけを差し替える |
| E2E（任意・未導入） | ブラウザで画面と API を通して、主な利用の流れが動くか | なし | ― | 導入するかは後で決める（今は Chrome DevTools で手で確かめる） |

**役割の分け方**：入力の境界・分岐の組み合わせは数が多いので**単体テストで細かく**、HTTP の状態コード・権限・DB の制約・トランザクションは**結合テストで代表的な値**を確かめる。同じ観点を両方で細かく繰り返さない。

### 1.1 開発の進め方

機能ごとに **開発 → 単体テスト → 開発 → 単体・結合テスト** と回す。

1. サービス・関数を作ったら、その単体テストを書いて通す
2. コントローラー・画面までつないだら、結合テストを書いて通す
3. テストを足したら、この文書の観点表（4 章）に ID を足す。コードと同じ PR に含める

---

## 2. 観点の出し方（テストの技法）

| 技法 | 種類 | 使う場面 | このプロジェクトでの例 |
|---|---|---|---|
| **分岐網羅（C1）** | ホワイトボックス | 単体テスト。`if`・`switch`・例外のすべての分かれ道を、通る／通らないの両方で 1 回以上通す | `RefreshTokenService` のトークンが「有効／期限切れ／無効にした後」の分かれ道 |
| **命令網羅（C0）** | ホワイトボックス | カバレッジの計測で、一度も動いていない行を見つける | 5 章のカバレッジ |
| **同値分割** | ブラックボックス | 入力を「同じ結果になるグループ」に分け、グループごとに代表の値を 1 つ選ぶ | ユーザー名：正しい形式／使えない文字を含む／長さが範囲外 |
| **境界値分析** | ブラックボックス | 範囲の端（最小・最大）と、そのすぐ外の値を確かめる | 本文 280 文字は OK・281 文字は NG |
| **デシジョンテーブル** | ブラックボックス | 条件の組み合わせで結果が変わるとき、組み合わせを表にして全部確かめる | コメントの削除：コメントした本人か × 投稿した本人か |
| **状態遷移** | ブラックボックス | 操作の順番で結果が変わるとき、状態と操作の組を確かめる | ログイン → 取り直し → ログアウト → 取り直し（401） |
| **エラー推測** | ブラックボックス | 経験から間違いやすい所を狙う | 絵文字の文字数、二重のいいね、同時の取り直し |

---

## 3. テストの名前の付け方

| 項目 | 決めごと |
|---|---|
| 観点の ID | `<区分>-<機能>-<連番>`。区分は `UT`・`IT`、機能は要件定義書の機能 ID（`AU`・`US`・`PO`・`TL`・`CM`・`LK`・`FL`）と、共通の部品の `CO`（common）。例：`UT-PO-01` |
| backend の単体テスト | テストのメソッドに `@DisplayName("UT-PO-01 本文が 280 文字なら投稿できる")` のように、ID と日本語の説明を付ける |
| backend の結合テスト | クラスに `@DisplayName("IT-PO-01・IT-PO-04 投稿の編集")` のように、確かめる観点の ID を付ける（1 つのクラスで観点の中の多くの場合を確かめ、メソッド名で場合を表すため） |
| frontend | `it("UT-PO-01 本文が 280 文字なら投稿できる", …)` のように、ID を説明の先頭に付ける |
| 1 つの ID に複数のテスト | 境界値の両側など、同じ観点を複数のテストで確かめてよい。同じ ID を付ける |

---

## 4. 観点表

列の意味：**技法**は 2 章の技法、**場所**はテストのクラス・ファイル（UT の backend はサービス名 + `UnitTests`、frontend は対象のファイル名 + `.test.ts(x)`）。

### 4.1 認証（F-AU）

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-AU-01 | ユーザー名：3・4・15・16 文字、使えない文字（記号・全角）。メールアドレス・ユーザー名の重複（同時の登録で DB の制約に当たった場合を含む） | 境界値・同値分割 | BR-02、BR-04 | frontend `auth/validation`、backend `RequestValidationTests`・`AuthServiceUnitTests` |
| UT-AU-02 | パスワード：7・8・72・73 文字、英字だけ・数字だけ、確認用と不一致（登録・変更） | 境界値・同値分割 | BR-05、BR-09 | frontend `auth/validation`、backend `RequestValidationTests` |
| UT-AU-03 | 表示名：0・1・50・51 文字 | 境界値 | BR-03 | 同上 |
| UT-AU-04 | ログインの失敗回数：4 回目までは LOGIN_FAILED・5 回目で LOGIN_LOCKED・止めている間は照合しない・15 分ちょうどで戻る・成功で回数が 0 に戻る・未登録のメールアドレスも同じ LOGIN_FAILED | 境界値・分岐網羅 | BR-08、BR-09 | `AuthServiceUnitTests`、`UserUnitTests` |
| UT-AU-05 | リフレッシュトークン：ない・知らない・期限切れ（期限ちょうどを含む）は拒否、有効なら新しいトークンに交換、取り直しで使い終わったトークンが来たら全トークンを無効化、ログアウト後のトークンは拒否だけ | 分岐網羅・状態遷移 | BR-07 | `RefreshTokenServiceUnitTests` |
| UT-AU-06 | パスワードの変更：今のパスワードが違う・正しい、今の端末以外のトークンを無効化（今の端末のトークンがなければ全部）、利用者が消えていたら UNAUTHENTICATED | 分岐網羅 | BR-05 | `AuthServiceUnitTests`、`RefreshTokenServiceUnitTests` |
| UT-AU-07 | 画面のセッション：起動直後はリフレッシュトークンで取り直す（取り直せなければログインしていない）・期限切れの 401 で取り直しを 1 回だけ呼ぶ・同時に呼ばれてもまとめる・UNAUTHENTICATED のときだけ期限切れの知らせを出す（LOGIN_FAILED では出さない、二重に出さない）・ログアウトの通信に失敗してもトークンを捨てる | 状態遷移・デシジョンテーブル・エラー推測 | BR-07 | frontend `api/client`・`api/auth`・`auth/session` |
| IT-AU-01 | 登録：成功（201・Cookie）、形式の誤り（400・`errors`）、メールアドレス／ユーザー名の重複（409） | 同値分割 | BR-01〜05、BR-09 | `SignupTests` |
| IT-AU-02 | ログイン：成功、メールアドレスかパスワードが違う（同じ `LOGIN_FAILED`）、一時停止中（`LOGIN_LOCKED`） | 同値分割 | BR-04、BR-08、BR-09 | `LoginTests` |
| IT-AU-03 | 取り直し・ログアウトの状態遷移：ログイン → 取り直し → 古いトークンで取り直し（401）→ ログアウト → 取り直し（401）。`X-Requested-With` がない（403） | 状態遷移 | BR-07 | `RefreshCookieTests` |
| IT-AU-04 | ログインが要る API は、どれもトークンなしで 401（API 設計書 4 章の 25 の API をすべて並べる）。期限切れ・発行者違い・署名違いも 401。要らない API（ヘルスチェックなど）はログインなしで使える | デシジョンテーブル | BR-06 | `SecurityConfigTests` |
| IT-AU-05 | 画面：未ログインで保護された URL を開くとログイン画面、ログイン済みでログイン画面を開くとホーム | 状態遷移 | BR-06 | frontend `auth/RouteGuards`、`pages/Login`・`Signup` |
| IT-AU-06 | パスワードの変更：新しいパスワードでだけログインできる・ほかの端末は取り直せなくなり今の端末は続けて使える・今のパスワードが違えば変えない | 状態遷移・同値分割 | BR-05 | `ChangePasswordTests` |

### 4.2 利用者・プロフィール（F-US）

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-US-01 | プロフィールの編集：送った項目だけ変わる・ユーザー名を送らなければ重複を確かめない・他人が使っている名前は USERNAME_TAKEN（同時の変更を含む） | 分岐網羅・同値分割 | BR-02、BR-03 | `UserServiceUnitTests`、`UserUnitTests` |
| UT-US-02 | 自己紹介：160・161 文字。アイコン：ファイルがない（REQUIRED）、変えたら前のファイルを消す（前がなければ消さない） | 境界値・分岐網羅 | BR-24、BR-25 | `RequestValidationTests`、`UserServiceUnitTests`、frontend `pages/ProfileEdit` |
| UT-US-03 | 検索語：空・空白だけ・1・50・51 文字（絵文字は 1 文字）。`%`・`_`・`\` はただの文字として探す | 境界値・エラー推測 | API 05 2.4 | `UserServiceUnitTests` |
| UT-US-04 | おすすめ：フォロー中の人がフォローしている人で 5 人そろえばそのまま、足りなければ選んだ人を除いて最近登録した人で埋める | 境界値・分岐網羅 | 4.1 候補 | `UserServiceUnitTests` |
| IT-US-01 | プロフィールの取得：ユーザー名（大文字違い）・ID・いない利用者（404）。他人のメールアドレスを返さない | 同値分割 | BR-02 | `ProfileTests`、`UserSummaryTests` |
| IT-US-02 | アイコンの変更：画像あり・`file` なし（400）・形式違い（415）・5MB 超（413）、古いファイルが消える | 同値分割 | BR-24 | `AvatarTests` |
| IT-US-03 | 検索・おすすめ（自分・フォロー済みを除く）・プロフィールの編集の API | 同値分割 | BR-02、BR-03 | `UserSearchTests`、`RecommendationTests`、`UpdateProfileTests` |

### 4.3 投稿・画像（F-PO）

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-PO-01 | 本文の文字数：0・1・280・281 文字。絵文字は 1 文字、組み合わせの絵文字は複数文字 | 境界値・エラー推測 | BR-10 | backend `RequestValidationTests`・`CodePointLengthValidatorTests`、frontend `postLength` |
| UT-PO-02 | 本文と画像の組み合わせ：本文あり／なし（空白だけ・送られないを含む）× 画像あり／なし（両方なしだけ `POST_EMPTY`）。編集で本文を空にできるのは画像があるときだけ | デシジョンテーブル | BR-11、BR-15 | `PostServiceUnitTests` |
| UT-PO-03 | 画像の枚数：0・4・5 枚 | 境界値 | BR-20 | 同上 |
| UT-PO-04 | 画像の中身の判定：JPEG・PNG・WebP・GIF、拡張子だけ偽った別の形式、壊れたファイル | 同値分割・エラー推測 | BR-21 | `ImageInspectorTests` |
| UT-PO-05 | 編集・削除の権限：本人／他人／ない投稿。削除では画像のファイルを DB の確定後に消すよう頼む。画面の削除の確認は、最初に「キャンセル」に焦点があり、Esc はキャンセル扱い | 分岐網羅・エラー推測 | BR-12、BR-13、NF-SE-04 | `PostServiceUnitTests`、frontend `ConfirmDialog` |
| UT-PO-06 | 画像が 1 枚でも正しくなければ、投稿も画像も保存しない | 分岐網羅・エラー推測 | API 05 4.4 | `PostServiceUnitTests` |
| UT-PO-07 | 画面での画像の縮小：長辺が 2048px を超える（縦長・横長・2049px）・ちょうど・下回る、アイコンは 400px。描き直して 5MB を超えた PNG は WebP に、それでも超えたら・JPEG なら E-11。GIF は描き直さない | 境界値・分岐網羅 | BR-23 | frontend `imageAttach` |
| IT-PO-01 | 投稿・詳細・編集・削除の権限：本人／他人／未ログイン／存在しない投稿 → 成功／403／401／404（未ログインは IT-AU-04 で全 API をまとめて確かめ、編集では本文が変わらないことも確かめる） | デシジョンテーブル | BR-12、NF-SE-04 | `CreatePostTests`、`GetPostTests`、`UpdatePostTests`、`DeletePostTests`、`SecurityConfigTests` |
| IT-PO-02 | 削除でコメント・いいね・画像も消える | 同値分割 | BR-13 | `DeletePostTests` |
| IT-PO-03 | 画像のアップロード：5MB ちょうど・超え（413）、形式違い（415） | 境界値 | BR-21、BR-22 | `PostImageTests`、`ImageUploadsTests` |
| IT-PO-04 | 編集で「編集済み」になり、投稿日時・画像・いいね・コメントが残る | 同値分割 | BR-15、BR-16 | `UpdatePostTests`、`PostImageTests` |

### 4.4 タイムライン（F-TL）

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-TL-01 | 留守中のハイライト：初めて開く・前回から 6 時間未満・6 時間以上。ハイライトに出した投稿を下の一覧から除く | 境界値・分岐網羅 | BR-52、BR-52-1 | `TimelineServiceUnitTests` |
| UT-TL-02 | 続きの読み込み（カーソルあり）では、ハイライトを判定せず時刻も記録しない・同じ基準の時刻で続きから読む。読み込みの間に消された投稿は飛ばす | 分岐網羅・エラー推測 | BR-52、BR-53 | `TimelineServiceUnitTests` |
| UT-TL-03 | いいね経由の文言：1 人（ほか 0 人）／3 人（ほか 2 人）、最新のいいねの人の名前。自分の投稿には付けない | 境界値 | BR-50-3 | `TimelineServiceUnitTests`、frontend `PostCard` |
| UT-CO-01 | カーソル：作って読むと元に戻る、壊れた文字列は 400。画面は続きを読むときだけ `?cursor=` を付け（`?` があれば `&`）、前の返事の `nextCursor` をそのまま送る | 同値分割・エラー推測 | BR-53 | `CursorsTests`、frontend `api/cursor`・`UserList` |
| IT-TL-01 | 一覧の件数：ちょうど 20 件なら続きはない、21 件なら続きが 1 件。同じ時刻が並んでも重複・抜けがない | 境界値 | BR-53 | `AllTimelineTests`、`FollowListTests` |
| IT-TL-02 | 読んでいる途中で投稿・いいねが増えても重複・抜けがない | エラー推測 | BR-53 | `FollowingStabilityTests` |
| IT-TL-03 | フォロー中タブ：自分とフォロー中の人だけ・いいね経由の投稿は 1 回だけ・ハイライトの選び方（反応の多い順・反応 0 件は出さない・最大 3 件・自分の投稿は除く）・ハイライトに出た投稿は下に出ない | 同値分割・境界値 | BR-50-2、BR-50-3、BR-52、BR-52-1 | `FollowingTimelineTests`、`LikedViaTimelineTests`、`HighlightTests` |
| IT-TL-04 | 利用者ごとの投稿一覧 | 同値分割 | F-TL | `UserPostsTimelineTests` |
| IT-TL-05 | 画面：タブを覚える・続きを読み込む・引っ張って更新 | 状態遷移 | BR-51、BR-53、BR-54 | frontend `pages/Timeline` |

### 4.5 コメント（F-CM）

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-CM-01 | 削除の権限：コメントした本人 × 投稿した本人（どちらかなら削除できる。両方違えば 403）。一覧の `deletable` も同じ判定。ないコメント・ない投稿は 404 | デシジョンテーブル | BR-35 | `CommentServiceUnitTests` |
| UT-CM-02 | 本文：0・1・280・281 文字 | 境界値 | BR-30 | `RequestValidationTests` |
| IT-CM-01 | コメントの投稿・一覧（古い順）・削除の権限・数 | デシジョンテーブル | BR-30、BR-31、BR-35 | `CreateCommentTests`、`CommentListTests`、`DeleteCommentTests`、`CommentCountTests` |

### 4.6 いいね（F-LK）

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-LK-01 | いいね・取り消しは、重複を無視して追加・なくても消すだけで、操作のあとの状態（いいね済みか・数）を返す。ない投稿は 404 で DB を変えない | 同値分割 | BR-33、A-4 | `LikeServiceUnitTests` |
| UT-LK-02 | 画面：押してすぐ表示を変え、失敗したら元に戻す | 状態遷移・エラー推測 | 画面 04 4.1 | frontend `LikeButton` |
| IT-LK-01 | いいね・取り消しを何度送っても結果が同じ（未いいね → いいね → いいね → 取り消し → 取り消し）・数・いいねした人の一覧（新しい順）、自分の投稿にもできる | 状態遷移・同値分割 | BR-32〜34、A-4 | `LikeTests`、`LikeCountTests`、`LikerListTests` |

### 4.7 フォロー（F-FL）

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-FL-01 | フォローの相手：自分（400。利用者を探しもしない）・他人・いない利用者（404）。解除はフォローしていなくてもエラーにしない。画面の利用者の一覧は、0 人・見つからない（404）・ほかの誤りで出し分ける | 同値分割 | BR-42、BR-43、BR-44 | `FollowServiceUnitTests`、frontend `UserList` |
| IT-FL-01 | フォロー・解除・一覧（新しい順）・数 | 同値分割 | BR-42〜44 | `FollowTests`、`FollowListTests` |

### 4.8 共通・セキュリティ

| ID | 観点 | 技法 | 要件 | 場所 |
|---|---|---|---|---|
| UT-CO-02 | 画像の保存（ローカル・S3）と署名つき URL | 分岐網羅 | NF-SE-06 | `LocalImageStorageTests`、`S3ImageStorageTests` |
| UT-CO-03 | CSP のヘッダーの組み立て | 同値分割 | NF-SE-05 | `ContentSecurityPolicyTests` |
| IT-CO-01 | 開発用の画像の URL：正しい署名／違う署名／期限切れ（404） | 同値分割 | NF-SE-06 | `MediaControllerTests` |
| IT-CO-02 | 画面の URL への直接のアクセスで `index.html`、`/api/` などは除く | 同値分割 | API 05 5.2 | `SpaConfigTests` |
| IT-CO-03 | Swagger UI は開発だけ、API 仕様書の書き出し | 同値分割 | API 05 5.1 | `SwaggerUiTests`、`OpenApiSpecExportTests` |
| IT-CO-04 | エラーの形（ProblemDetail と `code`） | 同値分割 | API 05 2.4 | 各 IT |
| IT-CO-05 | アプリの起動とマイグレーション、DB の制約（ユーザー名の大文字・小文字を区別しない重複・形式）、開発用のテストデータ（本番の設定では入れない・二重に入らない） | 同値分割 | BR-02、DB 設計書 4.1・7 章 | `BackendApplicationTests`、`DevDataTests`、`DevDataNotLoadedTests` |

---

## 5. カバレッジ（どれだけのコードを通ったか）

| 項目 | backend | frontend |
|---|---|---|
| 計測の道具 | JaCoCo（`./gradlew test` のあとにレポートを作る） | Vitest のカバレッジ（`@vitest/coverage-v8`。`npm run test` で計測） |
| 対象 | 単体テスト・結合テストを合わせた値 | 単体テスト・結合テストを合わせた値 |
| 今の値（2026-10-18） | 命令 95%・分岐 84%（単体テストの追加前は 94%・81%） | 命令 95%・分岐 88%（単体テストの追加前は 92%・85%） |
| しきい値（下回ると失敗） | 命令 92%・分岐 80% | 命令 93%・分岐 85% |
| 設定の場所 | `backend/build.gradle.kts` の `jacocoTestCoverageVerification`（`check` で動く） | `frontend/vite.config.ts` の `coverage.thresholds` |
| レポート | `backend/build/reports/jacoco/test/html/index.html` | `frontend/coverage/index.html`（Git に入れない） |
| 下回ったとき | CI を失敗させる | 同左 |

カバレッジは「テストが通っていない所」を見つける道具として使う。数字を上げるためだけのテスト（結果を確かめないテスト）は書かない。
しきい値は今の値を少し下回る線にして、テストを足さずに機能を増やしたときに気づけるようにする。単体テストを足して値が上がったら、しきい値も上げる。

---

## 6. 実行の方法

| 何を | コマンド | 備考 |
|---|---|---|
| まとめて（CI と同じ） | `node scripts/check.mjs` | backend の結合テストは Docker を使う |
| backend だけ | `cd backend; ./gradlew test` | 1 クラスだけ：`./gradlew test --tests '*PostServiceUnitTests'` |
| frontend だけ | `cd frontend; npx vitest run` | 1 ファイルだけ：`npx vitest run src/postLength.test.ts` |

---

## 7. 改訂履歴

| 版数 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-10-18 | 初版 |
| 0.2 | 2026-10-18 | カバレッジの計測を導入し、今の値・しきい値・レポートの場所を追記（5 章） |
| 0.3 | 2026-10-18 | backend の単体テストを追加し、観点表を実装に合わせて直した（入力チェックの境界値は RequestValidationTests に、SQL で決まる観点は結合テストに） |
| 0.4 | 2026-10-18 | frontend の単体テストを追加し、観点表（UT-AU-07・UT-PO-05・UT-PO-07・UT-CO-01・UT-FL-01）とカバレッジの値を更新 |
| 0.5 | 2026-10-18 | 結合テストを観点表と突き合わせ、全 API の未ログインの 401・一覧の 20 件ちょうどの境界・編集で残るものを追加。IT-AU-06・IT-CO-05 を追加し、結合テストのクラスに観点 ID を付けた |
