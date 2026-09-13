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


if __name__ == "__main__":
    unittest.main()
