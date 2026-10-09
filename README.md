# RaiseTimeline

X（旧 Twitter）のような、タイムライン形式のテキスト SNS です。学習を目的に作っています。

## 特徴

- **インプレッション数（表示回数）を表示しません。** 記録もしません
- **リツイート・引用の機能はありません**
- いいねの数・コメントの数は表示します
- タイムラインは「全体」と「フォロー中」のタブで切り替えます。フォロー中タブは2010年代後半の Twitter に近い使い心地です（フォロー中の人がいいねした投稿、留守中のハイライト）

## 主な機能

ログイン、タイムライン、投稿（テキスト・画像）、コメント、いいね、フォロー・フォロワー、利用者の検索

詳しくは [機能一覧](docs/01-1_feature-list.md) を参照してください。

## 技術スタック

| 区分 | 技術 |
|---|---|
| フロントエンド | React 19.3.0、TypeScript 6.0.3、Vite 8.3.3、Material UI 9.4.0 |
| バックエンド | Java 25、Spring Boot 4.1.1 |
| データベース | PostgreSQL 18.6 |
| インフラ | AWS（ALB・EC2・RDS・S3）、Terraform |

## 開発環境の起動

前提：Docker Desktop、Node.js 24.21.0。Java 25 は Gradle が自動で取得します。

```sh
docker compose up -d                       # 開発用の PostgreSQL
cd backend  && ./gradlew bootRun           # API（http://localhost:8080/）
cd frontend && npm install && npm run dev  # 画面（http://localhost:5173/）
```

## ドキュメント

| No | 文書 |
|---|---|
| 01 | [要件定義書](docs/01_requirements.md) |
| 01-1 | [機能一覧](docs/01-1_feature-list.md) |
| 02 | [技術選定書](docs/02_tech-stack.md) |
| 03 | [DB 設計書](docs/03_db-design.md) |
| 04 | [画面設計書](docs/04_screen-design.md) |
