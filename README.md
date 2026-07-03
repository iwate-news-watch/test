# Task Manager (Android)

タスク管理用のネイティブ Android アプリです。Kotlin + Jetpack Compose (Material 3) + Room で構成されています。

## 主な機能

- タスクの追加・編集・削除
- チェックボックスで完了/未完了を切り替え
- 優先度 (低・中・高) の設定と色分け表示
- 期限日の設定 (DatePicker)
- フィルタ (すべて / 未完了 / 完了済み) 切り替えタブ
- スワイプでタスクを削除
- Room によるローカル永続化 (アプリを閉じてもデータは保持されます)
- ライト/ダークテーマ、Android 12+ の Dynamic Color 対応

## プロジェクト構成

```
app/src/main/java/com/example/taskmanager/
├── MainActivity.kt              # Navigation Compose によるホスト Activity
├── data/
│   ├── Task.kt                  # Room Entity / Priority enum
│   ├── TaskDao.kt                # DAO
│   ├── TaskDatabase.kt           # RoomDatabase
│   ├── TaskRepository.kt         # リポジトリ層
│   └── Converters.kt              # Priority <-> String の TypeConverter
└── ui/
    ├── TaskViewModel.kt          # 状態管理 (StateFlow)
    ├── theme/                    # Material3 テーマ
    ├── components/TaskItem.kt    # タスク1件分の行 UI
    └── screens/
        ├── TaskListScreen.kt      # 一覧画面
        └── AddEditTaskScreen.kt   # 追加・編集画面
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
