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


class PageDiagnosticsTest(unittest.TestCase):
    def test_page_진단_event를_등록한다(self):
        page = Mock()

        app._attach_page_diagnostics(page, "request-id")

        self.assertEqual(
            ["console", "pageerror", "requestfailed", "response"],
            [call.args[0] for call in page.on.call_args_list],
        )

    def test_동일한_page_진단_event는_한번만_기록한다(self):
        page = Mock()
        response = Mock(
            status=429,
            url="https://workspace.notion.site/script.js",
        )
        response.request.resource_type = "script"
        app._attach_page_diagnostics(page, "request-id")
        response_handler = next(
            call.args[1]
            for call in page.on.call_args_list
            if call.args[0] == "response"
        )

        with patch.object(app, "_response_received") as response_received:
            response_handler(response)
            response_handler(response)

        response_received.assert_called_once_with(response, "request-id")

    def test_http_오류는_query_없이_origin만_기록한다(self):
        response = Mock(
            status=429,
            url="https://api.notion.com/v1/load?token=secret",
        )
        response.request.resource_type = "xhr"

        with patch("builtins.print") as print_log:
            app._response_received(response, "request-id")

        output = print_log.call_args.args[0]
        self.assertIn("status=429", output)
        self.assertIn("origin=https://api.notion.com", output)
        self.assertNotIn("secret", output)

    def test_정상_http_응답은_기록하지_않는다(self):
        response = Mock(status=200)

        with patch("builtins.print") as print_log:
            app._response_received(response, "request-id")

        print_log.assert_not_called()


class RuntimeRecoveryTest(unittest.TestCase):
    def tearDown(self):
        app._browser = None
        app._playwright = None

    @patch.object(app.os, "makedirs")
    def test_브라우저의_쓰기_경로를_tmp로_고정한다(self, makedirs):
        with patch.dict(app.os.environ, {}, clear=True):
            app._prepare_browser_environment()

            self.assertEqual("/tmp", app.os.environ["HOME"])
            self.assertEqual("/tmp/.cache", app.os.environ["XDG_CACHE_HOME"])

        makedirs.assert_any_call("/tmp/.cache/fontconfig", exist_ok=True)
        makedirs.assert_any_call("/tmp/.pki/nssdb", exist_ok=True)

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

        browser_context, page = app._new_page("test-agent", "request-id")

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
        fresh_browser.on.assert_called_once()
        fresh_browser.new_context.assert_called_once_with(user_agent="test-agent")

    @patch.object(app, "sync_playwright")
    def test_chromium_launch_실패_후_새_runtime으로_재시도한다(self, sync_playwright):
        failed_playwright = Mock()
        failed_playwright.chromium.launch.side_effect = app.PlaywrightError(
            "launch closed"
        )
        failed_manager = Mock()
        failed_manager.start.return_value = failed_playwright

        page = Mock()
        browser_context = Mock()
        browser_context.new_page.return_value = page
        browser = Mock()
        browser.is_connected.return_value = True
        browser.new_context.return_value = browser_context
        fresh_playwright = Mock()
        fresh_playwright.chromium.launch.return_value = browser
        fresh_manager = Mock()
        fresh_manager.start.return_value = fresh_playwright
        sync_playwright.side_effect = [failed_manager, fresh_manager]

        actual_context, actual_page = app._new_page(None, "request-id")

        self.assertIs(browser_context, actual_context)
        self.assertIs(page, actual_page)
        failed_playwright.stop.assert_called_once_with()
        fresh_playwright.chromium.launch.assert_called_once_with(
            headless=True,
            args=app.LAUNCH_ARGS,
        )

    @patch.object(app, "_log_runtime_environment")
    @patch.object(app, "_log_memory")
    @patch.object(app, "_new_page")
    def test_page_생성이_두번_종료되면_launch_failed를_반환한다(
        self,
        new_page,
        log_memory,
        log_runtime_environment,
    ):
        new_page.side_effect = app.PlaywrightError(
            "BrowserContext.new_page: Target page, context or browser has been closed"
        )
        context = Mock(aws_request_id="request-id")

        result = app.handler({"url": "https://example.com"}, context)

        self.assertEqual({"error": "LAUNCH_FAILED"}, result)
        new_page.assert_called_once_with(None, "request-id")
        log_memory.assert_any_call("launch-failed", context)
        log_runtime_environment.assert_called_once_with(context)

    @patch.object(app, "sync_playwright")
    def test_new_page가_두번_종료되면_매번_runtime을_폐기한다(
        self,
        sync_playwright,
    ):
        playwrights = []
        managers = []
        browsers = []
        contexts = []
        for _ in range(2):
            browser_context = Mock()
            browser_context.new_page.side_effect = app.PlaywrightError(
                "BrowserContext.new_page: Target page, context or browser has been closed"
            )
            browser = Mock()
            browser.is_connected.return_value = True
            browser.new_context.return_value = browser_context
            playwright = Mock()
            playwright.chromium.launch.return_value = browser
            manager = Mock()
            manager.start.return_value = playwright

            contexts.append(browser_context)
            browsers.append(browser)
            playwrights.append(playwright)
            managers.append(manager)

        sync_playwright.side_effect = managers

        with self.assertRaises(app.PlaywrightError):
            app._new_page(None, "request-id")

        for browser_context in contexts:
            browser_context.close.assert_called_once_with()
        for browser in browsers:
            browser.close.assert_called_once_with()
        for playwright in playwrights:
            playwright.stop.assert_called_once_with()
        self.assertIsNone(app._browser)
        self.assertIsNone(app._playwright)


if __name__ == "__main__":
    unittest.main()
