import { chromium as playwrightChromium } from 'playwright-core';
import chromium from '@sparticuz/chromium';
import * as notionRenderer from './notion_renderer.mjs';
import * as naverRenderer from './naver_renderer.mjs';
import * as youtubeRenderer from './youtube_renderer.mjs';

const RENDER_USER_AGENT = 'Mozilla/5.0 (X11; Linux x86_64)';
const DEFAULT_TIMEOUT_MS = 20_000;
const GENERIC_LOAD_TIMEOUT_MS = 5_000;
const CLEANUP_TIMEOUT_MS = 1_000;
const MAX_HTML_BYTES = 4 * 1024 * 1024;

class RenderTimeoutError extends Error {
  constructor(stage) {
    super(`${stage} timed out`);
    this.name = 'TimeoutError';
  }
}

function remainingMs(deadline) {
  return Math.max(0, deadline - Date.now());
}

async function withinDeadline(operation, deadline, stage) {
  const timeoutMs = remainingMs(deadline);
  if (timeoutMs === 0) throw new RenderTimeoutError(stage);

  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(operation),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new RenderTimeoutError(stage)), timeoutMs);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

async function closeWithin(resource, resourceName, timeoutMs) {
  if (!resource) return;

  console.log(JSON.stringify({ stage: `${resourceName}-close-started` }));
  try {
    await withinDeadline(
      () => resource.close(),
      Date.now() + timeoutMs,
      `${resourceName}-close`,
    );
    console.log(JSON.stringify({ stage: `${resourceName}-close-finished` }));
  } catch (error) {
    console.error(JSON.stringify({
      stage: `${resourceName}-close-failed`,
      name: error?.name,
      message: error?.message,
    }));
  }
}

function truncateHtml(html) {
  const bytes = Buffer.byteLength(html, 'utf8');
  if (bytes <= MAX_HTML_BYTES) return { html, truncated: false };
  return { html: Buffer.from(html, 'utf8').subarray(0, MAX_HTML_BYTES).toString('utf8'), truncated: true };
}

export function createHandler({
  playwright = playwrightChromium,
  chromiumProvider = chromium,
  notion = notionRenderer,
  naver = naverRenderer,
  youtube = youtubeRenderer,
  genericLoadTimeoutMs = GENERIC_LOAD_TIMEOUT_MS,
  cleanupTimeoutMs = CLEANUP_TIMEOUT_MS,
} = {}) {
  return async function renderHandler(event = {}) {
    const url = event.url;
    if (!url) return { error: 'URL_REQUIRED' };

    const timeoutMs = Number(event.timeoutMs) || DEFAULT_TIMEOUT_MS;
    const deadline = Date.now() + timeoutMs;
    const userAgent = event.userAgent || RENDER_USER_AGENT;

    if (youtube.isYouTubeVideoUrl(url)) {
      try {
        const transcriptHtml = await withinDeadline(
          () => youtube.extract(url, {
            language: event.transcriptLanguage || 'ko',
            userAgent,
          }),
          deadline,
          'youtube-transcript',
        );
        if (!transcriptHtml) return { error: 'YOUTUBE_TRANSCRIPT_UNAVAILABLE' };
        const result = truncateHtml(transcriptHtml);
        return { error: null, html: result.html, truncated: result.truncated };
      } catch (error) {
        console.error(JSON.stringify({
          stage: 'youtube-transcript-failed',
          name: error?.name,
          message: error?.message,
        }));
        return { error: error?.name === 'TimeoutError'
          ? 'TIMEOUT'
          : 'YOUTUBE_TRANSCRIPT_UNAVAILABLE' };
      }
    }

    let browser;
    let context;

    try {
      const executablePath = await withinDeadline(
        () => chromiumProvider.executablePath(),
        deadline,
        'chromium-executable',
      );

      console.log(JSON.stringify({
        stage: 'browser-launching',
        url,
        executablePath,
        argsCount: chromiumProvider.args.length,
      }));

      browser = await withinDeadline(
        () => playwright.launch({
          args: chromiumProvider.args,
          executablePath,
          headless: true,
          timeout: remainingMs(deadline),
        }),
        deadline,
        'browser-launch',
      );

      console.log(JSON.stringify({ stage: 'browser-launched', connected: browser.isConnected() }));

      context = await withinDeadline(
        () => browser.newContext({ userAgent }),
        deadline,
        'context-create',
      );
      const page = await withinDeadline(() => context.newPage(), deadline, 'page-create');
      const response = await withinDeadline(
        () => page.goto(url, {
          waitUntil: 'domcontentloaded',
          timeout: remainingMs(deadline),
        }),
        deadline,
        'navigation',
      );
      if (response && !response.ok()) {
        return { error: 'HTTP_ERROR', status: response.status() };
      }
      let renderedHtml;
      if (notion.isNotionUrl(page.url())) {
        const prepared = await withinDeadline(
          () => notion.prepare(page),
          deadline,
          'notion-prepare',
        );
        if (!prepared) return { error: 'NOTION_CONTENT_UNAVAILABLE' };
      } else if (naver.isNaverBlogUrl(url) || naver.isNaverBlogUrl(page.url())) {
        renderedHtml = await withinDeadline(
          () => naver.extract(page, url),
          deadline,
          'naver-extract',
        );
        if (!renderedHtml) return { error: 'NAVER_CONTENT_UNAVAILABLE' };
      } else {
        const loadTimeoutMs = Math.min(genericLoadTimeoutMs, remainingMs(deadline));
        if (loadTimeoutMs > 0) {
          try {
            await withinDeadline(
              () => page.waitForLoadState('load', { timeout: loadTimeoutMs }),
              Date.now() + loadTimeoutMs,
              'load-wait',
            );
          } catch (error) {
            console.log(JSON.stringify({
              stage: 'load-wait-finished',
              url,
              result: error?.name === 'TimeoutError' ? 'timeout' : 'failed',
            }));
          }
        }
      }

      if (!renderedHtml) {
        console.log(JSON.stringify({ stage: 'content-started', url }));
        renderedHtml = await withinDeadline(() => page.content(), deadline, 'content');
        console.log(JSON.stringify({ stage: 'content-finished', url }));
      }
      const result = truncateHtml(renderedHtml);

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
      await closeWithin(context, 'context', cleanupTimeoutMs);
      await closeWithin(browser, 'browser', cleanupTimeoutMs);
    }
  };
}

export const handler = createHandler();
