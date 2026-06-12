from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import csv
import re
import time


ROOT = Path(__file__).resolve().parents[1]
SEED_PATH = ROOT / "tools" / "seed_urls.txt"
OUTPUT_PATH = ROOT / "output" / "campus_docs.csv"

CATEGORY = "校园资料"
SOURCE_TYPE = "官网爬虫"
REQUEST_DELAY_SECONDS = 1.5
TIMEOUT_SECONDS = 12


class CampusPageParser(HTMLParser):
    """提取网页标题和正文文本，跳过脚本、样式等无关内容。"""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.title_parts = []
        self.body_parts = []
        self.in_title = False
        self.in_body = False
        self.skip_depth = 0

    def handle_starttag(self, tag, attrs):
        tag = tag.lower()
        if tag == "title":
            self.in_title = True
        elif tag == "body":
            self.in_body = True

        if tag in {"script", "style", "noscript", "svg", "canvas"}:
            self.skip_depth += 1

    def handle_endtag(self, tag):
        tag = tag.lower()
        if tag == "title":
            self.in_title = False
        elif tag == "body":
            self.in_body = False

        if tag in {"script", "style", "noscript", "svg", "canvas"} and self.skip_depth > 0:
            self.skip_depth -= 1

    def handle_data(self, data):
        if self.skip_depth > 0:
            return

        text = clean_text(data)
        if not text:
            return

        if self.in_title:
            self.title_parts.append(text)
        elif self.in_body:
            self.body_parts.append(text)

    @property
    def title(self):
        return clean_text(" ".join(self.title_parts))

    @property
    def content(self):
        return clean_text(" ".join(self.body_parts))


def clean_text(value):
    """压缩多余空白，去掉不可见字符。"""
    if not value:
        return ""
    value = value.replace("\u3000", " ")
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def read_seed_urls():
    if not SEED_PATH.exists():
        raise FileNotFoundError(f"未找到 URL 种子文件：{SEED_PATH}")

    urls = []
    for line in SEED_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        urls.append(line)
    return urls


def fetch_html(url):
    request = Request(
        url,
        headers={
            "User-Agent": "campus-qa-course-crawler/1.0 (+offline knowledge collection)"
        },
    )
    with urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        content_type = response.headers.get("Content-Type", "")
        raw = response.read()

    charset = detect_charset(content_type) or "utf-8"
    return raw.decode(charset, errors="ignore")


def detect_charset(content_type):
    match = re.search(r"charset=([\w\-]+)", content_type, re.IGNORECASE)
    if match:
        return match.group(1)
    return None


def parse_page(url, html):
    parser = CampusPageParser()
    parser.feed(html)

    title = parser.title or url
    content = parser.content
    if not content:
        raise ValueError("页面正文为空")

    return {
        "title": title,
        "content": content,
        "category": CATEGORY,
        "source_url": url,
        "source_type": SOURCE_TYPE,
    }


def write_csv(rows):
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(
            file,
            fieldnames=["title", "content", "category", "source_url", "source_type"],
        )
        writer.writeheader()
        writer.writerows(rows)


def crawl():
    urls = read_seed_urls()
    rows = []

    print(f"读取到 {len(urls)} 个 URL。")
    for index, url in enumerate(urls, start=1):
        print(f"[{index}/{len(urls)}] 正在采集：{url}")
        try:
            html = fetch_html(url)
            rows.append(parse_page(url, html))
            print("  成功")
        except HTTPError as error:
            print(f"  失败：HTTP {error.code} {error.reason}")
        except URLError as error:
            print(f"  失败：网络错误 {error.reason}")
        except Exception as error:
            print(f"  失败：{error}")

        if index < len(urls):
            time.sleep(REQUEST_DELAY_SECONDS)

    write_csv(rows)
    print(f"采集完成，成功 {len(rows)} 条。")
    print(f"CSV 输出位置：{OUTPUT_PATH}")


if __name__ == "__main__":
    crawl()
