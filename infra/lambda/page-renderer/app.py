"""동적 페이지를 헤드리스 브라우저로 렌더링해 HTML을 반환한다."""

import os
import resource
import shutil
import threading
import time
import traceback
from urllib.parse import urlsplit

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


def _resource_limit(name):
    limit_id = getattr(resource, name, None)
    if limit_id is None:
        return "unavailable"

    try:
        soft, hard = resource.getrlimit(limit_id)
        return f"{soft}/{hard}"
    except (OSError, ValueError):
        return "unavailable"


def _log_runtime_environment(context):
    """브라우저 프로세스 생성에 영향을 주는 Lambda 실행 환경만 기록한다."""
    request_id = getattr(context, "aws_request_id", "unknown")
    try:
        tmp_usage = shutil.disk_usage("/tmp")
        tmp_free_mib = f"{tmp_usage.free / 1024 / 1024:.1f}"
        tmp_total_mib = f"{tmp_usage.total / 1024 / 1024:.1f}"
    except OSError:
        tmp_free_mib = "unavailable"
        tmp_total_mib = "unavailable"

    try:
        process_count = sum(name.isdigit() for name in os.listdir("/proc"))
    except OSError:
        process_count = "unavailable"

    print(
        "[render-runtime] "
        f"requestId={request_id} "
        f"pid={os.getpid()} "
        f"pythonThreads={threading.active_count()} "
        f"processes={process_count} "
        f"home={os.environ.get('HOME', 'unavailable')} "
        f"xdgCacheHome={os.environ.get('XDG_CACHE_HOME', 'unavailable')} "
        f"tmpFreeMiB={tmp_free_mib} "
        f"tmpTotalMiB={tmp_total_mib} "
        f"nofile={_resource_limit('RLIMIT_NOFILE')} "
        f"nproc={_resource_limit('RLIMIT_NPROC')}"
    )


def _prepare_browser_environment():
    """읽기 전용 Lambda 파일시스템에서 브라우저 캐시를 /tmp로 고정한다."""
    os.environ["HOME"] = "/tmp"
    os.environ["XDG_CACHE_HOME"] = "/tmp/.cache"
    os.makedirs("/tmp/.cache/fontconfig", exist_ok=True)
    os.makedirs("/tmp/.pki/nssdb", exist_ok=True)


def _elapsed_ms(started_at):
    return (time.monotonic() - started_at) * 1000


def _log_setup(request_id, attempt, stage, started_at):
    print(
        "[render-setup] "
        f"requestId={request_id} "
        f"attempt={attempt} "
        f"stage={stage} "
        f"elapsedMs={_elapsed_ms(started_at):.1f}"
    )


def _log_exception(request_id, attempt, stage, exception, started_at):
    formatted_traceback = "".join(
        traceback.format_exception(type(exception), exception, exception.__traceback__)
    ).rstrip().replace("\n", "\\n")
    print(
        "[render-error] "
        f"requestId={request_id} "
        f"attempt={attempt} "
        f"stage={stage} "
        f"elapsedMs={_elapsed_ms(started_at):.1f} "
        f"exceptionType={type(exception).__name__} "
        f"exception={exception!r} "
        f"traceback={formatted_traceback}"
    )


def _browser_instance(request_id="unknown", attempt=1, started_at=None):
    """웜 컨테이너에서 브라우저를 재사용한다. 죽어 있으면 다시 띄운다."""
    global _playwright, _browser
    started_at = started_at or time.monotonic()
    _prepare_browser_environment()

    if _browser is not None and _browser.is_connected():
        _log_setup(request_id, attempt, "browser-reused", started_at)
        return _browser

    if _playwright is None:
        _log_setup(request_id, attempt, "playwright-starting", started_at)
        _playwright = sync_playwright().start()
        _log_setup(request_id, attempt, "playwright-started", started_at)

    _log_setup(request_id, attempt, "chromium-launching", started_at)
    _browser = _playwright.chromium.launch(headless=True, args=LAUNCH_ARGS)
    _browser.on(
        "disconnected",
        lambda _: print(
            "[render-browser] "
            f"event=disconnected createdByRequestId={request_id} "
            f"createdByAttempt={attempt}"
        ),
    )
    _log_setup(
        request_id,
        attempt,
        f"chromium-launched connected={_browser.is_connected()}",
        started_at,
    )
    return _browser


def _discard_runtime(request_id="unknown", attempt=1, started_at=None):
    """손상된 브라우저와 Playwright driver를 함께 버린다."""
    global _playwright, _browser
    started_at = started_at or time.monotonic()

    browser = _browser
    playwright = _playwright
    _browser = None
    _playwright = None
    _log_setup(request_id, attempt, "runtime-discard-starting", started_at)

    if browser is not None:
        try:
            browser.close()
        except PlaywrightError as exception:
            _log_exception(
                request_id,
                attempt,
                "browser-close-failed",
                exception,
                started_at,
            )

    if playwright is not None:
        try:
            playwright.stop()
        except PlaywrightError as exception:
            _log_exception(
                request_id,
                attempt,
                "playwright-stop-failed",
                exception,
                started_at,
            )

    _log_setup(request_id, attempt, "runtime-discarded", started_at)


def _new_page(user_agent, request_id="unknown"):
    """손상된 런타임이면 Playwright부터 한 번 새로 시작해 page를 만든다."""
    for attempt in range(2):
        browser_context = None
        attempt_number = attempt + 1
        attempt_started_at = time.monotonic()
        try:
            _log_setup(
                request_id,
                attempt_number,
                "browser-requested",
                attempt_started_at,
            )
            browser = _browser_instance(
                request_id,
                attempt_number,
                attempt_started_at,
            )
            _log_setup(
                request_id,
                attempt_number,
                "context-creating",
                attempt_started_at,
            )
            browser_context = (
                browser.new_context(user_agent=user_agent)
                if user_agent
                else browser.new_context()
            )
            _log_setup(
                request_id,
                attempt_number,
                "context-created",
                attempt_started_at,
            )
            _log_setup(
                request_id,
                attempt_number,
                "page-creating",
                attempt_started_at,
            )
            page = browser_context.new_page()
            _attach_page_diagnostics(page, request_id)
            _log_setup(
                request_id,
                attempt_number,
                "page-created",
                attempt_started_at,
            )
            return browser_context, page
        except PlaywrightError as exception:
            _log_exception(
                request_id,
                attempt_number,
                "page-setup-failed",
                exception,
                attempt_started_at,
            )
            if browser_context is not None:
                try:
                    browser_context.close()
                except PlaywrightError as close_exception:
                    _log_exception(
                        request_id,
                        attempt_number,
                        "context-close-failed",
                        close_exception,
                        attempt_started_at,
                    )
            _discard_runtime(
                request_id,
                attempt_number,
                attempt_started_at,
            )
            if attempt == 1:
                raise
            _log_setup(
                request_id,
                attempt_number,
                "retrying-with-new-runtime",
                attempt_started_at,
            )


def _url_origin(url):
    """서명·쿼리 값을 로그에 노출하지 않고 실패한 호스트만 식별한다."""
    try:
        parsed = urlsplit(url)
        return f"{parsed.scheme}://{parsed.netloc}"
    except (TypeError, ValueError):
        return "unavailable"


def _console_message(message, request_id):
    message_type = message.type
    if message_type not in ("warning", "error"):
        return

    location = message.location or {}
    print(
        "[render-console] "
        f"requestId={request_id} "
        f"type={message_type} "
        f"origin={_url_origin(location.get('url', ''))}"
    )


def _page_error(exception, request_id):
    print(
        "[render-page-error] "
        f"requestId={request_id} "
        f"exceptionType={type(exception).__name__} "
        f"exception={exception!r}"
    )


def _request_failed(request, request_id):
    print(
        "[render-request-failed] "
        f"requestId={request_id} "
        f"resourceType={request.resource_type} "
        f"origin={_url_origin(request.url)} "
        f"failure={request.failure}"
    )


def _response_received(response, request_id):
    if response.status < 400:
        return

    print(
        "[render-http-error] "
        f"requestId={request_id} "
        f"status={response.status} "
        f"resourceType={response.request.resource_type} "
        f"origin={_url_origin(response.url)}"
    )


def _attach_page_diagnostics(page, request_id):
    """본문 미제공 원인을 구분할 수 있는 브라우저 이벤트만 기록한다."""
    seen_events = set()

    def once(key, callback):
        if key in seen_events:
            return
        seen_events.add(key)
        callback()

    page.on(
        "console",
        lambda message: once(
            ("console", message.type, _url_origin((message.location or {}).get("url", ""))),
            lambda: _console_message(message, request_id),
        ),
    )
    page.on(
        "pageerror",
        lambda exception: once(
            ("pageerror", type(exception).__name__, str(exception)),
            lambda: _page_error(exception, request_id),
        ),
    )
    page.on(
        "requestfailed",
        lambda request: once(
            (
                "requestfailed",
                request.resource_type,
                _url_origin(request.url),
                request.failure,
            ),
            lambda: _request_failed(request, request_id),
        ),
    )
    page.on(
        "response",
        lambda response: once(
            (
                "response",
                response.status,
                response.request.resource_type,
                _url_origin(response.url),
            ),
            lambda: _response_received(response, request_id),
        ),
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
    request_started_at = time.monotonic()
    _log_memory("request-start", context)
    _log_runtime_environment(context)

    url = (event or {}).get("url")
    if not url:
        _log_memory("request-rejected", context)
        return {"error": "URL_REQUIRED"}

    timeout_ms = int((event or {}).get("timeoutMs") or DEFAULT_TIMEOUT_MS)

    user_agent = (event or {}).get("userAgent")
    request_id = getattr(context, "aws_request_id", "unknown")
    try:
        browser_context, page = _new_page(user_agent, request_id)
    except PlaywrightError as exception:
        _log_exception(
            request_id,
            2,
            "launch-failed",
            exception,
            request_started_at,
        )
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
