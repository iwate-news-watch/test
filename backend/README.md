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
- `PATCH /api/tasks/:id` — 部分更新 (`title`, `description`, `isCompleted`, `priority`, `tag`, `dueDate`, `reminderOffsetMinutes`, `snoozedUntil` を任意の組み合わせで送信可能。`null` を明示的に送るとその項目がクリアされる)
- `DELETE /api/tasks/:id` — 削除 (サブタスクも連動して削除される)

## デプロイについて

**Vercel には向いていません。** Vercel の実行環境はリクエストのたびに使い捨てられ、ファイルシステムが永続化されないため、SQLiteのデータファイルが保存されません。このサーバーは「常時起動できて、ディスクが永続化される」場所にデプロイしてください。

### Render での手順(お試し向け、シンプル)

1. https://render.com でGitHubアカウントでログイン
2. Dashboard → New → **Web Service**
3. `nowakinowaki/test` リポジトリを接続
4. 設定:
   - **Root Directory**: `backend`
   - **Runtime**: Node
   - **Build Command**: `npm install && npm run build && npx prisma migrate deploy`
   - **Start Command**: `npm start`
5. Environment Variables に追加:
   - `DATABASE_URL` = `file:./dev.db`
   - `JWT_SECRET` = (長いランダム文字列。ローカルで `python3 -c "import secrets; print(secrets.token_hex(32))"` などで生成)
6. Create Web Service → デプロイ完了後に発行される `https://xxxxx.onrender.com` が backend の URL です。Web版の `VITE_API_URL` にはこれに `/api` を付けたものを設定します。

**注意**: Renderの無料プランはディスクが永続化されないため、サービスの再起動やスリープ復帰のたびにSQLiteのデータが消えます。お試し確認にはこれで十分ですが、実際にデータを残したい場合は有料プランで [Render Disks](https://render.com/docs/disks) を追加し、`DATABASE_URL` をそのマウントパス配下 (`file:/var/data/dev.db` など) に変更してください。

### Fly.io での手順(データを永続化したい場合)

Fly.io は無料枠内でも永続ボリューム(`fly volumes`)を使えるため、SQLiteを実運用する場合に向いています。`flyctl` CLI での `fly launch` → `fly volumes create` → `fly deploy` という流れになりますが、Dockerfile相当の設定が必要なため、詳細は [Fly.ioのNode.jsガイド](https://fly.io/docs/languages-and-frameworks/node/) を参照してください。

### 本格運用する場合

より本格的な運用では `DATABASE_URL` を PostgreSQL 等に差し替えることもできます(Render・Railway・Neon などはマネージドPostgresを提供しています)。その場合は `prisma/schema.prisma` の `datasource` の `provider` も `postgresql` に変更し、`npx prisma migrate dev` でマイグレーションを作り直してください。
