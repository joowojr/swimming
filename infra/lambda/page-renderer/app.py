"""동적 페이지를 헤드리스 브라우저로 렌더링해 HTML을 반환한다."""

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

import notion_renderer

DEFAULT_TIMEOUT_MS = 20_000

# 동기 invoke 응답 페이로드 상한이 6MB다. 백엔드의 max-body-bytes와 같은 4MB로 자른다.
MAX_HTML_BYTES = 4 * 1024 * 1024

# Lambda에는 user namespace가 없어 샌드박스를 못 쓰고, /dev/shm 도 좁다.
LAUNCH_ARGS = [
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--disable-gpu",
]

_playwright = None
_browser = None

CGROUP_CURRENT_MEMORY_PATHS = (
    "/sys/fs/cgroup/memory.current",
    "/sys/fs/cgroup/memory/memory.usage_in_bytes",
)
CGROUP_PEAK_MEMORY_PATHS = (
    "/sys/fs/cgroup/memory.peak",
    "/sys/fs/cgroup/memory/memory.max_usage_in_bytes",
)
CGROUP_LIMIT_MEMORY_PATHS = (
    "/sys/fs/cgroup/memory.max",
    "/sys/fs/cgroup/memory/memory.limit_in_bytes",
)


def _read_cgroup_bytes(paths):
    for path in paths:
        try:
            with open(path, encoding="utf-8") as value_file:
                value = value_file.read().strip()
        except OSError:
            continue

        if value == "max":
            return None

        try:
            return int(value)
        except ValueError:
            continue

    return None


def _mebibytes(value):
    return "unavailable" if value is None else f"{value / 1024 / 1024:.1f}"


def _log_memory(stage, context):
    """Chromium 자식 프로세스까지 포함한 실행 환경의 cgroup 메모리를 기록한다."""
    request_id = getattr(context, "aws_request_id", "unknown")
    current = _read_cgroup_bytes(CGROUP_CURRENT_MEMORY_PATHS)
    peak = _read_cgroup_bytes(CGROUP_PEAK_MEMORY_PATHS)
    limit = _read_cgroup_bytes(CGROUP_LIMIT_MEMORY_PATHS)
    print(
        "[render-memory] "
        f"requestId={request_id} "
        f"stage={stage} "
        f"currentMiB={_mebibytes(current)} "
        f"environmentPeakMiB={_mebibytes(peak)} "
        f"limitMiB={_mebibytes(limit)}"
    )


def _browser_instance():
    """웜 컨테이너에서 브라우저를 재사용한다. 죽어 있으면 다시 띄운다."""
    global _playwright, _browser

    if _browser is not None and _browser.is_connected():
        return _browser

    if _playwright is None:
        _playwright = sync_playwright().start()

    _browser = _playwright.chromium.launch(headless=True, args=LAUNCH_ARGS)
    return _browser


def _discard_browser():
    """연결이 끊긴 웜 브라우저를 버려 다음 시도에서 새로 띄운다."""
    global _browser

    browser = _browser
    _browser = None
    if browser is None:
        return

    try:
        browser.close()
    except PlaywrightError:
        pass


def _new_page(user_agent):
    """죽은 웜 브라우저라면 한 번 새로 띄워 context와 page를 만든다."""
    for attempt in range(2):
        browser_context = None
        try:
            browser = _browser_instance()
            browser_context = (
                browser.new_context(user_agent=user_agent)
                if user_agent
                else browser.new_context()
            )
            return browser_context, browser_context.new_page()
        except PlaywrightError:
            if browser_context is not None:
                try:
                    browser_context.close()
                except PlaywrightError:
                    pass
            _discard_browser()
            if attempt == 1:
                raise
            print("[render] browser disconnected; retrying with a new browser")


def _truncate(html):
    encoded = html.encode("utf-8")
    if len(encoded) <= MAX_HTML_BYTES:
        return html, False

    return encoded[:MAX_HTML_BYTES].decode("utf-8", errors="ignore"), True


def _close(resource):
    try:
        resource.close()
    except PlaywrightError:
        pass


def handler(event, context):
    _log_memory("request-start", context)

    url = (event or {}).get("url")
    if not url:
        _log_memory("request-rejected", context)
        return {"error": "URL_REQUIRED"}

    timeout_ms = int((event or {}).get("timeoutMs") or DEFAULT_TIMEOUT_MS)

    user_agent = (event or {}).get("userAgent")
    try:
        browser_context, page = _new_page(user_agent)
    except PlaywrightError as exception:
        print(f"[render] launch failed reason={exception}")
        _log_memory("launch-failed", context)
        return {"error": "LAUNCH_FAILED"}

    _log_memory("page-created", context)

    try:
        response = page.goto(url, timeout=timeout_ms)
        _log_memory("navigation-finished", context)
        print(
            f"[render] navigation "
            f"requestedUrl={url} "
            f"finalUrl={page.url}"
        )

        # Playwright는 4xx/5xx도 탐색 성공으로 취급한다. 그대로 page.content()를 반환하면
        # Cloudflare 차단 안내나 로그인 오류 페이지가 실제 문서로 저장될 수 있다.
        if response is not None and not response.ok:
            print(f"[render] http error url={page.url} status={response.status}")
            return {"error": "HTTP_ERROR", "status": response.status}

        if notion_renderer.is_notion_url(page.url):
            if not notion_renderer.wait_for_content(page):
                _log_memory("notion-content-unavailable", context)
                return {"error": "NOTION_CONTENT_UNAVAILABLE"}

            notion_renderer.settle(page)
            _log_memory("notion-settled", context)
            notion_renderer.expand_toggles(page)
            _log_memory("notion-toggles-finished", context)
        else:
            try:
                page.wait_for_load_state(
                    "networkidle",
                    timeout=timeout_ms,
                )
            except PlaywrightTimeoutError:
                print(f"[render] networkidle timeout url={url}")
            _log_memory("networkidle-finished", context)

        html, truncated = _truncate(page.content())
        _log_memory("content-extracted", context)
        return {"html": html, "truncated": truncated}

    except PlaywrightTimeoutError:
        print(f"[render] timeout url={url}")
        return {"error": "TIMEOUT"}
    except PlaywrightError as exception:
        print(f"[render] failed url={url} reason={exception}")
        return {"error": "RENDER_FAILED"}
    finally:
        _close(page)
        _close(browser_context)
        _log_memory("request-finished", context)
