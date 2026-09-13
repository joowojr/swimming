import unittest
from unittest.mock import Mock

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError

import notion_renderer


class NotionUrlTest(unittest.TestCase):
    def test_notion_com과_notion_site_하위_도메인을_인식한다(self):
        self.assertTrue(
            notion_renderer.is_notion_url("https://app.notion.com/p/example")
        )
        self.assertTrue(
            notion_renderer.is_notion_url("https://workspace.notion.site/example")
        )

    def test_유사한_외부_도메인은_notion으로_인식하지_않는다(self):
        self.assertFalse(
            notion_renderer.is_notion_url("https://notion.site.example.com/page")
        )
        self.assertFalse(
            notion_renderer.is_notion_url("https://example-notion.site/page")
        )


class NotionContentTest(unittest.TestCase):
    def test_토글_선택자는_notion_본문의_접이식_블록으로_제한한다(self):
        self.assertNotIn(
            '.notion-focusable[aria-expanded="false"]',
            notion_renderer.CLOSED_TOGGLE_SELECTOR,
        )
        self.assertIn(
            ".notion-page-content .notion-selectable.notion-toggle-block",
            notion_renderer.CLOSED_TOGGLE_SELECTOR,
        )

    def test_실제_notion_본문이_나타나면_성공한다(self):
        page = Mock()

        self.assertTrue(notion_renderer.wait_for_content(page))
        page.wait_for_function.assert_called_once()

    def test_본문_대기가_timeout되면_실패한다(self):
        page = Mock()
        page.url = "https://workspace.notion.site/example"
        page.wait_for_function.side_effect = PlaywrightTimeoutError("timeout")

        self.assertFalse(notion_renderer.wait_for_content(page))


if __name__ == "__main__":
    unittest.main()
