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
import os
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
# 定時実行の cron 式 → 見出しの時刻（daily.yml の schedule と合わせる）
SCHEDULE_LABELS = {"0 2 * * *": "11:00", "0 7 * * *": "16:00"}


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

def run_label(now: datetime) -> str:
    """定時実行なら「11:00」「16:00」、手動実行なら実際の時刻を返す。"""
    return SCHEDULE_LABELS.get(os.environ.get("RUN_SCHEDULE", "").strip(), now.strftime("%H:%M"))


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
    return {"label": run_label(now), "updated_at": now.strftime("%H:%M"),
            "first_run": first_run, "municipalities": results}


def load_daily(path: Path, day: str) -> dict:
    data = load_json(path, None) or {"date": day, "runs": []}
    if "runs" not in data:  # 1日1回だった頃の形式
        legacy = {k: data[k] for k in ("updated_at", "first_run", "municipalities") if k in data}
        legacy["label"] = legacy.get("updated_at", "")
        data = {"date": data["date"], "runs": [legacy]}
    return data


def add_run(daily: dict, new: dict) -> dict:
    """その日の実行結果に今回の回を加える。

    同じ見出しの回（定時実行の再実行など）がすでにあれば、先に見つけた新着を残したまま更新する。
    """
    old = next((r for r in daily["runs"] if r.get("label") == new["label"]), None)
    if old is not None:
        old_by_name = {m["name"]: m for m in old.get("municipalities", [])}
        for muni in new["municipalities"]:
            prev = old_by_name.get(muni["name"])
            if not prev:
                continue
            urls = {i["url"] for i in muni["new"]}
            muni["new"] = muni["new"] + [i for i in prev.get("new", []) if i["url"] not in urls]
            muni["new"].sort(key=lambda i: i["date"], reverse=True)
            muni["record_only"] = muni["record_only"] or prev.get("record_only", False)
        new["first_run"] = new["first_run"] or old.get("first_run", False)
        daily["runs"].remove(old)
    daily["runs"].append(new)
    daily["runs"].sort(key=lambda r: r.get("updated_at", ""), reverse=True)
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
.runs{display:flex;flex-wrap:wrap;gap:6px;list-style:none;padding:0;margin:0 0 8px}
.runs a{display:inline-block;padding:4px 12px;border-radius:999px;background:var(--chip);font-size:.85rem}
section.run{margin:28px 0 0;padding-top:20px;border-top:2px solid var(--line)}
section.run:first-of-type{border-top:0;padding-top:0}
h2.runhead{font-size:1.15rem;line-height:1.4;margin:0 0 4px}
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
.notice{background:var(--chip);border-radius:12px;padding:12px 16px;margin:0 0 16px}
h4{font-size:.95rem;margin:20px 0 6px;color:var(--muted)}
h3.foot{font-size:.95rem;margin:36px 0 8px;color:var(--muted)}
.names{margin:0;font-size:.9rem;color:var(--muted)}
.archive{display:flex;flex-wrap:wrap;gap:6px;list-style:none;padding:0;margin:0}
.archive a{display:inline-block;padding:4px 10px;border:1px solid var(--line);
border-radius:8px;background:var(--card);font-size:.85rem}
footer{margin-top:32px;font-size:.78rem;color:var(--muted)}
"""


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


def render_run(day: str, run: dict) -> str:
    munis = run["municipalities"]
    errors = [m for m in munis if m["status"] == "error"]
    with_new = [m for m in munis if m["new"] and m["status"] != "error"]
    record_only = [m for m in munis if m["record_only"] and not m["new"] and m["status"] != "error"]
    no_update = [m for m in munis if m["status"] == "ok" and not m["new"] and not m["record_only"]]
    unconfigured = [m for m in munis if m["status"] == "unconfigured"]
    total = sum(len(m["new"]) for m in munis)

    body = [f'<section class="run" id="{run_anchor(run)}">']
    body.append(f'<h2 class="runhead">{esc(jp_date(day))} {esc(run["label"])} 更新分</h2>')
    body.append(f'<p class="sub">新着 {total} 件 ・ 取得 {esc(run.get("updated_at", ""))}（日本時間）</p>')

    if run.get("first_run"):
        body.append('<p class="notice">初回のため記録のみ行いました。次回の更新から新着を表示します。</p>')

    # 取得エラーは目立つように先頭へ
    for m in errors:
        parts = [f'<section class="card err"><h3>⚠ {esc(m["name"])} — 取得エラー</h3>']
        parts.append('<p class="msg">一部または全部の取得元から取得できませんでした。</p>')
        parts.append('<ul class="errlist">' + "".join(f"<li>{esc(e)}</li>" for e in m["errors"]) + "</ul>")
        if m["new"]:
            parts.append(render_items(m["new"]))
        parts.append("</section>")
        body.append("".join(parts))

    for m in with_new:
        body.append(f'<section class="card"><h3>{esc(m["name"])}'
                    f'<span class="count">{len(m["new"])}件</span></h3>'
                    f'{render_items(m["new"])}</section>')

    for label, group in (("初回のため記録のみ", record_only), ("更新なし", no_update),
                         ("取得元未設定", unconfigured)):
        if group:
            body.append(f"<h4>{label}</h4>")
            body.append(f'<p class="names">{esc("、".join(m["name"] for m in group))}</p>')
    body.append("</section>")
    return "\n".join(body)


def run_anchor(run: dict) -> str:
    return "run-" + re.sub(r"\D", "", run.get("label", ""))


def render_page(day: dict, archive_dates: list[str], prefix: str, is_index: bool) -> str:
    runs = day["runs"]
    total = sum(len(m["new"]) for r in runs for m in r["municipalities"])

    body = ["<h1>岩手県 市町村新着情報</h1>"]
    body.append(f'<p class="sub">{esc(jp_date(day["date"]))}の新着 {total} 件（{len(runs)} 回分・新しい順）</p>')
    if len(runs) > 1:
        chips = "".join(f'<li><a href="#{run_anchor(r)}">{esc(r["label"])} 更新分</a></li>' for r in runs)
        body.append(f'<ul class="runs">{chips}</ul>')
    body.extend(render_run(day["date"], r) for r in runs)

    body.append('<h3 class="foot">過去30日のアーカイブ</h3>')
    if not is_index:
        body.append(f'<p><a href="{prefix}index.html">← 最新の更新分へ</a></p>')
    links = "".join(
        f'<li><a href="{prefix}archive/{d}.html">{esc(jp_date(d))}</a></li>' for d in archive_dates
    )
    body.append(f'<ul class="archive">{links}</ul>')
    body.append("<footer>各記事の内容は各自治体の公式サイトでご確認ください。</footer>")

    title = f"{jp_date(day['date'])}の新着"
    return (
        "<!doctype html>\n<html lang=\"ja\"><head><meta charset=\"utf-8\">"
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
        "<meta name=\"color-scheme\" content=\"light dark\">"
        f"<title>{esc(title)} | 岩手 市町村新着情報</title>"
        f"<style>{CSS}</style></head><body><main>\n"
        + "\n".join(body)
        + "\n</main></body></html>\n"
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
    save_json(daily_path, add_run(load_daily(daily_path, today), result))
    write_pages(now.date())
    total = sum(len(m["new"]) for m in result["municipalities"])
    print(f"完了: {result['label']} 更新分 新着 {total} 件", file=sys.stderr)


if __name__ == "__main__":
    main()
