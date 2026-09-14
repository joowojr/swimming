const CONTENT_WAIT_MS = 10_000;
const PAGE_SETTLE_WAIT_MS = 2_000;
const TOGGLE_CLICK_WAIT_MS = 500;
const MAX_TOGGLE_CLICKS = 100;

export function isNotionUrl(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return host === 'notion.com' || host.endsWith('.notion.com')
      || host === 'notion.site' || host.endsWith('.notion.site');
  } catch {
    return false;
  }
}

export async function prepare(page) {
  try {
    await page.waitForFunction(() => {
      const content = document.querySelector('.notion-page-content');
      return Boolean(content?.innerText.trim() && content.querySelector('.notion-selectable'));
    }, { timeout: CONTENT_WAIT_MS });
  } catch (error) {
    console.log(JSON.stringify({ stage: 'notion-content-unavailable', message: error.message }));
    return false;
  }

  await page.waitForTimeout(PAGE_SETTLE_WAIT_MS);
  await expandToggles(page);
  return true;
}

async function expandToggles(page) {
  const selector = [
    '.notion-page-content .notion-selectable.notion-toggle-block [role="button"][aria-expanded="false"]',
    '.notion-page-content .notion-header-block [role="button"][aria-expanded="false"]',
    '.notion-page-content .notion-sub_header-block [role="button"][aria-expanded="false"]',
    '.notion-page-content .notion-sub_sub_header-block [role="button"][aria-expanded="false"]',
  ].join(', ');

  let expanded = 0;
  for (let index = 0; index < MAX_TOGGLE_CLICKS; index += 1) {
    const toggles = page.locator(selector);
    const before = await toggles.count();
    if (before === 0) break;

    const toggle = toggles.first();
    if (!await toggle.isVisible()) break;
    try {
      await toggle.scrollIntoViewIfNeeded();
      await toggle.click({ timeout: 2_000, force: true });
      await page.waitForTimeout(TOGGLE_CLICK_WAIT_MS);
      expanded += 1;
    } catch (error) {
      console.log(JSON.stringify({ stage: 'notion-toggle-click-failed', message: error.message }));
      break;
    }
  }

  console.log(JSON.stringify({
    stage: 'notion-toggle-expansion-finished',
    expanded,
    remaining: await page.locator(selector).count(),
  }));
}
