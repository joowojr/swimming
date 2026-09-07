package com.swimming.backend.knowledge.service.crawl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.swimming.backend.knowledge.config.KnowledgeFetchProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JavaScript로 본문을 그리는 페이지를 헤드리스 브라우저로 받아온다.
 *
 * <p>모든 페이지에 쓰지 않는다. 브라우저는 요청당 비용이 크고, 측정한 13개 사이트 중
 * 실제로 필요한 곳은 둘뿐이었다. {@link WebFetchService}가 jsoup 결과가 비어 있을 때만
 * 부른다.
 *
 * <p>Playwright 객체는 만든 스레드에서만 쓸 수 있다. 그래서 전용 스레드 하나를 두고
 * 그 위에서만 브라우저를 만들고 쓴다. 폴백이 드물어 직렬화해도 문제가 없고, 브라우저가
 * 하나로 고정되어 메모리 상한이 분명해진다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "app.knowledge.fetch.render.enabled",
        havingValue = "true"
)
public class RenderedPageFetcher {

    private final KnowledgeFetchProperties properties;

    /** Playwright는 스레드 안전하지 않다. 브라우저를 만든 이 스레드에서만 접근한다. */
    private final ExecutorService browserThread =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "knowledge-render");
                thread.setDaemon(true);
                return thread;
            });

    private Playwright playwright;
    private Browser browser;

    /** 브라우저를 못 띄우는 환경에서 매 요청 실패를 반복하지 않는다. */
    private boolean unavailable;

    public RenderedPageFetcher(KnowledgeFetchProperties properties) {
        this.properties = properties;
    }

    /**
     * @return 렌더링된 HTML. 브라우저를 쓸 수 없거나 실패하면 비어 있다.
     */
    public Optional<String> render(String url) {
        try {
            return browserThread.submit(() -> renderOnBrowserThread(url)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            log.info("[source-render] failed url={} reason={}", url, exception.toString());
            return Optional.empty();
        }
    }

    private Optional<String> renderOnBrowserThread(String url) {
        if (!ensureBrowser()) {
            return Optional.empty();
        }

        Page page = browser.newPage();
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(properties.render().timeout().toMillis()));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            return Optional.of(page.content());
        } finally {
            page.close();
        }
    }

    private boolean ensureBrowser() {
        if (unavailable) {
            return false;
        }
        if (browser != null && browser.isConnected()) {
            return true;
        }

        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));

            log.info("[source-render] headless browser started");
            return true;

        } catch (RuntimeException exception) {
            unavailable = true;
            log.warn(
                    "[source-render] 브라우저를 띄우지 못해 렌더링 폴백을 끕니다. "
                            + "'mvn/gradle playwright install chromium'이 필요합니다. reason={}",
                    exception.toString()
            );
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        browserThread.submit(() -> {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        });
        browserThread.shutdown();
    }
}
