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
- すべてのタスク (クイック追加・フィルタ・スワイプ相当の削除ボタン)
- タスク編集 (優先度・期限日時・リマインダー・サブタスク・Googleカレンダー連携)

## 制約事項

- リマインダー通知はブラウザの `Notification` API を使い、このタブを開いている間のみ動作します(閉じていても届く確実な通知が必要な場合は Android アプリを使ってください)。
- 複数端末での更新はポーリング(10秒間隔)で反映されます。リアルタイム同期(WebSocket等)は未実装です。
