# API 設計書：タイムライン型 SNS（RaiseTimeline）

| 項目 | 内容 |
|---|---|
| 文書番号 | 05 |
| 版数 | 0.3 |
| 作成日 | 2026-10-09 |
| 作成者 | major182 |
| 前提となる文書 | [01 要件定義書](01_requirements.md)、[01-1 機能一覧](01-1_feature-list.md)、[03 DB 設計書](03_db-design.md)、[04 画面設計書](04_screen-design.md) |

React（画面）と Spring Boot（サーバー）の**約束ごと**。URL・送る値・返す値・エラーの正。
画面の文言は [04 画面設計書](04_screen-design.md) 6章の ID（E-xx・S-xx など）で参照する。

---

## 1. 方針

| No | 方針 | 理由 |
|---|---|---|
| A-1 | URL は名詞、操作は HTTP のメソッドで表す（REST の形）。すべて `/api/` で始める | 画面の URL（`/users/...`）と重ならないようにする。画面と API は同じオリジンから配信する（技術選定書 4.2） |
| A-2 | 利用者は **ID（数字）**で指す。ユーザー名を使うのは、プロフィールを開く API だけ | ユーザー名は変えられる（BR-02、DB 設計書 D-1） |
| A-3 | 一覧は**すべてカーソル方式**で 20 件ずつ返す。カーソルは画面が中身を読まない文字列 | BR-53。並び方の作りを変えても、画面を直さずに済む |
| A-4 | いいね・フォローは **PUT（する）／DELETE（やめる）**。何度送っても結果が同じ | 画面は返事を待たずに表示を切り替える（画面設計書 4.1）ので、二重に押しても順番が前後しても壊れない形にする |
| A-5 | エラーは ProblemDetail（RFC 9457）に**エラーの種類（`code`）**を加えて返す | 画面が `code` でメッセージを選べる。入力欄ごとの誤り（BR-09）も返せる |

---

## 2. 共通仕様

### 2.1 形式

| 項目 | 内容 |
|---|---|
| 送受信 | JSON。画像を送る API だけ `multipart/form-data` |
| 項目名 | キャメルケース（`displayName`） |
| 日時 | ISO 8601 の UTC（`2026-10-09T05:05:00Z`）。日本時間への変換は画面で行う |
| 値がない項目 | 省略せず `null` |
| 文字数 | コードポイントで数える（DB 設計書 D-7） |

### 2.2 認証（JWT）

決めごとの理由は技術選定書 4.1。

| 項目 | 内容 |
|---|---|
| アクセストークン | 登録・ログイン・取り直しの応答（AuthResponse。3.6）の `accessToken` で受け取る。期限 15 分。画面はメモリにだけ置き、`Authorization: Bearer <トークン>` ヘッダーで送る |
| リフレッシュトークン | 登録・ログイン・取り直しの応答で `REFRESH_TOKEN` Cookie として受け取る。`HttpOnly`・`Secure`・`SameSite=Strict`・`Path=/api/auth`、期限 7 日。開発環境（HTTP）だけ `Secure` を外す |
| 取り直し | 画面の起動時と、アクセストークンの期限切れ（401）のときに `POST /api/auth/refresh` を呼ぶ。同時に何度も呼ばないよう、画面は呼び出しを1つにまとめる |
| ログインが不要な API | `POST /api/auth/signup`、`POST /api/auth/login`、`POST /api/auth/refresh`、`POST /api/auth/logout`、`GET /actuator/health` |
| ログインしていないとき | 上以外の `/api/**` は、トークンがない・期限切れ・署名が正しくないときに **401**（`UNAUTHENTICATED`）。ログイン画面へのリダイレクトはしない |
| CSRF | アクセストークンはヘッダーで送るので、CSRF の対象外。Cookie を使う `/api/auth/refresh`・`/api/auth/logout` は、Cookie の `SameSite=Strict` に加え、ヘッダー `X-Requested-With: RaiseTimeline` を必須にする（他のサイトからはこのヘッダーを付けて送れない）。ないときは **403**（`CSRF_INVALID`） |
| CORS | 設定しない（画面と API が同じオリジン。開発中も Vite の proxy を通す） |

### 2.3 状態コード

| コード | 使う場面 |
|---|---|
| 200 | 取得・更新に成功（本文あり） |
| 201 | 作成に成功（登録・投稿・コメント）。`Location` を付ける |
| 204 | 成功（本文なし）：削除・ログアウト・フォロー・パスワードの変更 |
| 400 | 入力の誤り |
| 401 | ログインしていない・期限切れ・ログインの失敗 |
| 403 | 権限がない（他人の投稿の編集・削除など）・CSRF の対策のヘッダーがない |
| 404 | 見つからない（削除された投稿・いない利用者） |
| 409 | メールアドレス・ユーザー名が使われている |
| 413 | 画像が 5MB を超える |
| 415 | 画像の形式が JPEG・PNG・WebP・GIF でない（中身で判定） |
| 500 | 想定外の誤り |

投稿は全員に公開されている（BR-14）ので、他人の投稿の編集・削除は存在を隠す必要がなく、404 ではなく 403 を返す。

### 2.4 エラーの形

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "入力内容を確認してください",
  "instance": "/api/auth/signup",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "password", "code": "PASSWORD_WEAK", "message": "8〜72 文字で、英字と数字をそれぞれ 1 文字以上含めてください" }
  ]
}
```

- `type` は既定値（`about:blank`）のため、Spring が省略して返す
- `detail`・`message` は画面にそのまま出せる日本語（画面設計書の文言と同じ）
- `errors` は入力欄ごとの誤り。400（`VALIDATION_FAILED`）と 409 のときだけ付ける
- 形式のチェックで誤りがあれば 400 だけを返し、重複のチェック（409）はしない

**エラーの種類（`code`）**

| `code` | 状態 | 場面 | 画面 |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | 入力の誤り（中身は `errors`） | 下の表 |
| `POST_EMPTY` | 400 | 本文も画像もない（BR-11） | E-01 |
| `IMAGE_TOO_MANY` | 400 | 画像が 5 枚以上 | E-12 |
| `CURRENT_PASSWORD_WRONG` | 400 | 今のパスワードが違う | E-22 |
| `UNAUTHENTICATED` | 401 | ログインしていない・トークンの期限切れ・取り直しができない | S-02（画面は先に取り直しを試す） |
| `LOGIN_FAILED` | 401 | メールアドレスかパスワードが違う（未登録のメールアドレスも同じ） | E-20 |
| `LOGIN_LOCKED` | 401 | ログインの一時停止中（BR-08） | E-21 |
| `FORBIDDEN` | 403 | 権限がない | S-03 |
| `CSRF_INVALID` | 403 | CSRF の対策のヘッダー（`X-Requested-With`）がない | S-04 |
| `NOT_FOUND` | 404 | 見つからない | N-04・N-05 |
| `CONFLICT` | 409 | 使われている（中身は `errors`） | 下の表 |
| `IMAGE_TOO_LARGE` | 413 | 画像が大きすぎる | E-11 |
| `IMAGE_TYPE_INVALID` | 415 | 画像の形式が違う | E-10 |
| `INTERNAL_ERROR` | 500 | 想定外の誤り | S-04 |

**入力欄ごとの誤り（`errors[].code`）**

| `code` | 場面 | 画面 |
|---|---|---|
| `REQUIRED` | 必須の項目が空 | E-01 |
| `EMAIL_INVALID` | メールアドレスの形式 | E-02 |
| `EMAIL_TAKEN` | メールアドレスが使われている（409） | E-03 |
| `PASSWORD_WEAK` | パスワードの条件 | E-04 |
| `PASSWORD_MISMATCH` | パスワード（確認）が違う | E-05 |
| `CURRENT_PASSWORD_WRONG` | 今のパスワードが違う（パスワードの変更） | E-22 |
| `USERNAME_INVALID` | ユーザー名の形式 | E-06 |
| `USERNAME_TAKEN` | ユーザー名が使われている（409） | E-07 |
| `DISPLAY_NAME_LENGTH` | 表示名の長さ | E-08 |
| `BIO_TOO_LONG` | 自己紹介の長さ | E-09 |
| `BODY_TOO_LONG` | 投稿・コメントが 280 文字を超える | E-13 |
| `QUERY_LENGTH` | 検索語の長さ（1〜50 文字） | ― |

### 2.5 一覧

```json
{ "items": [ … ], "nextCursor": "eyJ0Ijoi…" }
```

- 続きは `?cursor=<nextCursor の値>` を付けて同じ API を呼ぶ。`nextCursor` が `null` なら終わり
- 1 回に 20 件（変えられない）
- カーソルは、並びの値を JSON にして Base64URL にした文字列。中身は API ごとに違う（タイムラインは DB 設計書 5.2 の基準の時刻なども含む。検索は読み飛ばす件数）。壊れたカーソルは 400

---

## 3. データの形

### 3.1 UserSummary（利用者の要約）

```json
{
  "id": 12,
  "username": "raise_user",
  "displayName": "レイズ",
  "avatarUrl": null,
  "bio": "自己紹介",
  "followedByMe": false,
  "isMe": false
}
```

- `avatarUrl`：未設定なら `null`（画面は既定のアイコン）。画像の URL はどれも期限 1 時間の署名つき URL（NF-SE-06）
- `isMe` が `true` ならフォローボタンを出さない

### 3.2 Profile（プロフィール）

UserSummary ＋ `followingCount`（フォロー数）・`followerCount`（フォロワー数）・`createdAt`（登録日時）。

### 3.3 Me（ログインしている利用者）

`id`・`username`・`displayName`・`avatarUrl`・`email`。**メールアドレスを返すのはこの形だけ**（他人のメールアドレスはどの API も返さない）。

### 3.6 AuthResponse（ログインの応答）

登録・ログイン・取り直しの応答。あわせて `REFRESH_TOKEN` Cookie を返す。

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "…": "Me" }
}
```

- `expiresIn` はアクセストークンの残りの秒数

### 3.4 Post（投稿）

```json
{
  "id": 345,
  "author": { "…": "UserSummary" },
  "body": "本文",
  "images": [ { "url": "https://…", "width": 1200, "height": 800 } ],
  "createdAt": "2026-10-09T05:05:00Z",
  "editedAt": null,
  "likeCount": 34,
  "commentCount": 12,
  "likedByMe": true,
  "likedVia": null,
  "isMine": false
}
```

| 項目 | 内容 |
|---|---|
| `images` | 並び順のとおり。なければ空の配列 |
| `editedAt` | 未編集なら `null`。値があれば「編集済み」（BR-16） |
| `likedVia` | フォロー中タブのいいね経由の投稿だけ `{ "displayName": "山田", "username": "yamada", "othersCount": 2 }`（「山田さん、ほか 2 人がいいねしました」）。ほかは `null` |
| `isMine` | `true` のときだけ編集・削除のメニューを出す（BR-12） |

インプレッション数・リツイートの数は**持たない**（BR-40、BR-41）。

### 3.5 Comment（コメント）

`id`・`postId`・`author`（UserSummary）・`body`・`createdAt`・`deletable`（コメントした本人か投稿した本人なら `true`。BR-35）。

---

## 4. API

表の「エラー」には、2.2 の 401 と 500 を除いた、その API に特有のものだけを書く。

### 4.1 認証

| メソッド・パス | 送る値 | 成功 | エラー | 画面 |
|---|---|---|---|---|
| `POST /api/auth/signup` | `email`・`password`・`passwordConfirmation`・`username` | 201 AuthResponse（ログインした状態になる） | 400、409（`EMAIL_TAKEN`・`USERNAME_TAKEN`） | SC-02 |
| `POST /api/auth/login` | `email`・`password` | 200 AuthResponse | 400、401（`LOGIN_FAILED`・`LOGIN_LOCKED`） | SC-01 |
| `POST /api/auth/refresh` | ―（`REFRESH_TOKEN` Cookie と `X-Requested-With`） | 200 AuthResponse（リフレッシュトークンも新しくなる） | 401、403 | 起動時・期限切れのとき |
| `POST /api/auth/logout` | ―（`REFRESH_TOKEN` Cookie と `X-Requested-With`） | 204（Cookie を消す） | 403 | SC-10 |
| `GET /api/auth/me` | ― | 200 Me | 401 | 起動時 |
| `PUT /api/me/password` | `currentPassword`・`newPassword`・`newPasswordConfirmation` | 204 | 400（`CURRENT_PASSWORD_WRONG` ほか） | SC-10 |

- 登録の表示名はユーザー名と同じ値にする（BR-01）
- 失敗の回数の数え方は DB 設計書 5.8
- 取り直しでは、受け取ったリフレッシュトークンを無効にして新しいものを返す。無効にしたトークンがもう一度送られたら、その利用者のリフレッシュトークンをすべて無効にして 401 を返す（DB 設計書 4.7）
- ログアウトは、Cookie のリフレッシュトークンを無効にして Cookie を消す。トークンがない・無効でも 204 を返す（すでにログアウトした状態と同じ）
- パスワードを変えたら、今の端末以外のリフレッシュトークンをすべて無効にする
- 登録の 409 は登録済みのメールアドレスを知らせてしまうが、登録画面の使いやすさを優先して許す。ログインでは知らせない（BR-09）

### 4.2 利用者・プロフィール

| メソッド・パス | 送る値 | 成功 | エラー | 画面 |
|---|---|---|---|---|
| `GET /api/users/by-username/{username}` | ― | 200 Profile | 404 | SC-05、SC-08 |
| `GET /api/users/{userId}` | ― | 200 Profile | 404 | SC-06 |
| `PATCH /api/me/profile` | `displayName`・`username`・`bio`（送った項目だけ変える） | 200 Profile | 400、409（`USERNAME_TAKEN`） | SC-06 |
| `PUT /api/me/avatar` | multipart：`file`（画像 1 枚） | 200 Profile | 413、415 | SC-06 |
| `GET /api/users/search?q=` | ― | 200 一覧（UserSummary） | 400（`QUERY_LENGTH`） | SC-07 |
| `GET /api/users/recommendations` | ― | 200 `{ "items": [UserSummary × 最大 5] }` | ― | SC-03、SC-07 |

- ユーザー名は大文字・小文字を区別せずに探す。自分の今のユーザー名は「使われている」にしない
- アイコンを変えたら、古いファイルを消す

### 4.3 タイムライン

| メソッド・パス | 成功 | 画面 |
|---|---|---|
| `GET /api/timeline/following` | 200 `{ "highlights": [Post × 最大 3], "items": [Post], "nextCursor" }` | SC-03 |
| `GET /api/timeline/all` | 200 一覧（Post） | SC-03 |
| `GET /api/users/{userId}/posts` | 200 一覧（Post）。利用者がいなければ 404 | SC-05 |

- フォロー中タブは、**カーソルなしで呼んだときだけ**留守中のハイライトを判定して `highlights` に入れ、「最後にタイムラインを開いた時刻」を更新する（DB 設計書 5.3）。続きの呼び出しでは `highlights` は空
- フォロー中タブのカーソルには、基準の時刻とハイライトに出した投稿の ID も入れる（DB 設計書 5.2）

### 4.4 投稿

| メソッド・パス | 送る値 | 成功 | エラー | 画面 |
|---|---|---|---|---|
| `POST /api/posts` | multipart：`body`（0〜280 文字）、`images`（0〜4 個。送った順が並び順） | 201 Post | 400（`POST_EMPTY`・`IMAGE_TOO_MANY` ほか）、413、415 | MD-01 |
| `GET /api/posts/{postId}` | ― | 200 Post（`likedVia` は `null`） | 404 | SC-04 |
| `PATCH /api/posts/{postId}` | `body` | 200 Post（`editedAt` を今にする） | 400、403、404 | MD-02 |
| `DELETE /api/posts/{postId}` | ― | 204 | 403、404 | DL-01 |

- 投稿の保存は「画像をすべて確かめる → S3 に保存 → DB に保存」の順。DB の保存に失敗したら、保存した S3 のファイルを消す
- 編集で変えられるのは本文だけ（BR-15）。画像のない投稿で本文を空にしたら `POST_EMPTY`
- 削除すると、コメント・いいね・画像も消える（BR-13）。S3 のファイルは DB の削除の確定後に消す（DB 設計書 4.3）

### 4.5 コメント

| メソッド・パス | 送る値 | 成功 | エラー | 画面 |
|---|---|---|---|---|
| `GET /api/posts/{postId}/comments` | ― | 200 一覧（Comment、**古い順**） | 404 | SC-04 |
| `POST /api/posts/{postId}/comments` | `body`（1〜280 文字） | 201 Comment | 400、404 | SC-04 |
| `DELETE /api/comments/{commentId}` | ― | 204 | 403、404 | DL-01 |

### 4.6 いいね

| メソッド・パス | 成功 | エラー | 画面 |
|---|---|---|---|
| `PUT /api/posts/{postId}/like` | 200 `{ "liked": true, "likeCount": 35 }` | 404 | 投稿カード |
| `DELETE /api/posts/{postId}/like` | 200 `{ "liked": false, "likeCount": 34 }` | 404 | 投稿カード |
| `GET /api/posts/{postId}/likes` | 200 一覧（UserSummary、いいねした時刻の新しい順） | 404 | SC-09 |

いいね済みでの PUT、未いいねでの DELETE も、何も変えずに今の状態を返す（A-4）。

### 4.7 フォロー

| メソッド・パス | 成功 | エラー | 画面 |
|---|---|---|---|
| `PUT /api/users/{userId}/follow` | 204 | 400（自分自身。BR-43）、404 | 利用者の行、SC-05 |
| `DELETE /api/users/{userId}/follow` | 204 | 404 | 利用者の行、SC-05 |
| `GET /api/users/{userId}/following` | 200 一覧（UserSummary、フォローした時刻の新しい順） | 404 | SC-08 |
| `GET /api/users/{userId}/followers` | 200 一覧（同上） | 404 | SC-08 |

### 4.8 そのほか

| メソッド・パス | 内容 |
|---|---|
| `GET /actuator/health` | ALB のヘルスチェック。正常なら 200 `{ "status": "UP" }`、DB につながらなければ 503 |
| `GET /media/{key}` | **開発環境だけ**。ローカルに保存した画像を返す（本番は S3 の署名つき URL）。ログインが必要 |

---

## 5. 実装の注意

- **本人の確認はサービス層で行う**（投稿・コメントの編集と削除。NF-SE-04）。画面でボタンを隠すだけでは、API を直接呼ばれると防げない
- **画面の URL への直接のアクセス**：`/users/raise_user` などを開いたり再読み込みしたりしたら、Spring Boot は `index.html` を返す（React Router が画面を出す）。`/api/`・`/actuator/`・`/media/` で始まる URL は除く。画面のファイルはログインなしで返す
- **アップロードの上限**：`spring.servlet.multipart.max-file-size=5MB`、`max-request-size=21MB`（画像 4 枚 + 本文）。超えたら 413（`IMAGE_TOO_LARGE`）
- **Swagger UI**：springdoc-openapi 3.1.1 で、開発中だけ `/swagger-ui.html` から API を試せるようにする（本番では無効）。Spring Boot 4.1 との組み合わせは導入時に確かめ、技術選定書に追記する

---

## 6. 改訂履歴

| 版数 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-10-09 | 初版 |
| 0.2 | 2026-10-09 | 認証の実装に合わせて更新：エラーの例から type を外した（既定値は省略される）、ログアウト後は CSRF トークンを取り直す、入力欄の誤りに CURRENT_PASSWORD_WRONG を追加 |
| 0.3 | 2026-10-09 | 認証を JWT 方式に変更（2.2、3.6 AuthResponse、4.1 に refresh を追加し csrf を削除） |
