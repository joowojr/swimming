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
        new_page.return_value = Mock(), Mock(), browser_context, page

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


class RuntimeLifecycleTest(unittest.TestCase):
    @patch.object(app.os, "makedirs")
    def test_브라우저의_쓰기_경로를_tmp로_고정한다(self, makedirs):
        with patch.dict(app.os.environ, {}, clear=True):
            app._prepare_browser_environment()

            self.assertEqual("/tmp", app.os.environ["HOME"])
            self.assertEqual("/tmp/.cache", app.os.environ["XDG_CACHE_HOME"])

        makedirs.assert_any_call("/tmp/.cache/fontconfig", exist_ok=True)
        makedirs.assert_any_call("/tmp/.pki/nssdb", exist_ok=True)

    @patch.object(app.subprocess, "run")
    def test_Chromium_단독_smoke_실행의_종료코드와_stderr를_기록한다(self, run):
        run.return_value = Mock(returncode=0, stdout="<html></html>", stderr="ok")
        playwright = Mock()
        playwright.chromium.executable_path = "/ms-playwright/chromium/chrome"

        with patch.object(app.os.path, "exists", return_value=True), patch.object(
            app.os, "stat", return_value=Mock(st_mode=0o100755)
        ), patch("builtins.print") as print_log:
            app._log_chromium_diagnostics(playwright, "request-id", 1, 0)

        run.assert_called_once()
        output = " ".join(call.args[0] for call in print_log.call_args_list)
        self.assertIn("stage=standalone-smoke", output)
        self.assertIn("returnCode=0", output)
        self.assertIn("stderr='ok'", output)

    @patch.object(app, "sync_playwright")
    @patch.object(app, "_log_chromium_diagnostics")
    def test_요청마다_새_browser_context_page를_생성한다(
        self, log_diagnostics, sync_playwright
    ):
        playwright = Mock()
        browser = Mock()
        browser.is_connected.return_value = True
        context = Mock()
        page = Mock()
        context.new_page.return_value = page
        browser.new_context.return_value = context
        playwright.chromium.launch.return_value = browser
        sync_playwright.return_value.start.return_value = playwright

        actual_playwright, actual_browser, actual_context, actual_page = app._new_page(
            "test-agent", "request-id"
        )

        self.assertIs(playwright, actual_playwright)
        self.assertIs(browser, actual_browser)
        self.assertIs(context, actual_context)
        self.assertIs(page, actual_page)
        playwright.chromium.launch.assert_called_once_with(
            headless=True, args=app.LAUNCH_ARGS
        )
        browser.new_context.assert_called_once_with(user_agent="test-agent")
        log_diagnostics.assert_called_once()

    @patch.object(app, "sync_playwright")
    @patch.object(app, "_log_chromium_diagnostics")
    def test_page_생성_실패시_요청의_runtime을_정리한다(
        self, log_diagnostics, sync_playwright
    ):
        playwright = Mock()
        browser = Mock()
        context = Mock()
        context.new_page.side_effect = app.PlaywrightError("closed")
        browser.new_context.return_value = context
        playwright.chromium.launch.return_value = browser
        sync_playwright.return_value.start.return_value = playwright

        with self.assertRaises(app.PlaywrightError):
            app._new_page(None, "request-id")

        context.close.assert_called_once_with()
        browser.close.assert_called_once_with()
        playwright.stop.assert_called_once_with()

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

if __name__ == "__main__":
    unittest.main()
