"""JavaScript로 본문을 그리는 페이지를 헤드리스 브라우저로 받아 HTML을 돌려준다.

백엔드는 jsoup 결과가 비어 있을 때만 이 함수를 부른다. 폴백이 드물어 동시성을 낮게 잡고,
브라우저는 웜 컨테이너에서 재사용한다.
"""

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

DEFAULT_TIMEOUT_MS = 20_000
# 동기 invoke 응답 페이로드 상한이 6MB다. 백엔드의 max-body-bytes와 같은 4MB로 자른다.
MAX_HTML_BYTES = 4 * 1024 * 1024

# Lambda에는 user namespace가 없어 샌드박스를 못 쓰고, /dev/shm 도 좁다.
LAUNCH_ARGS = [
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--single-process",
    "--disable-gpu",
]

_playwright = None
_browser = None


def _browser_instance():
    """웜 컨테이너에서 브라우저를 재사용한다. 죽어 있으면 다시 띄운다."""
    global _playwright, _browser

    if _browser is not None and _browser.is_connected():
        return _browser

    if _playwright is None:
        _playwright = sync_playwright().start()

    _browser = _playwright.chromium.launch(headless=True, args=LAUNCH_ARGS)
    return _browser


def _truncate(html):
    encoded = html.encode("utf-8")
    if len(encoded) <= MAX_HTML_BYTES:
        return html, False

    return encoded[:MAX_HTML_BYTES].decode("utf-8", errors="ignore"), True


def handler(event, context):
    url = (event or {}).get("url")
    if not url:
        return {"error": "URL_REQUIRED"}

    timeout_ms = int((event or {}).get("timeoutMs") or DEFAULT_TIMEOUT_MS)

    try:
        browser = _browser_instance()
    except PlaywrightError as exception:
        print(f"[render] launch failed reason={exception}")
        return {"error": "LAUNCH_FAILED"}

    user_agent = (event or {}).get("userAgent")
    browser_context = (
        browser.new_context(user_agent=user_agent)
        if user_agent
        else browser.new_context()
    )
    page = browser_context.new_page()

    try:
        response = page.goto(url, timeout=timeout_ms)

        # Playwright는 4xx/5xx도 탐색 성공으로 취급한다. 그대로 page.content()를 반환하면
        # Cloudflare 차단 안내나 로그인 오류 페이지가 실제 문서로 저장될 수 있다.
        if response is not None and not response.ok:
            print(f"[render] http error url={page.url} status={response.status}")
            return {"error": "HTTP_ERROR", "status": response.status}

        # networkidle 까지 못 가도 이미 그려진 본문이 쓸 만한 경우가 많다. 대기 실패는 삼킨다.
        try:
            page.wait_for_load_state("networkidle", timeout=timeout_ms)
        except PlaywrightTimeoutError:
            print(f"[render] networkidle timeout url={url}")

        html, truncated = _truncate(page.content())
        return {"html": html, "truncated": truncated}

    except PlaywrightTimeoutError:
        print(f"[render] timeout url={url}")
        return {"error": "TIMEOUT"}
    except PlaywrightError as exception:
        print(f"[render] failed url={url} reason={exception}")
        return {"error": "RENDER_FAILED"}
    finally:
        page.close()
        browser_context.close()
