package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.config.WebFetchProperties;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceFetchDispatcherTest {

    private static final String YOUTUBE_URL = "https://youtu.be/dQw4w9WgXcQ";
    private static final String ARTICLE_URL = "https://tech.kakao.com/posts/777";

    private final WebFetchService webFetchService = mock(WebFetchService.class);

    @Test
    @DisplayName("맡겠다고 나선 수집기가 있으면 그쪽으로 보낸다")
    void 맡는_수집기가_있으면_그쪽으로_보낸다() {
        RecordingFetcher youtube = new RecordingFetcher(uri -> "youtu.be".equals(uri.getHost()));
        SourceFetchDispatcher dispatcher = dispatcher(List.of(youtube));

        SourceFetchResult result = dispatcher.fetch(YOUTUBE_URL);

        assertThat(youtube.handled).containsExactly(YOUTUBE_URL);
        assertThat(result.isSuccess()).isTrue();
        verify(webFetchService, never()).fetch(anyString());
    }

    @Test
    @DisplayName("아무도 맡지 않는 링크는 일반 웹 수집으로 보낸다")
    void 맡는_수집기가_없으면_웹_수집으로_보낸다() {
        RecordingFetcher youtube = new RecordingFetcher(uri -> "youtu.be".equals(uri.getHost()));
        SourceFetchDispatcher dispatcher = dispatcher(List.of(youtube));
        when(webFetchService.fetch(ARTICLE_URL)).thenReturn(success(ARTICLE_URL));

        dispatcher.fetch(ARTICLE_URL);

        assertThat(youtube.handled).isEmpty();
        verify(webFetchService).fetch(ARTICLE_URL);
    }

    @Test
    @DisplayName("전용 수집기가 하나도 없으면 지금처럼 전부 웹 수집으로 간다")
    void 전용_수집기가_없으면_전부_웹_수집이다() {
        SourceFetchDispatcher dispatcher = dispatcher(List.of());
        when(webFetchService.fetch(YOUTUBE_URL)).thenReturn(success(YOUTUBE_URL));

        dispatcher.fetch(YOUTUBE_URL);

        verify(webFetchService).fetch(YOUTUBE_URL);
    }

    @Test
    @DisplayName("먼저 맡겠다고 한 수집기 하나만 부른다")
    void 먼저_맡은_수집기_하나만_부른다() {
        RecordingFetcher first = new RecordingFetcher(uri -> true);
        RecordingFetcher second = new RecordingFetcher(uri -> true);
        SourceFetchDispatcher dispatcher = dispatcher(List.of(first, second));

        dispatcher.fetch(YOUTUBE_URL);

        assertThat(first.handled).containsExactly(YOUTUBE_URL);
        assertThat(second.handled).isEmpty();
    }

    @Test
    @DisplayName("파싱하지 못하는 주소도 웹 수집이 판정하게 넘긴다")
    void 파싱하지_못하는_주소는_웹_수집이_판정한다() {
        SourceFetchDispatcher dispatcher = dispatcher(List.of());
        String broken = "h ttp://%%%";
        when(webFetchService.fetch(broken)).thenReturn(
                SourceFetchResult.failure(broken, SourceFetchResult.Failure.INVALID_URL, null)
        );

        SourceFetchResult result = dispatcher.fetch(broken);

        assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.INVALID_URL);
    }

    @Test
    @DisplayName("여러 링크를 입력 순서대로 돌려주고 같은 링크는 한 번만 요청한다")
    void 입력_순서를_지키고_중복은_한_번만_요청한다() {
        RecordingFetcher youtube = new RecordingFetcher(uri -> "youtu.be".equals(uri.getHost()));
        SourceFetchDispatcher dispatcher = dispatcher(List.of(youtube));
        when(webFetchService.fetch(ARTICLE_URL)).thenReturn(success(ARTICLE_URL));

        List<SourceFetchResult> results = dispatcher.fetchAll(
                List.of(ARTICLE_URL, YOUTUBE_URL, ARTICLE_URL)
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).requestedUrl()).isEqualTo(ARTICLE_URL);
        assertThat(results.get(1).requestedUrl()).isEqualTo(YOUTUBE_URL);
        assertThat(youtube.handled).containsExactly(YOUTUBE_URL);
        verify(webFetchService).fetch(ARTICLE_URL);
    }

    @Test
    @DisplayName("한 URL이 실패해도 나머지 결과를 순서대로 돌려준다")
    void 한_URL이_실패해도_나머지를_순서대로_돌려준다() {
        SourceFetchDispatcher dispatcher = new SourceFetchDispatcher(
                new WebFetchService(properties(), new HtmlToMarkdownConverter(), Optional.empty()),
                List.of(),
                properties()
        );

        List<SourceFetchResult> results = dispatcher.fetchAll(List.of(
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

    private SourceFetchDispatcher dispatcher(List<SourceFetcher> fetchers) {
        return new SourceFetchDispatcher(webFetchService, fetchers, properties());
    }

    private static SourceFetchResult success(String url) {
        return SourceFetchResult.success(url, new FetchedDocument(
                url, url, "제목", null, null, "article", "본문", false
        ));
    }

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

    /** 어떤 링크를 자신이 맡았는지 기록만 하는 수집기. */
    private static final class RecordingFetcher implements SourceFetcher {

        private final java.util.function.Predicate<URI> supports;
        private final List<String> handled = new ArrayList<>();

        private RecordingFetcher(java.util.function.Predicate<URI> supports) {
            this.supports = supports;
        }

        @Override
        public boolean supports(URI uri) {
            return supports.test(uri);
        }

        @Override
        public SourceFetchResult fetch(String requestedUrl, URI uri) {
            handled.add(requestedUrl);
            return success(requestedUrl);
        }
    }
}
