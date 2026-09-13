"""동적 페이지를 헤드리스 브라우저로 렌더링해 HTML을 반환한다."""

from urllib.parse import urlparse

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

DEFAULT_TIMEOUT_MS = 20_000

NOTION_DOMAIN = "notion.com"
NOTION_PAGE_SETTLE_WAIT_MS = 2_000
NOTION_TOGGLE_CLICK_WAIT_MS = 500
MAX_NOTION_TOGGLE_CLICKS = 100
NOTION_CLOSED_TOGGLE_SELECTOR = (
    '.notion-focusable[aria-expanded="false"], '
    '[role="button"][aria-expanded="false"][aria-label="Open"]'
)

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


def _is_notion_url(url):
    try:
        host = (urlparse(url).hostname or "").lower()
    except ValueError:
        return False

    return host == NOTION_DOMAIN or host.endswith(f".{NOTION_DOMAIN}")


def _expand_notion_toggles(page):
    """Notion 공개 페이지의 닫힌 toggle을 반복적으로 찾아 모두 연다."""
    expanded_count = 0
    for _ in range(MAX_NOTION_TOGGLE_CLICKS):
        toggles = page.locator(NOTION_CLOSED_TOGGLE_SELECTOR)
        before_count = toggles.count()
        if before_count == 0:
            break

        toggle = toggles.nth(0)
        if not toggle.is_visible():
            print("[render] notion toggle skipped reason=not-visible")
            break

        try:
            # Locator는 DOM 변경 후 재매칭되므로 클릭 대상의 handle을 보존한다.
            toggle_handle = toggle.element_handle()
            toggle.scroll_into_view_if_needed()
            toggle.click(timeout=2_000, force=True)
            page.wait_for_timeout(NOTION_TOGGLE_CLICK_WAIT_MS)
        except PlaywrightError as exception:
            print(f"[render] notion toggle click failed reason={exception}")
            break

        handle_expanded = (
            toggle_handle is not None
            and toggle_handle.get_attribute("aria-expanded") == "true"
        )
        after_count = page.locator(NOTION_CLOSED_TOGGLE_SELECTOR).count()
        if not handle_expanded and after_count >= before_count:
            print(
                "[render] notion toggle click had no effect "
                f"collapsed={before_count}->{after_count}"
            )
            break

        expanded_count += 1

    remaining = page.locator(NOTION_CLOSED_TOGGLE_SELECTOR).count()
    print(
        "[render] notion toggle expansion finished "
        f"expanded={expanded_count} remaining={remaining}"
    )


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

        if _is_notion_url(page.url):
            try:
                page.wait_for_function(
                    """
                    () => document.body &&
                          document.body.innerText &&
                          document.body.innerText.trim().length > 0
                    """,
                    timeout=5_000,
                )
            except PlaywrightTimeoutError:
                print(f"[render] notion body wait timeout url={page.url}")
            page.wait_for_timeout(NOTION_PAGE_SETTLE_WAIT_MS)
            _log_memory("notion-settled", context)
            _expand_notion_toggles(page)
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
