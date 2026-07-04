# Task Manager

タスク管理アプリです。ADHD傾向のあるユーザーを前提に、実行機能の負荷を下げる方向で設計しています。**Android アプリ・Webアプリ・共有バックエンド**の3つで構成されており、同じアカウントでログインすればどちらからでも同じタスクを見る・編集できます。

```
.
├── app/       # Android アプリ (Kotlin + Jetpack Compose)
├── backend/   # 共有 API サーバー (Node.js + Express + Prisma/PostgreSQL + JWT認証)
└── web/       # Web アプリ (React + Vite + TypeScript)
```

それぞれの詳細は各ディレクトリの README を参照してください:
[`backend/README.md`](backend/README.md) / [`web/README.md`](web/README.md)

## ADHD傾向を前提にした設計

Android・Web共通のUX方針です。

- **フォーカスモード(起動時のデフォルト画面)**: 開いた瞬間に全タスクの一覧を見せず、「今やる1〜3件」だけを表示。情報過多による先延ばしや選択麻痺を避けます。「1件だけ」⇄「3件」の切り替えも可能。
- **クイック追加**: 一覧画面の上部に常時テキスト欄があり、タイトルだけで1タップ即追加。優先度や期限を考える手間が思考の妨げにならないようにしています。
- **タスク分解(サブタスク)**: 編集画面でタスクをチェックリスト形式の小さいステップに分解可能。サブタスクにも締切日を付けられます。大きく漠然としたタスクの「何から手をつけていいかわからない」を軽減します。
- **繰り返しタスク**: 「毎日繰り返す」を設定すると、完了するたびに翌日分のタスクが自動で新しく作られます。過去の完了履歴は残ったまま、今日やることだけが一覧に積み重なっていきます。
- **リマインダー通知+スヌーズ**: 期限日時に対して「期限ちょうど/10分前/30分前/1時間前/前日」の通知タイミングを複数選択可能(例: 前日と1時間前を両方セット)。通知からその場で「10分後に再通知」または「完了にする」を選べます(Androidは端末再起動後も再スケジュールされます)。
- **期限切れのはっきりした警告**: 期限切れタスクは赤系の背景と警告アイコンで強調表示(ユーザーの希望により、控えめでなく目立つ表示を採用)。
- **完了時のポジティブなフィードバック**: タスクを完了すると、責めるのではなく肯定的な一言をスナックバー/トーストで表示します。
- **Googleカレンダー連携**: 期限を設定したタスクをワンタップでGoogleカレンダーの予定作成画面に渡せます。

## クイックスタート(ローカルで動作確認する場合)

```bash
# 1. バックエンドを起動
cd backend
npm install
cp .env.example .env   # DATABASE_URL にPostgreSQLの接続文字列、JWT_SECRETを変更しておくこと
npx prisma migrate deploy
npm run dev            # http://localhost:3000

# 2. 別ターミナルでWebを起動
cd web
npm install
cp .env.example .env   # VITE_API_URL=http://localhost:3000/api のままでOK
npm run dev            # http://localhost:5173 を開く
```

Android アプリからも同じバックエンドに接続できます。ログイン画面の「サーバーURL」欄に、バックエンドをホストしているURL (例: `http://10.0.2.2:3000/api/` はエミュレータから開発PCのlocalhostを指すエイリアス) を入力してください。実機やAndroid Studio以外の環境で複数端末から使う場合は、バックエンドをインターネットからアクセスできる場所にデプロイし、そのURLを指定します。

## Android アプリ

Kotlin + Jetpack Compose (Material 3) 製。データは Retrofit 経由で `/backend` の API を呼び出し、Web版と同じデータを共有します(ローカルRoom DBは廃止済み)。

### 主な機能

- タスクの追加・編集・削除、サブタスク(チェックリスト)
- チェックボックスで完了/未完了を切り替え
- 優先度 (低・中・高) の設定と色分け表示、優先度別にグループ化した一覧表示
- タグ付け(仕事・プライベートなど自由入力)とタグでの絞り込み
- 月間カレンダー表示(期限のあるタスクを日付ごとに確認)
- 期限日の設定は「今日/明日/今週末/来週」のワンタップ選択+「朝/昼/夕方/夜」のざっくり時間帯選択(細かい時刻入力の手間を省く設計)。リマインダー通知/スヌーズはAlarmManagerベースで端末再起動後も再登録
- フィルタ (すべて / 未完了 / 完了済み) 切り替えタブ、フォーカスモード
- スワイプでタスクを削除
- メール+パスワードでのログイン/新規登録(JWT、暗号化ストレージにトークン保存)
- ライト/ダークテーマ、Android 12+ の Dynamic Color 対応

### プロジェクト構成

```
app/src/main/java/com/example/taskmanager/
├── MainActivity.kt                 # Navigation Compose + ボトムナビ + 認証フロー + 通知権限リクエスト
├── TaskManagerApp.kt                # Application (通知チャンネル初期化)
├── auth/
│   ├── AuthRepository.kt           # ログイン/登録/ログアウト
│   ├── AuthViewModel.kt             # 認証状態 (StateFlow)
│   ├── TokenStore.kt                # JWTの暗号化保存 (EncryptedSharedPreferences)
│   └── ServerConfig.kt              # 接続先バックエンドURLの保存
├── data/
│   ├── Task.kt                     # Task モデル / Priority enum / isOverdue 等の拡張関数
│   ├── TaskRepository.kt            # リポジトリ層 (API呼び出しのラッパー)
│   └── remote/
│       ├── ApiClient.kt             # Retrofit + 認証ヘッダー付与
│       ├── ApiModels.kt             # リクエストDTO
│       ├── AuthApi.kt               # 認証エンドポイント
│       └── TaskApi.kt               # タスクエンドポイント
├── calendar/CalendarShare.kt        # Googleカレンダーへのインテント連携
├── notifications/
│   ├── NotificationHelper.kt        # 通知チャンネル/通知の組み立て
│   ├── ReminderScheduler.kt         # AlarmManager によるスケジューリング
│   ├── ReminderReceiver.kt          # 通知の表示 (APIからタスクを取得)
│   ├── ReminderActionReceiver.kt    # 通知上の「スヌーズ/完了」アクション
│   └── BootReceiver.kt              # 端末再起動後のリマインダー再登録
└── ui/
    ├── TaskViewModel.kt             # 状態管理 (StateFlow) / ポーリングでバックエンドと同期
    ├── theme/                       # Material3 テーマ
    ├── components/TaskItem.kt       # タスク1件分の行 UI (期限切れ強調・サブタスク進捗表示)
    └── screens/
        ├── LoginScreen.kt / RegisterScreen.kt  # ログイン・新規登録
        ├── FocusScreen.kt           # フォーカスモード画面(デフォルト起動画面)
        ├── TaskListScreen.kt        # 一覧画面 + クイック追加
        └── AddEditTaskScreen.kt     # 追加・編集画面(優先度/期限/リマインダー/サブタスク)
```

### ビルド方法

このリポジトリには Gradle Wrapper のバイナリ (`gradlew` / `gradle-wrapper.jar`) を含めていません
(作業環境が外部ネットワークに制限されておりダウンロードできなかったため)。お手元の環境でビルドする場合は次のいずれかの方法を使ってください。

1. **Android Studio で開く** (推奨): プロジェクトルートを開けば Android Studio が同梱の Gradle で自動的に Wrapper を生成・同期します。
2. **ローカルに Gradle がある場合**: プロジェクトルートで以下を実行して Wrapper を生成してから通常通りビルドできます。

   ```bash
   gradle wrapper --gradle-version 8.7
   ./gradlew assembleDebug
   ```

必要な SDK: `compileSdk 34` / `minSdk 26` / `targetSdk 34`。

### 既知の制約

- データは常時バックエンドから取得します(オフラインキャッシュなし)。ネットワークがない状態では一覧の閲覧・編集ができません。
- 複数端末での更新はポーリング(10秒間隔)+各操作直後の再取得で反映されます。リアルタイム同期(WebSocket等)は未実装です。
- バックエンドURLはログイン/登録画面で都度設定します。デフォルトはAndroidエミュレータ用の `http://10.0.2.2:3000/api/`。
