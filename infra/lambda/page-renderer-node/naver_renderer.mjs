const FRAME_WAIT_MS = 10_000;
const FRAME_POLL_MS = 250;

export function isNaverBlogUrl(url) {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return host === 'blog.naver.com' || host.endsWith('.blog.naver.com');
  } catch {
    return false;
  }
}

export async function extract(page, blogUrl) {
  const frame = await findPostFrame(page, blogUrl);
  if (!frame) return null;

  const title = await firstText(frame, [
    '.se-title-text',
    '.pcol1',
    'h3.se_textarea',
  ]) || '제목 없음';
  const date = await firstText(frame, [
    '.se_publishDate',
    '.blog2_series',
    '.se-date',
  ]) || '';
  const content = await firstHtml(frame, [
    '.se-main-container',
    '.post-view',
    '.se_component_wrap',
    '.se-text-paragraph',
  ]);

  if (!content) {
    console.log(JSON.stringify({
      stage: 'naver-selectors-empty',
      frameUrl: frame.url(),
      bodyTextLength: (await frame.locator('body').innerText().catch(() => '')).trim().length,
      selectorCounts: await selectorCounts(frame),
    }));
    return null;
  }
  console.log(JSON.stringify({ stage: 'naver-content-extracted', titleLength: title.length, contentLength: content.length }));

  return `<article><h1>${escapeHtml(title)}</h1>${date ? `<time>${escapeHtml(date)}</time>` : ''}<div class="article-content">${content}</div></article>`;
}

async function findPostFrame(page, blogUrl) {
  const deadline = Date.now() + FRAME_WAIT_MS;
  while (Date.now() < deadline) {
    const frames = page.frames().filter((frame) => frame !== page.mainFrame());
    const candidate = frames.find((frame) => {
      const frameUrl = frame.url();
      return /PostView|PostList/i.test(frameUrl);
    });
    if (candidate) {
      console.log(JSON.stringify({ stage: 'naver-frame-found', frameUrl: candidate.url() }));
      return candidate;
    }
    await page.waitForTimeout(FRAME_POLL_MS);
  }
  console.log(JSON.stringify({
    stage: 'naver-frame-unavailable',
    frameUrls: page.frames().map((frame) => frame.url()),
    blogHost: new URL(blogUrl).hostname,
  }));
  return null;
}

async function firstText(frame, selectors) {
  for (const selector of selectors) {
    const locator = frame.locator(selector).first();
    if (await locator.count() && await locator.isVisible().catch(() => false)) {
      const value = (await locator.innerText().catch(() => '')).trim();
      if (value) return value;
    }
  }
  return '';
}

async function firstHtml(frame, selectors) {
  for (const selector of selectors) {
    const locator = frame.locator(selector);
    if (await locator.count()) {
      const html = await locator.evaluateAll((elements) => elements
        .map((element) => element.outerHTML)
        .join('\n\n')).catch(() => '');
      if (html.trim()) return html;
    }
  }
  return '';
}

async function selectorCounts(frame) {
  const selectors = [
    '.se-title-text', '.pcol1', 'h3.se_textarea',
    '.se_publishDate', '.blog2_series', '.se-date',
    '.se-main-container', '.post-view', '.se_component_wrap', '.se-text-paragraph',
  ];
  return Object.fromEntries(await Promise.all(selectors.map(async (selector) => [
    selector,
    await frame.locator(selector).count().catch(() => 0),
  ])));
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[character]));
}
