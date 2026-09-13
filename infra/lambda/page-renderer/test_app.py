import unittest
from unittest.mock import Mock, patch

import app


class HandlerTest(unittest.TestCase):
    @patch.object(app, "_log_memory")
    @patch.object(app.notion_renderer, "wait_for_content", return_value=False)
    @patch.object(app, "_new_page")
    def test_notion_본문이_없으면_html_대신_오류를_반환한다(
        self,
        new_page,
        wait_for_notion_content,
        log_memory,
    ):
        browser_context = Mock()
        page = Mock()
        page.url = "https://workspace.notion.site/example"
        page.goto.return_value = Mock(ok=True)
        new_page.return_value = browser_context, page

        result = app.handler(
            {"url": "https://app.notion.com/p/example"},
            Mock(aws_request_id="request-id"),
        )

        self.assertEqual({"error": "NOTION_CONTENT_UNAVAILABLE"}, result)
        wait_for_notion_content.assert_called_once_with(page)
        log_memory.assert_any_call("notion-content-unavailable", unittest.mock.ANY)


class RuntimeRecoveryTest(unittest.TestCase):
    def tearDown(self):
        app._browser = None
        app._playwright = None

    @patch.object(app, "sync_playwright")
    def test_page_생성_실패_후_playwright부터_새로_시작한다(self, sync_playwright):
        stale_playwright = Mock()
        stale_browser = Mock()
        stale_browser.is_connected.return_value = True
        stale_context = Mock()
        stale_context.new_page.side_effect = app.PlaywrightError("closed")
        stale_browser.new_context.return_value = stale_context

        fresh_page = Mock()
        fresh_context = Mock()
        fresh_context.new_page.return_value = fresh_page
        fresh_browser = Mock()
        fresh_browser.is_connected.return_value = True
        fresh_browser.new_context.return_value = fresh_context
        fresh_playwright = Mock()
        fresh_playwright.chromium.launch.return_value = fresh_browser
        sync_playwright.return_value.start.return_value = fresh_playwright

        app._playwright = stale_playwright
        app._browser = stale_browser

        browser_context, page = app._new_page("test-agent")

        self.assertIs(fresh_context, browser_context)
        self.assertIs(fresh_page, page)
        stale_context.close.assert_called_once_with()
        stale_browser.close.assert_called_once_with()
        stale_playwright.stop.assert_called_once_with()
        sync_playwright.return_value.start.assert_called_once_with()
        fresh_playwright.chromium.launch.assert_called_once_with(
            headless=True,
            args=app.LAUNCH_ARGS,
        )
        fresh_browser.new_context.assert_called_once_with(user_agent="test-agent")


if __name__ == "__main__":
    unittest.main()
