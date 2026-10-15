# 岩手県 市町村新着情報

岩手県内33市町村の新着行政情報を毎日16時（日本時間）に集めて、GitHub Pages に一覧として公開する仕組みです。

- 取得: RSS/Atom フィード（30市町村）と、RSSのない3町（紫波町・矢巾町・軽米町）のスクレイピング
- 実行: GitHub Actions が毎日自動実行し、結果をこのリポジトリにコミット
- 表示: スマホで読みやすいレイアウト、ダークモード対応、過去30日分のアーカイブ付き

```
.
├── sources.yaml               # 取得元の一覧（ここを編集する）
├── scripts/build.py           # 取得・新着判定・HTML生成
├── requirements.txt
├── state/
│   ├── seen.json              # 既出記事のURL（90日より古い記録は自動削除）
│   └── daily/YYYY-MM-DD.json  # 日ごとの結果（直近30日分）
├── docs/
│   ├── index.html             # 公開ページ（最新の更新分）
│   └── archive/YYYY-MM-DD.html  # 日別アーカイブ（直近30日分）
└── .github/workflows/daily.yml  # 毎日 UTC 7:00（日本時間16:00）に実行
```

## 新着の判定

- 取得した記事のURLを `state/seen.json` と照合し、未登録のものを「新着」として表示します。
- 初回の実行では記録だけを行い、ページに「初回のため記録のみ」と表示します。あとから取得元を追加した場合も、その取得元の初回は記録だけになります（既存記事がまとめて新着扱いになるのを防ぐため）。
- 取得に失敗した自治体はページ上部に赤く「取得エラー」と表示されます。ほかの自治体の処理は続行します。
- 同じ日に再実行した場合、その日すでに見つかった新着は消えずに残ります。

## 取得元の追加・変更方法

`sources.yaml` を編集して main ブランチにコミットするだけです。次回の実行から反映されます。

**RSS / Atom フィードの場合**

```yaml
  - name: 盛岡市
    sources:
      - type: rss
        url: https://www.city.morioka.iwate.jp/news.rss
```

- Atom フィードも `type: rss` で読めます。
- 1つの自治体に複数のフィードを並べられます。同じ記事が複数のフィードにあっても、URLが同じなら1件にまとめます。

```yaml
  - name: 釜石市
    sources:
      - type: rss
        url: https://www.city.kamaishi.iwate.jp/category/kubun/news/index.rss
      - type: rss
        url: https://www.city.kamaishi.iwate.jp/category/kubun/event/index.atom
```

**RSS のないサイト（スクレイピング）の場合**

ページ内で日付を見つけ、その直後に出てくる最初のリンクを記事のタイトルとURLとして扱います。

```yaml
  - name: 紫波町
    sources:
      - type: scrape
        url: https://www.town.shiwa.iwate.jp/news/
        date_format: dotted       # 「2026.10.15」形式
        # selector: "#news-list"  # （任意）記事一覧部分だけを対象にするCSSセレクタ
```

| `date_format` | 日付の書式 | 例 |
|---|---|---|
| `dotted` | 西暦をドットで区切る | `2026.10.15` |
| `reiwa` | 令和（令和N年 = 西暦 2018+N 年） | `令和8年 10月15日`（空白入り・全角数字も可） |

- メニューやバナーのリンクまで拾ってしまう場合は、`selector` で記事一覧の部分を指定してください。
- 日付が読めない記事も取りこぼさず、日付欄を空にして表示します。

**取得元をやめる・一時的に止める場合**

その自治体の `sources:` を `sources: []` にすると、ページに「取得元未設定」と表示されます。

**ローカルで試す場合**

```bash
pip install -r requirements.txt
python scripts/build.py
```

`state/` と `docs/` が更新されます。試した結果をコミットしたくない場合は `git checkout -- state docs` で元に戻してください。

## GitHub Pages の有効化手順

1. この仕組みのファイルを **main ブランチにマージ**します（定時実行は main ブランチのワークフローでのみ動きます）。
2. GitHub のリポジトリ画面で **Settings → Pages** を開きます。
3. 「Build and deployment」の **Source** で「**Deploy from a branch**」を選びます。
4. **Branch** で「`main`」と「`/docs`」を選び、**Save** を押します。
5. 1〜2分ほどで、画面上部に公開URL（`https://<ユーザー名>.github.io/<リポジトリ名>/`）が表示されます。
6. **Settings → Actions → General** の「Workflow permissions」が「Read and write permissions」になっているか確認します（ワークフロー側でも `contents: write` を指定していますが、組織の設定で制限されている場合はここを変更してください）。

> 補足: 無料プランでは、GitHub Pages を使えるのは公開（Public）リポジトリのみです。また main ブランチに保護ルール（直接 push の禁止など）があると、ワークフローの自動コミットが失敗します。

## 手動で実行する方法

1. リポジトリ画面の **Actions** タブを開きます。
2. 左の一覧から「**Daily Iwate municipal news**」を選びます。
3. 右側の「**Run workflow**」を押し、ブランチが `main` になっていることを確認して、緑の「**Run workflow**」を押します。
4. 数分で完了し、`state/` と `docs/` の更新が自動でコミットされます。その後1〜2分で公開ページに反映されます。

失敗した場合は、実行結果の「Build pages」ステップのログに、取得元ごとの `[OK]` / `[ERROR]` が出力されています。
