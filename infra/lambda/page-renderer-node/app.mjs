import { chromium as playwrightChromium } from 'playwright-core';
import chromium from '@sparticuz/chromium';
import * as notionRenderer from './notion_renderer.mjs';
import * as naverRenderer from './naver_renderer.mjs';
import * as youtubeRenderer from './youtube_renderer.mjs';

const RENDER_USER_AGENT = 'Mozilla/5.0 (X11; Linux x86_64)';
const DEFAULT_TIMEOUT_MS = 20_000;
const MAX_HTML_BYTES = 4 * 1024 * 1024;

function truncateHtml(html) {
  const bytes = Buffer.byteLength(html, 'utf8');
  if (bytes <= MAX_HTML_BYTES) return { html, truncated: false };
  return { html: Buffer.from(html, 'utf8').subarray(0, MAX_HTML_BYTES).toString('utf8'), truncated: true };
}

export async function handler(event = {}) {
  const url = event.url;
  if (!url) return { error: 'URL_REQUIRED' };

  const timeoutMs = Number(event.timeoutMs) || DEFAULT_TIMEOUT_MS;
  const userAgent = event.userAgent || RENDER_USER_AGENT;

  if (youtubeRenderer.isYouTubeVideoUrl(url)) {
    try {
      const transcriptHtml = await youtubeRenderer.extract(url, {
        language: event.transcriptLanguage || 'ko',
        userAgent,
      });
      if (!transcriptHtml) return { error: 'YOUTUBE_TRANSCRIPT_UNAVAILABLE' };
      const result = truncateHtml(transcriptHtml);
      return { error: null, html: result.html, truncated: result.truncated };
    } catch (error) {
      console.error(JSON.stringify({
        stage: 'youtube-transcript-failed',
        name: error?.name,
        message: error?.message,
      }));
      return { error: 'YOUTUBE_TRANSCRIPT_UNAVAILABLE' };
    }
  }

  let browser;

  console.log(JSON.stringify({
    stage: 'browser-launching',
    url,
    executablePath: await chromium.executablePath(),
    argsCount: chromium.args.length,
  }));

  try {
    browser = await playwrightChromium.launch({
      args: chromium.args,
      executablePath: await chromium.executablePath(),
      headless: true,
    });

    console.log(JSON.stringify({ stage: 'browser-launched', connected: browser.isConnected() }));

    const context = await browser.newContext({ userAgent });
    const page = await context.newPage();
    const response = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: timeoutMs });
    if (response && !response.ok()) {
      return { error: 'HTTP_ERROR', status: response.status() };
    }
    let renderedHtml;
    if (notionRenderer.isNotionUrl(page.url())) {
      if (!await notionRenderer.prepare(page)) return { error: 'NOTION_CONTENT_UNAVAILABLE' };
      renderedHtml = await page.content();
    } else if (naverRenderer.isNaverBlogUrl(url) || naverRenderer.isNaverBlogUrl(page.url())) {
      renderedHtml = await naverRenderer.extract(page, url);
      if (!renderedHtml) return { error: 'NAVER_CONTENT_UNAVAILABLE' };
    } else {
      try {
        await page.waitForLoadState('networkidle', { timeout: timeoutMs });
      } catch {
        console.log(JSON.stringify({ stage: 'networkidle-timeout', url }));
      }
    }
    const result = truncateHtml(renderedHtml || await page.content());

    return {
      error: null,
      html: result.html,
      truncated: result.truncated,
    };
  } catch (error) {
    console.error(JSON.stringify({
      stage: 'render-failed',
      name: error?.name,
      message: error?.message,
    }));
    if (error?.name === 'TimeoutError') return { error: 'TIMEOUT' };
    return { error: 'RENDER_FAILED' };
  } finally {
    if (browser) {
      await browser.close().catch((error) => {
        console.error(JSON.stringify({ stage: 'browser-close-failed', message: error.message }));
      });
    }
  }
}
