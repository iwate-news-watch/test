# Task Manager (Backend)

Android アプリと Web アプリが共有するタスクデータの REST API サーバーです。Node.js + Express + Prisma (PostgreSQL) + JWT 認証で構成されています。

## セットアップ

```bash
npm install
cp .env.example .env      # DATABASE_URL をPostgreSQLの接続文字列に、JWT_SECRETを長いランダム文字列に変更してください
npx prisma migrate deploy # スキーマを適用 (初回は `npx prisma migrate dev` でも可)
npm run dev                # http://localhost:3000 で起動
```

## 環境変数 (`.env`)

| 変数 | 説明 |
| --- | --- |
| `DATABASE_URL` | PostgreSQLの接続文字列(例: `postgresql://user:password@host:5432/dbname`)。Neon・Supabase・Render Postgresなどのマネージドサービスを想定 |
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
- `PATCH /api/tasks/:id` — 部分更新 (`title`, `description`, `isCompleted`, `priority`, `tag`, `dueDate`, `reminderOffsetsMinutes`, `snoozedUntil`, `isRecurring` を任意の組み合わせで送信可能。`null` を明示的に送るとその項目がクリアされる。`isRecurring` なタスクを `isCompleted: true` にすると、翌日分の新しいタスクが自動生成される)
- `DELETE /api/tasks/:id` — 削除 (サブタスクも連動して削除される)

## デプロイについて

**Vercel には向いていません。** Vercel の実行環境はリクエストのたびに使い捨てられるため、常時起動できるサーバー(Renderなど)にデプロイしてください。データベース自体はマネージドPostgres(Neon・Supabase・Render Postgresなど)を使うため、Web側のホスティングとDBのホスティングは別サービスでも構いません。

### 1. マネージドPostgresを用意する(例: Neon)

1. https://neon.tech でアカウント作成(無料枠あり、GitHubログイン可)
2. New Project → リージョンなど既定値のままで作成
3. 発行される接続文字列(`postgresql://user:password@xxxxx.neon.tech/dbname?sslmode=require` のような形式)をコピー。これが `DATABASE_URL` になります

Supabase や Render Postgres など、他のマネージドPostgresでも同様に接続文字列を発行できます。

### 2. Render での手順(バックエンドのWebサービス)

1. https://render.com でGitHubアカウントでログイン
2. Dashboard → New → **Web Service**
3. `nowakinowaki/test` リポジトリを接続
4. 設定:
   - **Root Directory**: `backend`
   - **Runtime**: Node
   - **Build Command**: `npm install && npm run build && npx prisma migrate deploy`
   - **Start Command**: `npm start`
5. Environment Variables に追加:
   - `DATABASE_URL` = (手順1で取得したPostgresの接続文字列)
   - `JWT_SECRET` = (長いランダム文字列。ローカルで `python3 -c "import secrets; print(secrets.token_hex(32))"` などで生成)
6. Create Web Service → デプロイ完了後に発行される `https://xxxxx.onrender.com` が backend の URL です。Web版の `VITE_API_URL` にはこれに `/api` を付けたものを設定します。

マネージドPostgresを使うことで、Renderのサービスを再デプロイ・再起動してもデータは消えません(以前のSQLiteファイル方式では、Renderの無料プランはディスクが永続化されないため再デプロイのたびに全データが消えていました。JWTトークン自体は署名だけで検証されるため、ログイン画面には気づかれずタスクだけが空になる、という不具合の原因になっていました)。
