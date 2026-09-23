import assert from 'node:assert/strict';
import test from 'node:test';

import { createHandler } from './app.mjs';

function dependencies({
  pageOverrides = {},
  contextOverrides = {},
  browserOverrides = {},
  cleanupTimeoutMs = 10,
} = {}) {
  const loadStates = [];
  const page = {
    goto: async () => ({ ok: () => true }),
    url: () => 'https://example.com/article',
    waitForLoadState: async (state) => loadStates.push(state),
    content: async () => '<html><body>article</body></html>',
    ...pageOverrides,
  };
  const context = {
    newPage: async () => page,
    close: async () => {},
    ...contextOverrides,
  };
  const browser = {
    isConnected: () => true,
    newContext: async () => context,
    close: async () => {},
    ...browserOverrides,
  };

  return {
    loadStates,
    handler: createHandler({
      playwright: { launch: async () => browser },
      chromiumProvider: { args: [], executablePath: async () => '/tmp/chromium' },
      notion: { isNotionUrl: () => false, prepare: async () => true },
      naver: { isNaverBlogUrl: () => false, extract: async () => null },
      youtube: { isYouTubeVideoUrl: () => false, extract: async () => null },
      genericLoadTimeoutMs: 10,
      cleanupTimeoutMs,
    }),
  };
}

test('범용 페이지는 networkidle 대신 load까지만 기다린다', async () => {
  const { handler, loadStates } = dependencies();

  const result = await handler({ url: 'https://example.com/article', timeoutMs: 100 });

  assert.equal(result.error, null);
  assert.deepEqual(loadStates, ['load']);
});

test('HTML 추출이 멈추면 전체 제한시간 안에 TIMEOUT을 반환한다', async () => {
  const { handler } = dependencies({
    pageOverrides: { content: () => new Promise(() => {}) },
  });

  const startedAt = Date.now();
  const result = await handler({ url: 'https://example.com/article', timeoutMs: 20 });

  assert.equal(result.error, 'TIMEOUT');
  assert.ok(Date.now() - startedAt < 200);
});

test('브라우저 정리가 멈춰도 성공 응답을 반환한다', async () => {
  const never = () => new Promise(() => {});
  const { handler } = dependencies({
    contextOverrides: { close: never },
    browserOverrides: { close: never },
  });

  const startedAt = Date.now();
  const result = await handler({ url: 'https://example.com/article', timeoutMs: 100 });

  assert.equal(result.error, null);
  assert.ok(Date.now() - startedAt < 200);
});
