# Task Manager (Web)

タスク管理アプリの Web 版です。React + Vite + TypeScript 製で、`/backend` の API を通じて Android アプリと同じタスクデータを共有します。

## 開発

```bash
npm install
cp .env.example .env   # VITE_API_URL を backend の URL に合わせて調整
npm run dev
```

`.env` の `VITE_API_URL` は `/backend` のベース URL (`http://localhost:3000/api` など) を指します。

## ビルド

```bash
npm run build
```

`dist/` に静的ファイルが出力されます。任意の静的ホスティング (Netlify, Vercel, Nginx など) に配置して公開できます。ビルド後の静的ファイルは環境変数を埋め込み済みなので、API のURLを変える場合はビルドし直してください。

## 画面構成

- ログイン / 新規登録
- フォーカス (今やる1〜3件だけを表示)
- すべてのタスク (クイック追加・フィルタ・タグ絞り込み・優先度別グループ表示・月間カレンダー表示・スワイプ相当の削除ボタン)
- タスク編集 (優先度・タグ・期限日時・リマインダー・サブタスク・Googleカレンダー連携)

## 制約事項

- リマインダー通知はブラウザの `Notification` API を使い、このタブを開いている間のみ動作します(閉じていても届く確実な通知が必要な場合は Android アプリを使ってください)。
- 複数端末での更新はポーリング(10秒間隔)で反映されます。リアルタイム同期(WebSocket等)は未実装です。

## Vercel へのデプロイとパスワード保護

このディレクトリを Vercel にインポートすればそのままデプロイできます(`vercel.json` でSPAのルーティング用のrewriteを設定済み)。デプロイ後は Project Settings → Environment Variables で `VITE_API_URL` を実際のバックエンドURLに設定し、再デプロイしてください。

サイト全体にパスワードをかける方法は2通りあります。

### 方法1: Vercel公式の Password Protection (Pro/Enterpriseプランのみ)

Vercelダッシュボード → 対象プロジェクト → Settings → Deployment Protection → **Password Protection** をオンにしてパスワードを設定します。Hobby(無料)プランでは利用できません。プランは Settings → Billing で確認できます。

### 方法2: コード側で実装したBasic認証 (どのプランでも利用可)

`web/middleware.ts` に Vercel Edge Middleware としてHTTP Basic認証を実装済みです。プランに関わらず動作します。有効にするには:

1. Vercelダッシュボード → 対象プロジェクト → Settings → Environment Variables で以下の2つを設定
   - `BASIC_AUTH_USER`
   - `BASIC_AUTH_PASSWORD`
2. 再デプロイ

サイトにアクセスするとブラウザの認証ダイアログが表示され、上記のユーザー名/パスワードを入力しないと閲覧できなくなります。環境変数を設定しない限りは無効(誰でも閲覧可能)なので、うっかりロックアウトすることはありません。

どちらの方法も、デプロイ・ダッシュボードでの設定操作自体はご自身のVercelアカウントで行っていただく必要があります。
