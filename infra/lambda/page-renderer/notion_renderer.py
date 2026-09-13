"""Notion 공개 페이지의 본문 준비 상태와 접이식 블록을 처리한다."""

from urllib.parse import urlparse

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError

NOTION_DOMAINS = ("notion.com", "notion.site")
CONTENT_WAIT_MS = 10_000
PAGE_SETTLE_WAIT_MS = 2_000
TOGGLE_CLICK_WAIT_MS = 500
MAX_TOGGLE_CLICKS = 100
CLOSED_TOGGLE_SELECTOR = (
    '.notion-page-content .notion-selectable.notion-toggle-block '
    '[role="button"][aria-expanded="false"], '
    '.notion-page-content .notion-selectable.notion-header-block '
    '[role="button"][aria-expanded="false"], '
    '.notion-page-content .notion-selectable.notion-sub_header-block '
    '[role="button"][aria-expanded="false"], '
    '.notion-page-content .notion-selectable.notion-sub_sub_header-block '
    '[role="button"][aria-expanded="false"]'
)


def is_notion_url(url):
    try:
        host = (urlparse(url).hostname or "").lower()
    except ValueError:
        return False

    return any(
        host == domain or host.endswith(f".{domain}")
        for domain in NOTION_DOMAINS
    )


def wait_for_content(page):
    """Notion 앱 셸이 아니라 실제 문서 블록이 렌더링될 때까지 기다린다."""
    try:
        page.wait_for_function(
            """
            () => {
                const content = document.querySelector('.notion-page-content');
                if (!content || !content.innerText.trim()) {
                    return false;
                }

                return content.querySelector('.notion-selectable') !== null;
            }
            """,
            timeout=CONTENT_WAIT_MS,
        )
        return True
    except PlaywrightTimeoutError:
        print(f"[render] notion content wait timeout url={page.url}")
        return False


def settle(page):
    page.wait_for_timeout(PAGE_SETTLE_WAIT_MS)


def expand_toggles(page):
    """Notion 본문의 닫힌 toggle과 접이식 heading을 모두 연다."""
    expanded_count = 0
    for _ in range(MAX_TOGGLE_CLICKS):
        toggles = page.locator(CLOSED_TOGGLE_SELECTOR)
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
            page.wait_for_timeout(TOGGLE_CLICK_WAIT_MS)
        except PlaywrightError as exception:
            print(f"[render] notion toggle click failed reason={exception}")
            break

        handle_expanded = (
            toggle_handle is not None
            and toggle_handle.get_attribute("aria-expanded") == "true"
        )
        after_count = page.locator(CLOSED_TOGGLE_SELECTOR).count()
        if not handle_expanded and after_count >= before_count:
            print(
                "[render] notion toggle click had no effect "
                f"collapsed={before_count}->{after_count}"
            )
            break

        expanded_count += 1

    remaining = page.locator(CLOSED_TOGGLE_SELECTOR).count()
    print(
        "[render] notion toggle expansion finished "
        f"expanded={expanded_count} remaining={remaining}"
    )
