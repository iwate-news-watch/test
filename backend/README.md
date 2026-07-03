# Task Manager (Backend)

Android アプリと Web アプリが共有するタスクデータの REST API サーバーです。Node.js + Express + Prisma (SQLite) + JWT 認証で構成されています。

## セットアップ

```bash
npm install
cp .env.example .env      # JWT_SECRET を必ず長いランダム文字列に変更してください
npx prisma migrate deploy # スキーマを適用 (初回は `npx prisma migrate dev` でも可)
npm run dev                # http://localhost:3000 で起動
```

## 環境変数 (`.env`)

| 変数 | 説明 |
| --- | --- |
| `DATABASE_URL` | SQLite ファイルのパス。既定値は `file:./dev.db` |
| `JWT_SECRET` | JWT 署名用のシークレット。本番では必ず推測不可能な長い値に変更する |
| `PORT` | リッスンするポート。既定値は `3000` |

## 本番ビルド

```bash
npm run build
npm start
```

## API 概要

すべての `/api/tasks/*` はログインして得られる `Authorization: Bearer <token>` ヘッダーが必要です。

- `POST /api/auth/register` `{ email, password }` → `{ token, user }`
- `POST /api/auth/login` `{ email, password }` → `{ token, user }`
- `GET /api/tasks` — トップレベルのタスク一覧(サブタスクの進捗付き)
- `GET /api/tasks/:id` — タスク1件
- `GET /api/tasks/:id/subtasks` — 指定タスクのサブタスク一覧
- `POST /api/tasks` — タスク作成 (`parentTaskId` を指定するとサブタスクになる)
- `PATCH /api/tasks/:id` — 部分更新 (`title`, `description`, `isCompleted`, `priority`, `dueDate`, `reminderOffsetMinutes`, `snoozedUntil` を任意の組み合わせで送信可能。`null` を明示的に送るとその項目がクリアされる)
- `DELETE /api/tasks/:id` — 削除 (サブタスクも連動して削除される)

## デプロイについて

このリポジトリには本番デプロイの自動化は含まれていません。Node.js が動く任意の環境 (Railway, Render, Fly.io, 自前の VPS など) にこのディレクトリをデプロイし、上記の環境変数を設定した上で `npm run build && npm start` を実行してください。SQLite はファイルベースのため、永続ボリュームにデータベースファイルを置く必要があります。より本格的な運用では `DATABASE_URL` を PostgreSQL 等に差し替えることも可能です (その場合は `prisma/schema.prisma` の `provider` も変更してください)。
