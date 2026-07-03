# Task Manager (Android)

タスク管理用のネイティブ Android アプリです。Kotlin + Jetpack Compose (Material 3) + Room で構成されています。ADHD傾向のあるユーザーを前提に、実行機能の負荷を下げる方向で設計しています。

## ADHD傾向を前提にした設計

- **フォーカスモード(起動時のデフォルト画面)**: 開いた瞬間に全タスクの一覧を見せず、「今やる1〜3件」だけを表示。情報過多による先延ばしや選択麻痺を避けます。「1件だけ」⇄「3件」の切り替えも可能。
- **クイック追加**: 一覧画面の上部に常時テキスト欄があり、タイトルだけで1タップ即追加。優先度や期限を考える手間が思考の妨げにならないようにしています。
- **タスク分解(サブタスク)**: 編集画面でタスクをチェックリスト形式の小さいステップに分解可能。大きく漠然としたタスクの「何から手をつけていいかわからない」を軽減します。
- **リマインダー通知+スヌーズ**: 期限日時に対して「10分前/30分前/1時間前/前日」などの通知タイミングを選択可能。通知からその場で「10分後に再通知」または「完了にする」を選べます。端末再起動後も通知は再スケジュールされます。
- **期限切れのはっきりした警告**: 期限切れタスクは赤系の背景と警告アイコンで強調表示(ユーザーの希望により、控えめでなく目立つ表示を採用)。
- **完了時のポジティブなフィードバック**: タスクを完了すると、責めるのではなく肯定的な一言をスナックバーで表示します。

## 主な機能

- タスクの追加・編集・削除、サブタスク(チェックリスト)
- チェックボックスで完了/未完了を切り替え
- 優先度 (低・中・高) の設定と色分け表示
- 期限日時の設定(日付+時刻)とリマインダー通知/スヌーズ
- フィルタ (すべて / 未完了 / 完了済み) 切り替えタブ、フォーカスモード
- スワイプでタスクを削除
- Room によるローカル永続化 (アプリを閉じてもデータは保持されます)
- ライト/ダークテーマ、Android 12+ の Dynamic Color 対応

## プロジェクト構成

```
app/src/main/java/com/example/taskmanager/
├── MainActivity.kt                 # Navigation Compose + ボトムナビ + 通知権限リクエスト
├── TaskManagerApp.kt                # Application (通知チャンネル初期化)
├── data/
│   ├── Task.kt                     # Room Entity / Priority enum / isOverdue 等の拡張関数
│   ├── SubtaskCount.kt              # サブタスク集計用 POJO
│   ├── TaskDao.kt                   # DAO
│   ├── TaskDatabase.kt              # RoomDatabase
│   ├── TaskRepository.kt            # リポジトリ層
│   └── Converters.kt                 # Priority <-> String の TypeConverter
├── notifications/
│   ├── NotificationHelper.kt        # 通知チャンネル/通知の組み立て
│   ├── ReminderScheduler.kt         # AlarmManager によるスケジューリング
│   ├── ReminderReceiver.kt          # 通知の表示
│   ├── ReminderActionReceiver.kt    # 通知上の「スヌーズ/完了」アクション
│   └── BootReceiver.kt              # 端末再起動後のリマインダー再登録
└── ui/
    ├── TaskViewModel.kt             # 状態管理 (StateFlow) / フォーカス抽出 / 通知連携
    ├── theme/                       # Material3 テーマ
    ├── components/TaskItem.kt       # タスク1件分の行 UI (期限切れ強調・サブタスク進捗表示)
    └── screens/
        ├── FocusScreen.kt           # フォーカスモード画面(デフォルト起動画面)
        ├── TaskListScreen.kt        # 一覧画面 + クイック追加
        └── AddEditTaskScreen.kt     # 追加・編集画面(優先度/期限/リマインダー/サブタスク)
```

## ビルド方法

このリポジトリには Gradle Wrapper のバイナリ (`gradlew` / `gradle-wrapper.jar`) を含めていません
(作業環境が外部ネットワークに制限されておりダウンロードできなかったため)。お手元の環境でビルドする場合は次のいずれかの方法を使ってください。

1. **Android Studio で開く** (推奨): プロジェクトルートを開けば Android Studio が同梱の Gradle で自動的に Wrapper を生成・同期します。
2. **ローカルに Gradle がある場合**: プロジェクトルートで以下を実行して Wrapper を生成してから通常通りビルドできます。

   ```bash
   gradle wrapper --gradle-version 8.7
   ./gradlew assembleDebug
   ```

必要な SDK: `compileSdk 34` / `minSdk 26` / `targetSdk 34`。

Room のスキーマは DB バージョン 2 で、まだ配布前のためマイグレーションは `fallbackToDestructiveMigration()` を使用しています。実運用でユーザーデータが既に存在する場合は適切な `Migration` を追加してください。
