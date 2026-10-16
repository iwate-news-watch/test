#!/usr/bin/env python3
"""岩手県内市町村の新着行政情報を取得し、GitHub Pages 用の HTML を生成する。

    python scripts/build.py

- sources.yaml の取得元を巡回し、記事URLを state/seen.json と照合して新着を判定
- その日の結果を state/daily/YYYY-MM-DD.json に保存（同日の再実行は追記マージ）
- docs/index.html と docs/archive/YYYY-MM-DD.html（直近30日分）を生成
"""

from __future__ import annotations

import html
import json
import re
import sys
import time
import unicodedata
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urljoin, urldefrag

import feedparser
import requests
import yaml
from bs4 import BeautifulSoup, NavigableString, Tag, UnicodeDammit

ROOT = Path(__file__).resolve().parent.parent
SOURCES_FILE = ROOT / "sources.yaml"
STATE_DIR = ROOT / "state"
SEEN_FILE = STATE_DIR / "seen.json"
DAILY_DIR = STATE_DIR / "daily"
DOCS_DIR = ROOT / "docs"
ARCHIVE_DIR = DOCS_DIR / "archive"

JST = timezone(timedelta(hours=9))
USER_AGENT = (
    "Mozilla/5.0 (compatible; iwate-gyosei-news/1.0; "
    "+https://github.com/nowakinowaki/test)"
)
TIMEOUT = 20
RETRY_WAIT = 3
SITE_INTERVAL = 1
SEEN_RETENTION_DAYS = 90
ARCHIVE_DAYS = 30


# ---------------------------------------------------------------------------
# 取得
# ---------------------------------------------------------------------------

_session = requests.Session()
_session.headers.update({"User-Agent": USER_AGENT})
_last_request = 0.0


def fetch(url: str) -> requests.Response:
    """URLを取得する。サイト間は1秒空け、失敗時は1回だけリトライする。"""
    global _last_request
    for attempt in range(2):
        wait = SITE_INTERVAL - (time.monotonic() - _last_request)
        if wait > 0:
            time.sleep(wait)
        try:
            resp = _session.get(url, timeout=TIMEOUT)
            resp.raise_for_status()
            return resp
        except requests.RequestException:
            if attempt == 1:
                raise
            time.sleep(RETRY_WAIT)
        finally:
            _last_request = time.monotonic()
    raise AssertionError("unreachable")


def normalize_url(url: str) -> str:
    return urldefrag(url.strip())[0]


def parse_feed(content: bytes, base_url: str) -> list[dict]:
    feed = feedparser.parse(content)
    if feed.bozo and not feed.entries:
        raise ValueError(f"フィードを解析できません: {feed.get('bozo_exception')}")
    items = []
    for entry in feed.entries:
        link = entry.get("link")
        if not link:
            continue
        stamp = entry.get("published_parsed") or entry.get("updated_parsed")
        day = ""
        if stamp:
            dt = datetime(*stamp[:6], tzinfo=timezone.utc).astimezone(JST)
            day = dt.date().isoformat()
        title = " ".join((entry.get("title") or "").split()) or link
        items.append({"title": title, "url": normalize_url(urljoin(base_url, link)), "date": day})
    return items


# --- スクレイピング --------------------------------------------------------

DATE_PATTERNS = {
    # 2026.10.15
    "dotted": re.compile(r"(\d{4})\s*\.\s*(\d{1,2})\s*\.\s*(\d{1,2})"),
    # 令和8年 10月15日 / 令和 8年10月 15日 / 令和元年...
    "reiwa": re.compile(r"令和\s*(\d{1,2}|元)\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日"),
}


def to_iso_date(fmt: str, m: re.Match) -> str:
    """日付パターンのマッチを YYYY-MM-DD にする。読めなければ空文字。"""
    try:
        y, mo, d = m.group(1), int(m.group(2)), int(m.group(3))
        if fmt == "reiwa":
            year = 2018 + (1 if y == "元" else int(y))
        else:
            year = int(y)
        return date(year, mo, d).isoformat()
    except ValueError:
        return ""


def _usable_link(a: Tag) -> str | None:
    href = (a.get("href") or "").strip()
    if not href or href.startswith(("#", "javascript:", "mailto:", "tel:")):
        return None
    return href


def decode_html(content: bytes, http_charset: str | None = None) -> str:
    """HTTPヘッダ → metaタグ → UTF-8/Shift_JIS/EUC-JP の順で文字コードを判定する。"""
    dammit = UnicodeDammit(content, known_definite_encodings=[http_charset] if http_charset else [],
                           user_encodings=["utf-8", "cp932", "euc-jp"])
    return dammit.unicode_markup or content.decode("utf-8", "replace")


def scrape_dated_links(content: bytes | str, base_url: str, date_format: str,
                       selector: str | None = None) -> list[dict]:
    """ページ内で日付パターンを見つけたら、その直後に出てくる最初のリンクを記事とする。

    日付が実在しない値などで読めなくても、記事は日付空欄で拾う。
    """
    pattern = DATE_PATTERNS[date_format]
    soup = BeautifulSoup(content, "html.parser")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    roots = soup.select(selector) if selector else [soup]

    items: list[dict] = []
    seen: set[str] = set()

    def add(a: Tag, day: str) -> None:
        href = _usable_link(a)
        if not href:
            return
        url = normalize_url(urljoin(base_url, href))
        if url in seen:
            return
        text = unicodedata.normalize("NFKC", a.get_text(" ", strip=True))
        title = " ".join(pattern.sub("", text).split()) or url
        seen.add(url)
        items.append({"title": title, "url": url, "date": day})

    for root in roots:
        pending: str | None = None  # 直前に見つけた日付（未使用）
        for node in root.descendants:
            if isinstance(node, NavigableString):
                text = unicodedata.normalize("NFKC", str(node))
                matches = list(pattern.finditer(text))
                if not matches:
                    continue
                day = to_iso_date(date_format, matches[-1])
                parent_a = node.find_parent("a")
                if parent_a is not None and _usable_link(parent_a):
                    # <a><span>2026.10.15</span>タイトル</a> のように日付がリンク内にある場合
                    add(parent_a, day)
                    pending = None
                else:
                    pending = day
            elif isinstance(node, Tag) and node.name == "a" and pending is not None:
                if _usable_link(node):
                    add(node, pending)
                    pending = None
    return items


def collect(source: dict) -> list[dict]:
    url = source["url"]
    resp = fetch(url)
    kind = source.get("type", "rss")
    if kind == "rss":
        return parse_feed(resp.content, url)
    if kind == "scrape":
        charset = requests.utils.get_encoding_from_headers(resp.headers)
        if charset == "ISO-8859-1":  # requests の text/* 既定値。宣言なしとみなす
            charset = None
        return scrape_dated_links(decode_html(resp.content, charset), url,
                                  source.get("date_format", "dotted"), source.get("selector"))
    raise ValueError(f"不明な type: {kind}")


# ---------------------------------------------------------------------------
# 状態
# ---------------------------------------------------------------------------

def load_json(path: Path, default):
    if path.exists():
        with path.open(encoding="utf-8") as f:
            return json.load(f)
    return default


def save_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")


def load_seen() -> dict:
    seen = load_json(SEEN_FILE, {})
    # articles: 記事URL -> 最後に見かけた日 / sources: 取得元URL -> 初回取得日
    seen.setdefault("articles", {})
    seen.setdefault("sources", {})
    return seen


def prune_seen(seen: dict, today: date) -> None:
    cutoff = (today - timedelta(days=SEEN_RETENTION_DAYS)).isoformat()
    seen["articles"] = {u: d for u, d in seen["articles"].items() if d >= cutoff}


# ---------------------------------------------------------------------------
# 実行
# ---------------------------------------------------------------------------

def run(now: datetime) -> dict:
    config = yaml.safe_load(SOURCES_FILE.read_text(encoding="utf-8"))
    seen = load_seen()
    first_run = not seen["sources"]
    today = now.date().isoformat()
    results = []

    for muni in config["municipalities"]:
        name = muni["name"]
        sources = muni.get("sources") or []
        result = {"name": name, "status": "ok", "errors": [], "new": [], "record_only": False}
        if not sources:
            result["status"] = "unconfigured"
            results.append(result)
            continue

        items: dict[str, dict] = {}
        fresh_sources = []
        for source in sources:
            url = source["url"]
            try:
                fetched = collect(source)
            except Exception as e:  # 1つの失敗で全体を止めない
                print(f"[ERROR] {name} {url}: {e}", file=sys.stderr)
                result["errors"].append(f"{url}: {type(e).__name__}: {e}"[:300])
                continue
            print(f"[OK] {name} {url}: {len(fetched)}件", file=sys.stderr)
            is_fresh = url not in seen["sources"]
            if is_fresh:
                fresh_sources.append(url)
            for item in fetched:
                # 同じ自治体の複数フィードはURLで重複を除く
                existing = items.get(item["url"])
                if existing is None:
                    items[item["url"]] = {**item, "_record_only": is_fresh}
                elif not is_fresh:
                    existing["_record_only"] = False
                    existing["date"] = existing["date"] or item["date"]

        if result["errors"]:
            result["status"] = "error"
        for url in fresh_sources:
            seen["sources"][url] = today
        result["record_only"] = bool(fresh_sources)

        for url, item in items.items():
            record_only = item.pop("_record_only")
            if url not in seen["articles"] and not record_only:
                result["new"].append(item)
            seen["articles"][url] = today
        result["new"].sort(key=lambda i: i["date"], reverse=True)
        results.append(result)

    prune_seen(seen, now.date())
    save_json(SEEN_FILE, seen)
    return {"fetched_at": now.strftime("%H:%M"), "first_run": first_run, "municipalities": results}


def empty_day(day: str) -> dict:
    return {"date": day, "last_fetched": "", "first_run": False, "municipalities": []}


def load_daily(path: Path, day: str) -> dict:
    """その日の一覧を読む。以前の形式（実行ごとに分かれたもの）は1つの一覧にまとめ直す。"""
    data = load_json(path, None)
    if not data:
        return empty_day(day)
    if "runs" in data:  # 実行ごとに区切っていた形式
        merged = empty_day(data["date"])
        for r in sorted(data["runs"], key=lambda r: r.get("updated_at", "")):
            add_fetch(merged, {"fetched_at": r.get("updated_at", ""), "first_run": r.get("first_run", False),
                               "municipalities": r["municipalities"]})
        return merged
    if "last_fetched" not in data:  # 1日1回だった頃の形式
        merged = empty_day(data["date"])
        add_fetch(merged, {"fetched_at": data.get("updated_at", ""), "first_run": data.get("first_run", False),
                           "municipalities": data["municipalities"]})
        return merged
    return data


def add_fetch(daily: dict, fetch_result: dict) -> dict:
    """今回の取得で増えた記事を、その日の一覧に自治体ごとに追加する。

    新しく取得したものほど上に並ぶ。取得エラー・初回記録のみの表示は今回の取得結果に合わせる。
    各記事には見つけた取得の時刻（fetched_at）を付け、最新の取得分を区別できるようにする。
    """
    prev_by_name = {m["name"]: m for m in daily["municipalities"]}
    merged = []
    for muni in fetch_result["municipalities"]:
        prev = prev_by_name.pop(muni["name"], None)
        items = [{**i, "fetched_at": fetch_result["fetched_at"]} for i in muni["new"]]
        if prev:
            urls = {i["url"] for i in items}
            items += [i for i in prev["new"] if i["url"] not in urls]
        merged.append({**muni, "new": items})
    # sources.yaml から外れた自治体も、その日にすでに載せた記事は残す
    merged += [m for m in prev_by_name.values() if m["new"]]
    daily["municipalities"] = merged
    daily["last_fetched"] = fetch_result["fetched_at"]
    daily["first_run"] = daily.get("first_run", False) or fetch_result["first_run"]
    return daily


# ---------------------------------------------------------------------------
# HTML
# ---------------------------------------------------------------------------

CSS = """
:root{--bg:#f7f7f5;--card:#fff;--fg:#1d1d1f;--muted:#6b6b70;--line:#e3e3e0;
--accent:#0a6bc2;--err-bg:#fdecec;--err-fg:#b3261e;--err-line:#f2b8b5;--chip:#eef4fb}
@media (prefers-color-scheme:dark){:root{--bg:#121214;--card:#1c1c1f;--fg:#ececee;
--muted:#9a9aa2;--line:#2e2e33;--accent:#6cb4ff;--err-bg:#3a1416;--err-fg:#ffb4ab;
--err-line:#7a2a2a;--chip:#1f2a38}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);
font:16px/1.65 -apple-system,BlinkMacSystemFont,"Hiragino Sans","Noto Sans JP",sans-serif;
-webkit-text-size-adjust:100%}
main{max-width:760px;margin:0 auto;padding:20px 16px 48px}
h1{font-size:1.35rem;line-height:1.4;margin:0 0 4px}
.sub{color:var(--muted);font-size:.85rem;margin:0 0 20px}
h1{font-size:1.25rem}
.site{font-size:.85rem;color:var(--muted);margin:0 0 6px}
.daynav{display:flex;justify-content:space-between;gap:8px;margin:0 0 16px;font-size:.88rem}
.daynav a{display:inline-block;padding:4px 10px;border:1px solid var(--line);border-radius:8px;background:var(--card)}
.daynav .next{margin-left:auto}
.card h3{font-size:1.05rem;margin:0 0 8px;display:flex;align-items:center;gap:8px;color:inherit}
.count{font-size:.75rem;font-weight:600;background:var(--chip);color:var(--accent);
padding:1px 8px;border-radius:999px}
section.card{background:var(--card);border:1px solid var(--line);border-radius:12px;
padding:14px 16px;margin:0 0 12px}
section.err{background:var(--err-bg);border-color:var(--err-line)}
section.err h3,.err .msg{color:var(--err-fg)}
ul.items{list-style:none;margin:0;padding:0}
ul.items li{padding:8px 0;border-top:1px solid var(--line)}
ul.items li:first-child{border-top:0;padding-top:0}
.date{display:block;color:var(--muted);font-size:.78rem}
a{color:var(--accent);text-decoration:none;word-break:break-word}
a:hover{text-decoration:underline}
.msg{margin:0;font-size:.9rem;color:var(--muted)}
.errlist{margin:6px 0 0;padding-left:18px;font-size:.78rem;word-break:break-all}
.latest{border:2px solid var(--accent);border-radius:14px;padding:12px 12px 2px;margin:0 0 24px}
.latest .card{margin-bottom:10px}
.latest .msg{margin:0 4px 12px}
h2{font-size:1.05rem;margin:0 0 10px}
h2 .meta{margin-left:8px;font-size:.8rem;font-weight:400;color:var(--muted)}
.latest h2{margin:0 4px 10px}
.notice{background:var(--chip);border-radius:12px;padding:12px 16px;margin:0 0 16px}
h4{font-size:.95rem;margin:20px 0 6px;color:var(--muted)}
h3.foot{font-size:.95rem;margin:36px 0 8px;color:var(--muted)}
.names{margin:0;font-size:.9rem;color:var(--muted)}
.archive{display:flex;flex-wrap:wrap;gap:6px;list-style:none;padding:0;margin:0}
.archive a{display:inline-block;padding:4px 10px;border:1px solid var(--line);
border-radius:8px;background:var(--card);font-size:.85rem}
footer{margin-top:32px;font-size:.78rem;color:var(--muted)}
.bar{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 16px;margin:0 0 20px}
.bar p{margin:0;font-size:.9rem}
.bar .state{color:var(--muted);font-size:.85rem}
.bar .state.busy{color:var(--accent)}
.bar .state.fail{color:var(--err-fg)}
.bar button{margin-top:10px;font:inherit;font-weight:600;padding:8px 18px;border-radius:999px;
border:0;background:var(--accent);color:var(--bg);cursor:pointer}
.bar button:disabled{opacity:.5;cursor:default}
.bar .rmsg{margin-top:8px;font-size:.88rem}
.bar .rmsg:empty{display:none}
[hidden]{display:none!important}
.layout{max-width:760px;margin:0 auto}
.ptoggle{font:inherit;font-size:.9rem;font-weight:600;padding:7px 16px;margin:0 0 16px;border-radius:999px;
border:1px solid var(--line);background:var(--card);color:var(--accent);cursor:pointer}
.panel{position:fixed;top:0;left:0;bottom:0;z-index:20;width:min(320px,86vw);overflow-y:auto;
background:var(--card);border-right:1px solid var(--line);padding:16px 16px 32px;
transform:translateX(-105%);transition:transform .2s;visibility:hidden}
.panel-open .panel{transform:none;visibility:visible}
.panel:focus{outline:none}
.backdrop{position:fixed;inset:0;z-index:15;background:rgba(0,0,0,.4)}
.phead{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:0 0 8px}
.phead h2{margin:0;font-size:1rem}
.pclose,.pbtns button{font:inherit;font-size:.78rem;padding:3px 10px;border-radius:8px;
border:1px solid var(--line);background:var(--bg);color:var(--fg);cursor:pointer}
.panel fieldset{border:0;margin:0 0 12px;padding:0}
.panel legend,.plabel{font-size:.82rem;font-weight:600;color:var(--muted);padding:0;margin:0 0 4px}
.psort{border-bottom:1px solid var(--line)!important;padding-bottom:10px!important}
.panel label{display:flex;align-items:center;gap:6px;font-size:.9rem;padding:3px 0;cursor:pointer}
.panel input{margin:0;accent-color:var(--accent);width:1.05em;height:1.05em}
.pregion legend{color:var(--fg)}
.pbtns{display:flex;gap:6px;margin:0 0 4px}
.fmsg{margin:0 4px 12px}
@media (min-width:1000px){
.has-panel .layout{max-width:1060px;display:grid;grid-template-columns:240px minmax(0,1fr);gap:8px;align-items:start}
.has-panel .panel{position:sticky;top:0;bottom:auto;width:auto;max-height:100vh;transform:none;visibility:visible;
z-index:auto;border:0;border-right:1px solid var(--line);background:transparent;padding:20px 16px 24px}
.ptoggle,.pclose,.backdrop{display:none!important}
}
"""


# 並び替え・絞り込みパネル用の地域区分（並び順＝地域順）と読み（五十音順用）
REGIONS = [
    ("県央", [("盛岡市", "もりおかし"), ("八幡平市", "はちまんたいし"), ("滝沢市", "たきざわし"),
             ("雫石町", "しずくいしちょう"), ("葛巻町", "くずまきまち"), ("岩手町", "いわてまち"),
             ("紫波町", "しわちょう"), ("矢巾町", "やはばちょう")]),
    ("県南", [("花巻市", "はなまきし"), ("北上市", "きたかみし"), ("遠野市", "とおのし"),
             ("一関市", "いちのせきし"), ("奥州市", "おうしゅうし"), ("西和賀町", "にしわがまち"),
             ("金ケ崎町", "かねがさきちょう"), ("平泉町", "ひらいずみちょう")]),
    ("沿岸", [("宮古市", "みやこし"), ("大船渡市", "おおふなとし"), ("陸前高田市", "りくぜんたかたし"),
             ("釜石市", "かまいしし"), ("住田町", "すみたちょう"), ("大槌町", "おおつちちょう"),
             ("山田町", "やまだまち"), ("岩泉町", "いわいずみちょう"), ("田野畑村", "たのはたむら")]),
    ("県北", [("久慈市", "くじし"), ("二戸市", "にのへし"), ("普代村", "ふだいむら"),
             ("軽米町", "かるまいまち"), ("野田村", "のだむら"), ("九戸村", "くのへむら"),
             ("洋野町", "ひろのちょう"), ("一戸町", "いちのへまち")]),
]
OTHER_REGION = "その他"
MUNI_INFO = {name: (region, order, kana)
             for order, (region, name, kana) in enumerate(
                 (r, n, k) for r, ms in REGIONS for n, k in ms)}


def muni_info(name: str) -> tuple[str, int, str]:
    """(地域, 地域順の順位, 読み)。区分にない自治体は「その他」として最後に並べる。"""
    return MUNI_INFO.get(name, (OTHER_REGION, len(MUNI_INFO), name))


def esc(s: str) -> str:
    return html.escape(s, quote=True)


def jp_date(iso: str) -> str:
    d = date.fromisoformat(iso)
    return f"{d.year}年{d.month}月{d.day}日"


def render_items(items: list[dict]) -> str:
    rows = []
    for i in items:
        day = f'<span class="date">{esc(jp_date(i["date"]))}</span>' if i["date"] else ""
        rows.append(f'<li>{day}<a href="{esc(i["url"])}" target="_blank" '
                    f'rel="noopener">{esc(i["title"])}</a></li>')
    return '<ul class="items">' + "".join(rows) + "</ul>"


def is_latest(item: dict, day: dict) -> bool:
    return bool(day.get("last_fetched")) and item.get("fetched_at") == day["last_fetched"]


def card_attrs(name: str, count: int) -> str:
    region, order, kana = muni_info(name)
    return (f'data-muni="{esc(name)}" data-region="{esc(region)}" data-order="{order}" '
            f'data-kana="{esc(kana)}" data-count="{count}"')


def render_mlist(cards: list[str], empty_msg: bool = True) -> str:
    """並び替え・絞り込みの対象になるカードのまとまり。絞り込みで全部隠れたときの案内を添える。"""
    if not cards:
        return ""
    out = '<div class="mlist">' + "\n".join(cards) + "</div>"
    if empty_msg:
        out += '<p class="msg fmsg" hidden>選択中の自治体の記事はありません</p>'
    return out


def by_region(munis: list[dict]) -> list[dict]:
    return sorted(munis, key=lambda m: muni_info(m["name"])[1])


def render_muni_cards(munis: list[dict]) -> list[str]:
    return [f'<section class="card" {card_attrs(m["name"], len(m["new"]))}><h3>{esc(m["name"])}'
            f'<span class="count">{len(m["new"])}件</span></h3>'
            f'{render_items(m["new"])}</section>' for m in by_region(munis) if m["new"]]


def render_latest(day: dict) -> str:
    """直近1回の取得で新しく見つかった記事を自治体ごとにまとめた枠。"""
    munis = [{**m, "new": [i for i in m["new"] if is_latest(i, day)]} for m in day["municipalities"]]
    count = sum(len(m["new"]) for m in munis)
    parts = ['<div class="latest">',
             f'<h2>最新の取得で増えた記事<span class="meta">{esc(day.get("last_fetched", ""))} 取得 ・ '
             f'{count}件<span class="shown"></span></span></h2>']
    if count:
        parts.append(render_mlist(render_muni_cards(munis)))
    else:
        parts.append('<p class="msg">前回から新しい記事はありません</p>')
    parts.append("</div>")
    return "\n".join(parts)


def render_day(day: dict, split_latest: bool = False) -> str:
    """その日の一覧。split_latest なら最新の取得分を先頭の枠に分け、残りを下に並べる。"""
    munis = day["municipalities"]
    errors = [m for m in munis if m["status"] == "error"]
    with_new = [m for m in munis if m["new"] and m["status"] != "error"]
    record_only = [m for m in munis if m["record_only"] and not m["new"] and m["status"] != "error"]
    no_update = [m for m in munis if m["status"] == "ok" and not m["new"] and not m["record_only"]]
    unconfigured = [m for m in munis if m["status"] == "unconfigured"]

    body = []
    if day.get("first_run") and not any(m["new"] for m in munis):
        body.append('<p class="notice">初回の取得は記録のみ行いました。次回の取得から増えた記事を表示します。</p>')
    elif split_latest:
        body.append(render_latest(day))

    def rest(m: dict) -> list[dict]:
        return [i for i in m["new"] if not is_latest(i, day)] if split_latest else m["new"]

    # 取得エラーは目立つように先頭へ
    err_cards = []
    for m in by_region(errors):
        parts = [f'<section class="card err" {card_attrs(m["name"], len(rest(m)))}><h3>⚠ {esc(m["name"])} — 取得エラー</h3>']
        parts.append('<p class="msg">最新の取得で、一部または全部の取得元から取得できませんでした。</p>')
        parts.append('<ul class="errlist">' + "".join(f"<li>{esc(e)}</li>" for e in m["errors"]) + "</ul>")
        if rest(m):
            parts.append(render_items(rest(m)))
        parts.append("</section>")
        err_cards.append("".join(parts))
    body.append(render_mlist(err_cards, empty_msg=False))

    others = render_muni_cards([{**m, "new": rest(m)} for m in with_new])
    if split_latest and others:
        body.append("<h2>今日のそのほかの記事</h2>")
    body.append(render_mlist(others))

    for label, group in (("初回のため記録のみ", record_only), ("更新なし", no_update),
                         ("取得元未設定", unconfigured)):
        if group:
            body.append(f"<h4>{label}</h4>")
            body.append(f'<p class="names">{esc("、".join(m["name"] for m in group))}</p>')
    return "\n".join(body)


def render_panel(day: dict) -> str:
    """自治体の並び替え・絞り込みパネル。JavaScript が使えるときだけ表示する。"""
    groups = [(r, [n for n, _ in ms]) for r, ms in REGIONS]
    extra = sorted({m["name"] for m in day["municipalities"]} - set(MUNI_INFO))
    if extra:
        groups.append((OTHER_REGION, extra))
    sorts = (("region", "地域順"), ("count", "新着件数順"), ("kana", "五十音順"))
    parts = ['<aside class="panel" id="panel" aria-label="並び替え・絞り込み" hidden>',
             '<div class="phead"><h2>並び替え・絞り込み</h2>'
             '<button type="button" class="pclose" id="panel-close">閉じる</button></div>',
             '<fieldset class="psort"><legend>並び替え</legend>']
    parts += [f'<label><input type="radio" name="sort" value="{v}"{" checked" if v == "region" else ""}> {t}</label>'
              for v, t in sorts]
    parts.append('</fieldset><div class="pfilter"><p class="plabel">絞り込み</p>')
    for region, names in groups:
        parts.append(f'<fieldset class="pregion" data-region="{esc(region)}"><legend>{esc(region)}</legend>'
                     '<div class="pbtns"><button type="button" data-act="on">すべて選択</button>'
                     '<button type="button" data-act="off">すべて解除</button></div>')
        parts += [f'<label><input type="checkbox" name="muni" value="{esc(n)}" checked> {esc(n)}</label>'
                  for n in names]
        parts.append("</fieldset>")
    parts.append('</div></aside><div class="backdrop" id="panel-backdrop" hidden></div>')
    return "".join(parts)


# 並び替え・絞り込み。設定は localStorage に保存（使えない環境では保存せずに動く）
PANEL_JS = """
(function () {
  var KEY = "iwate-news-panel";
  var panel = document.getElementById("panel");
  var toggle = document.getElementById("panel-toggle");
  var backdrop = document.getElementById("panel-backdrop");
  if (!panel || !toggle) return;
  var boxes = Array.prototype.slice.call(panel.querySelectorAll('input[name="muni"]'));
  var radios = Array.prototype.slice.call(panel.querySelectorAll('input[name="sort"]'));
  var state = {sort: "region", off: []};
  try {
    var saved = JSON.parse(window.localStorage.getItem(KEY) || "null");
    if (saved && typeof saved === "object") {
      if (["region", "count", "kana"].indexOf(saved.sort) >= 0) state.sort = saved.sort;
      if (Array.isArray(saved.off)) state.off = saved.off.filter(function (x) { return typeof x === "string"; });
    }
  } catch (e) {}

  function save() {
    try { window.localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {}
  }
  function cmp(a, b) {
    var d = 0;
    if (state.sort === "count") d = Number(b.dataset.count) - Number(a.dataset.count);
    else if (state.sort === "kana") d = a.dataset.kana.localeCompare(b.dataset.kana, "ja");
    return d || Number(a.dataset.order) - Number(b.dataset.order);
  }
  function apply() {
    radios.forEach(function (r) { r.checked = r.value === state.sort; });
    boxes.forEach(function (b) { b.checked = state.off.indexOf(b.value) < 0; });
    Array.prototype.forEach.call(document.querySelectorAll(".mlist"), function (list) {
      var cards = Array.prototype.slice.call(list.querySelectorAll(".card"));
      var shown = 0, items = 0;
      cards.sort(cmp).forEach(function (c) {
        var on = state.off.indexOf(c.dataset.muni) < 0;
        c.hidden = !on;
        if (on) { shown++; items += Number(c.dataset.count); }
        list.appendChild(c);
      });
      var msg = list.nextElementSibling;
      if (msg && msg.classList.contains("fmsg")) msg.hidden = shown > 0;
      var meta = list.parentNode.querySelector(".shown");
      if (meta) meta.textContent = state.off.length ? "（表示中 " + items + "件）" : "";
    });
    var total = boxes.length, on = boxes.filter(function (b) { return b.checked; }).length;
    toggle.textContent = "並び替え・絞り込み" + (on < total ? "（" + on + "/" + total + "）" : "");
  }
  function setOpen(open) {
    document.body.classList.toggle("panel-open", open);
    toggle.setAttribute("aria-expanded", open ? "true" : "false");
    backdrop.hidden = !open;
    if (open) panel.focus();
  }

  radios.forEach(function (r) {
    r.addEventListener("change", function () { state.sort = r.value; save(); apply(); });
  });
  boxes.forEach(function (b) {
    b.addEventListener("change", function () {
      state.off = state.off.filter(function (x) { return x !== b.value; });
      if (!b.checked) state.off.push(b.value);
      save(); apply();
    });
  });
  Array.prototype.forEach.call(panel.querySelectorAll(".pregion"), function (fs) {
    Array.prototype.forEach.call(fs.querySelectorAll("button"), function (btn) {
      btn.addEventListener("click", function () {
        var names = Array.prototype.map.call(fs.querySelectorAll('input[name="muni"]'), function (b) { return b.value; });
        state.off = state.off.filter(function (x) { return names.indexOf(x) < 0; });
        if (btn.dataset.act === "off") state.off = state.off.concat(names);
        save(); apply();
      });
    });
  });
  toggle.addEventListener("click", function () { setOpen(!document.body.classList.contains("panel-open")); });
  document.getElementById("panel-close").addEventListener("click", function () { setOpen(false); toggle.focus(); });
  backdrop.addEventListener("click", function () { setOpen(false); });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && document.body.classList.contains("panel-open")) { setOpen(false); toggle.focus(); }
  });

  panel.tabIndex = -1;
  panel.hidden = false;
  toggle.hidden = false;
  document.body.classList.add("has-panel");
  apply();
})();
"""


# 更新ボタン。docs/config.json の trigger_url（中継サーバーのURL）が空ならボタンも状態も出さない
REFRESH_JS = """
(function () {
  var stateEl = document.getElementById("run-state");
  var btn = document.getElementById("refresh");
  var msg = document.getElementById("refresh-msg");
  var base = "", wasBusy = false, timer = null;

  function hm(iso) {
    var p = {};
    new Intl.DateTimeFormat("ja-JP", {timeZone: "Asia/Tokyo", hour: "numeric", minute: "numeric", hour12: false})
      .formatToParts(new Date(iso)).forEach(function (x) { p[x.type] = x.value; });
    return Number(p.hour) + "時" + Number(p.minute) + "分";
  }
  function clock(iso) {
    return new Intl.DateTimeFormat("ja-JP", {timeZone: "Asia/Tokyo", month: "numeric", day: "numeric",
      hour: "2-digit", minute: "2-digit"}).format(new Date(iso));
  }
  function describe(run) {
    if (!run) return ["", ""];
    var s = run.status, c = run.conclusion;
    if (s === "in_progress") return ["実行中（" + clock(run.startedAt) + " 開始）", "busy"];
    if (s !== "completed") return ["実行待ち（" + clock(run.createdAt) + " 受付）", "busy"];
    if (c === "success") return ["前回の実行: 成功（" + clock(run.updatedAt) + "）", ""];
    if (c === "cancelled") return ["前回の実行: 中止（" + clock(run.updatedAt) + "）", ""];
    return ["前回の実行: 失敗（" + clock(run.updatedAt) + "）", "fail"];
  }
  function show(st) {
    var d = describe(st.latest);
    stateEl.textContent = d[0];
    stateEl.className = "state " + d[1];
    if (wasBusy && !st.busy && st.latest && st.latest.conclusion === "success") {
      msg.innerHTML = "更新が完了しました。1〜2分後に<a href=\\"\\" onclick=\\"location.reload();return false\\">再読み込み</a>すると反映されます。";
    }
    wasBusy = st.busy;
    clearTimeout(timer);
    if (st.busy) timer = setTimeout(refreshStatus, 20000);
  }
  function refreshStatus() {
    return fetch(base + "/status", {cache: "no-store"})
      .then(function (r) { return r.json(); }).then(show).catch(function () {});
  }

  fetch("config.json", {cache: "no-store"}).then(function (r) { return r.json(); }).then(function (cfg) {
    base = String(cfg.trigger_url || "").trim().replace(/\\/+$/, "");
    if (!base) return;
    btn.hidden = false;
    refreshStatus();
    btn.addEventListener("click", function () {
      btn.disabled = true;
      msg.textContent = "送信中…";
      fetch(base + "/trigger", {method: "POST"}).then(function (r) {
        return r.json().then(function (body) { return {status: r.status, body: body}; });
      }).then(function (res) {
        if (res.status === 202) {
          msg.textContent = "受け付けました。5分ほどで反映されます";
          wasBusy = true;
          setTimeout(refreshStatus, 5000);
        } else if (res.status === 429 && res.body.nextAvailableAt) {
          msg.textContent = (res.body.reason === "busy" ? "ただいま更新中です。" : "") +
            "次に更新できるのは" + hm(res.body.nextAvailableAt) + "です";
        } else {
          msg.textContent = "受け付けられませんでした。時間をおいてもう一度お試しください";
        }
      }).catch(function () {
        msg.textContent = "通信に失敗しました。時間をおいてもう一度お試しください";
      }).then(function () { btn.disabled = false; });
    });
  }).catch(function () {});
})();
"""


def render_bar() -> str:
    return (
        '<div class="bar">'
        '<p class="state" id="run-state"></p>'
        '<button type="button" id="refresh" hidden>今すぐ更新</button>'
        '<p class="rmsg" id="refresh-msg" role="status" aria-live="polite"></p>'
        '</div>'
    )


def render_daynav(d: str, days: list[str], prefix: str) -> str:
    """前の日・次の日へのリンク（データのある日どうしをつなぐ）。days は新しい順。"""
    i = days.index(d)
    links = []
    if i + 1 < len(days):
        p = days[i + 1]
        links.append(f'<a class="prev" href="{prefix}archive/{p}.html">← 前の日（{esc(md(p))}）</a>')
    if i > 0:
        n = days[i - 1]
        href = f"{prefix}index.html" if i - 1 == 0 else f"{prefix}archive/{n}.html"
        links.append(f'<a class="next" href="{href}">次の日（{esc(md(n))}） →</a>')
    return f'<nav class="daynav">{"".join(links)}</nav>' if links else ""


def md(iso: str) -> str:
    d = date.fromisoformat(iso)
    return f"{d.month}月{d.day}日"


def render_page(day: dict, archive_dates: list[str], prefix: str, is_index: bool) -> str:
    total = sum(len(m["new"]) for m in day["municipalities"])

    body = ['<p class="site">岩手県 市町村新着情報</p>']
    body.append(f"<h1>{esc(jp_date(day['date']))}の更新</h1>")
    body.append(f'<p class="sub">最終取得 {esc(day.get("last_fetched", ""))}（日本時間） ・ 新着 {total} 件</p>')
    body.append(render_daynav(day["date"], archive_dates, prefix))
    if is_index:
        body.append(render_bar())
    body.append('<button type="button" class="ptoggle" id="panel-toggle" aria-controls="panel" '
                'aria-expanded="false" hidden>並び替え・絞り込み</button>')
    body.append(render_day(day, split_latest=is_index))

    body.append('<h3 class="foot">過去30日のアーカイブ</h3>')
    if not is_index:
        body.append(f'<p><a href="{prefix}index.html">← 最新の一覧へ</a></p>')
    links = "".join(
        f'<li><a href="{prefix}archive/{d}.html">{esc(jp_date(d))}</a></li>' for d in archive_dates
    )
    body.append(f'<ul class="archive">{links}</ul>')
    body.append("<footer>各記事の内容は各自治体の公式サイトでご確認ください。</footer>")

    title = f"{jp_date(day['date'])}の更新"
    return (
        "<!doctype html>\n<html lang=\"ja\"><head><meta charset=\"utf-8\">"
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        "<meta name=\"color-scheme\" content=\"light dark\">"
        f"<title>{esc(title)} | 岩手 市町村新着情報</title>"
        f"<style>{CSS}</style></head><body><div class=\"layout\">"
        + render_panel(day)
        + "<main>\n"
        + "\n".join(body)
        + "\n</main></div>"
        + f"<script>{PANEL_JS}</script>"
        + (f"<script>{REFRESH_JS}</script>" if is_index else "")
        + "</body></html>\n"
    )


def write_pages(today: date) -> None:
    cutoff = today - timedelta(days=ARCHIVE_DAYS - 1)
    ARCHIVE_DIR.mkdir(parents=True, exist_ok=True)

    # 30日より古い日次データとアーカイブを削除
    for path in list(DAILY_DIR.glob("*.json")) + list(ARCHIVE_DIR.glob("*.html")):
        try:
            if date.fromisoformat(path.stem) < cutoff:
                path.unlink()
        except ValueError:
            pass

    days = sorted((p.stem for p in DAILY_DIR.glob("*.json")), reverse=True)
    for d in days:
        data = load_daily(DAILY_DIR / f"{d}.json", d)
        (ARCHIVE_DIR / f"{d}.html").write_text(
            render_page(data, days, "../", is_index=False), encoding="utf-8")
    latest = load_daily(DAILY_DIR / f"{days[0]}.json", days[0])
    (DOCS_DIR / "index.html").write_text(render_page(latest, days, "", is_index=True), encoding="utf-8")
    (DOCS_DIR / ".nojekyll").touch()


def main() -> None:
    now = datetime.now(JST)
    result = run(now)
    today = now.date().isoformat()
    daily_path = DAILY_DIR / f"{today}.json"
    save_json(daily_path, add_fetch(load_daily(daily_path, today), result))
    write_pages(now.date())
    total = sum(len(m["new"]) for m in result["municipalities"])
    print(f"完了: {result['fetched_at']} 取得 新着 {total} 件", file=sys.stderr)


if __name__ == "__main__":
    main()
