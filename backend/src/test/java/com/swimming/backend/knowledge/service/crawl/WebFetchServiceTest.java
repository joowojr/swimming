package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.config.WebFetchProperties;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebFetchServiceTest {

    private static final String URL = "https://example.com/article";

    private final WebFetchService service = new WebFetchService(
            properties(),
            new HtmlToMarkdownConverter(),
            Optional.empty()
    );

    private static WebFetchProperties properties() {
        return new WebFetchProperties(
                4,
                Duration.ofSeconds(15),
                4 * 1024 * 1024,
                50_000,
                300,
                "SwimmingBot/0.1",
                new WebFetchProperties.Render(false, null, Duration.ofSeconds(20), 1000)
        );
    }

    private Document parse(String head) {
        return Jsoup.parse("<html><head>" + head + "</head><body><p>본문</p></body></html>", URL);
    }

    @Test
    @DisplayName("문서가 밝힌 canonical 주소를 우선한다")
    void prefersDeclaredCanonical() {
        Document document = parse("<link rel=\"canonical\" href=\"https://example.com/real\">");

        assertThat(service.canonicalUrl(document, "https://example.com/article?utm_source=x"))
                .isEqualTo("https://example.com/real");
    }

    @Test
    @DisplayName("canonical이 없으면 og:url을 쓴다")
    void fallsBackToOgUrl() {
        Document document = parse("<meta property=\"og:url\" content=\"https://example.com/og\">");

        assertThat(service.canonicalUrl(document, URL)).isEqualTo("https://example.com/og");
    }

    @Test
    @DisplayName("둘 다 없으면 추적 파라미터와 fragment를 걷어낸다")
    void stripsTrackingParameters() {
        Document document = parse("");

        String canonical = service.canonicalUrl(
                document,
                "https://Example.com/a?utm_source=news&id=7&fbclid=abc&utm_medium=mail#section"
        );

        assertThat(canonical).isEqualTo("https://example.com/a?id=7");
    }

    @Test
    @DisplayName("걷어내고 남는 파라미터가 없으면 쿼리 자체를 없앤다")
    void dropsEmptyQuery() {
        assertThat(service.canonicalUrl(parse(""), "https://example.com/a?utm_source=news"))
                .isEqualTo("https://example.com/a");
    }

    @Test
    @DisplayName("상한 안쪽이면 그대로 둔다")
    void keepsShortMarkdown() {
        var truncation = service.truncate("짧은 문서");

        assertThat(truncation.truncated()).isFalse();
        assertThat(truncation.text()).isEqualTo("짧은 문서");
    }

    @Test
    @DisplayName("상한을 넘으면 줄 경계에서 끊고 잘렸다고 표시한다")
    void truncatesAtLineBreak() {
        String markdown = ("가".repeat(80) + "\n").repeat(1000);

        var truncation = service.truncate(markdown);

        assertThat(truncation.truncated()).isTrue();
        assertThat(truncation.text().length()).isLessThanOrEqualTo(50_000);
        assertThat(truncation.text().length()).isGreaterThan(40_000);
        assertThat(truncation.text().lines())
                .as("줄 중간에서 끊기지 않는다")
                .allMatch(line -> line.length() == 80);
    }

    @Test
    @DisplayName("http/https가 아닌 주소는 요청하지 않는다")
    void rejectsNonHttpScheme() {
        for (String url : List.of("file:///etc/passwd", "ftp://example.com/a", "그냥문자열")) {
            SourceFetchResult result = service.fetch(url);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.INVALID_URL);
            assertThat(result.isRetryable()).isFalse();
        }
    }

    @Test
    @DisplayName("루프백·내부망 주소는 요청하지 않는다")
    void rejectsInternalAddresses() {
        for (String url : List.of(
                "http://127.0.0.1:8080/admin",
                "http://localhost/actuator",
                "http://169.254.169.254/latest/meta-data/",
                "http://192.168.0.1/")) {

            SourceFetchResult result = service.fetch(url);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.failure())
                    .as(url)
                    .isEqualTo(SourceFetchResult.Failure.BLOCKED_ADDRESS);
            assertThat(result.isRetryable()).as(url).isFalse();
        }
    }

    @Test
    @DisplayName("한 URL이 실패해도 나머지 결과를 순서대로 돌려준다")
    void keepsOrderAndIsolatesFailures() {
        List<SourceFetchResult> results = service.fetchAll(List.of(
                "http://127.0.0.1/a",
                "ftp://example.com/b",
                "http://127.0.0.1/a"
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).requestedUrl()).isEqualTo("http://127.0.0.1/a");
        assertThat(results.get(0).failure()).isEqualTo(SourceFetchResult.Failure.BLOCKED_ADDRESS);
        assertThat(results.get(1).requestedUrl()).isEqualTo("ftp://example.com/b");
        assertThat(results.get(1).failure()).isEqualTo(SourceFetchResult.Failure.INVALID_URL);
    }

    @Test
    @DisplayName("링크와 서식을 뺀 실제 문장 길이로 본문 유무를 잰다")
    void measuresMeaningfulTextOnly() {
        String loginWall = """
                ## Log in or sign up for X

                [Continue with phone](https://x.com/i/jf/onboarding/web?mode=signup&redirect_after_login=%2Fa)
                [Terms](https://x.com/tos)·[Privacy](https://x.com/privacy)·[Cookies](https://support.x.com/articles/20170514)
                """;

        int meaningful = HtmlToMarkdownConverter.meaningfulTextOf(loginWall).length();

        assertThat(loginWall.length()).isGreaterThan(200);
        assertThat(meaningful).isLessThan(80);
    }

    @Test
    @DisplayName("본문이 사이트 메뉴뿐이면 og:description을 본문으로 쓰고 og:title을 제목으로 쓴다")
    void fallsBackToMetadataWhenBodyIsChrome() {
        Document document = Jsoup.parse("""
                <html><head>
                  <title>어떤이 on X: "게시물 본문이 제목에 통째로 들어간 경우"</title>
                  <meta property="og:title" content="어떤이 (@someone) on X">
                  <meta property="og:description" content="실제 게시물 본문입니다. 링크 없이 읽을 수 있는 문장이 들어 있습니다.">
                </head><body>
                  <main><h2>Log in or sign up</h2>
                    <a href="/login">Continue with phone</a>
                    <a href="/tos">Terms</a><a href="/privacy">Privacy</a>
                  </main>
                </body></html>
                """, URL);

        var result = service.fetchFrom(document);

        assertThat(result.document().markdown()).startsWith("실제 게시물 본문입니다");
        assertThat(result.document().title()).isEqualTo("어떤이 (@someone) on X");
    }

    @Test
    @DisplayName("본문이 충분하면 메타데이터로 바꾸지 않는다")
    void keepsBodyWhenItHasRealContent() {
        Document document = Jsoup.parse("""
                <html><head>
                  <title>진짜 문서</title>
                  <meta property="og:description" content="짧은 사이트 소개 문구">
                </head><body>
                  <main><h1>진짜 문서</h1><p>%s</p></main>
                </body></html>
                """.formatted("실제 본문 내용이 충분히 길게 이어집니다. ".repeat(30)), URL);

        var result = service.fetchFrom(document);

        assertThat(result.document().markdown()).contains("실제 본문 내용이 충분히");
        assertThat(result.document().markdown()).doesNotContain("짧은 사이트 소개 문구");
    }

    @Test
    @DisplayName("일반 HTTP 요청이 403이면 렌더러의 HTML로 다시 수집한다")
    void recoversForbiddenResponseWithRenderer() throws Exception {
        LambdaPageRendererClient renderer = mock(LambdaPageRendererClient.class);
        when(renderer.render(URL)).thenReturn(Optional.of("""
                <html><head><title>렌더링된 문서</title></head><body><main>
                  <h1>렌더링된 문서</h1>
                  <p>브라우저로 가져온 실제 본문입니다. 수집 결과가 유효한 문서로 남습니다.</p>
                </main></body></html>
                """));

        WebFetchService serviceWithRenderer = new WebFetchService(
                properties(), new HtmlToMarkdownConverter(), Optional.of(renderer)
        );

        try (MockedStatic<Jsoup> jsoup = forbiddenResponse(403)) {
            SourceFetchResult result = serviceWithRenderer.fetch(URL);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.document().title()).isEqualTo("렌더링된 문서");
            assertThat(result.document().markdown()).contains("브라우저로 가져온 실제 본문");
            verify(renderer).render(URL);
        }
    }

    @Test
    @DisplayName("403 렌더링도 실패하면 최초 HTTP 오류를 유지한다")
    void keepsForbiddenFailureWhenRendererFails() throws Exception {
        LambdaPageRendererClient renderer = mock(LambdaPageRendererClient.class);
        when(renderer.render(URL)).thenReturn(Optional.empty());

        WebFetchService serviceWithRenderer = new WebFetchService(
                properties(), new HtmlToMarkdownConverter(), Optional.of(renderer)
        );

        try (MockedStatic<Jsoup> jsoup = forbiddenResponse(403)) {
            SourceFetchResult result = serviceWithRenderer.fetch(URL);

            assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.HTTP_ERROR);
            assertThat(result.failureDetail()).isEqualTo("403");
            assertThat(result.isRetryable())
                    .as("Lambda 렌더링 실패 뒤에도 4xx 수집 오류는 재시도하지 않는다")
                    .isFalse();
            verify(renderer).render(URL);
        }
    }

    @Test
    @DisplayName("403 이외의 HTTP 오류에는 렌더러를 호출하지 않는다")
    void doesNotRenderOtherHttpErrors() throws Exception {
        LambdaPageRendererClient renderer = mock(LambdaPageRendererClient.class);
        WebFetchService serviceWithRenderer = new WebFetchService(
                properties(), new HtmlToMarkdownConverter(), Optional.of(renderer)
        );

        try (MockedStatic<Jsoup> jsoup = forbiddenResponse(404)) {
            SourceFetchResult result = serviceWithRenderer.fetch(URL);

            assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.HTTP_ERROR);
            assertThat(result.failureDetail()).isEqualTo("404");
            assertThat(result.isRetryable()).isFalse();
            verify(renderer, never()).render(URL);
        }
    }

    @Test
    @DisplayName("서버 HTTP 오류는 다시 수집할 수 있다")
    void allowsRetryForServerHttpErrors() throws Exception {
        try (MockedStatic<Jsoup> jsoup = forbiddenResponse(503)) {
            SourceFetchResult result = service.fetch(URL);

            assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.HTTP_ERROR);
            assertThat(result.failureDetail()).isEqualTo("503");
            assertThat(result.isRetryable()).isTrue();
        }
    }

    private MockedStatic<Jsoup> forbiddenResponse(int statusCode) throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.userAgent(org.mockito.ArgumentMatchers.anyString())).thenReturn(connection);
        when(connection.timeout(anyInt())).thenReturn(connection);
        when(connection.maxBodySize(anyInt())).thenReturn(connection);
        when(connection.followRedirects(true)).thenReturn(connection);
        when(connection.ignoreContentType(false)).thenReturn(connection);
        when(connection.execute()).thenThrow(new HttpStatusException(
                "HTTP error fetching URL", statusCode, URL
        ));

        MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class, CALLS_REAL_METHODS);
        jsoup.when(() -> Jsoup.connect(URL)).thenReturn(connection);
        return jsoup;
    }
}
