package com.swimming.backend.knowledge.live;

import com.swimming.backend.knowledge.config.WebFetchProperties;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.service.crawl.HtmlToMarkdownConverter;
import com.swimming.backend.knowledge.service.crawl.SourceFetchDispatcher;
import com.swimming.backend.knowledge.service.crawl.WebFetchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 웹을 호출한다. 기본 test 태스크에서는 제외되고 {@code ./gradlew fetchLive} 로만 돈다.
 */
@Tag("fetch-live")
class SourceFetchLiveTest {

    private static final List<String> SPRING_DOCS = List.of(
            "https://docs.spring.io/spring-boot/reference/features/external-config.html",
            "https://docs.spring.io/spring-framework/reference/core/beans/introduction.html",
            "https://docs.spring.io/spring-ai/reference/api/chatclient.html",
            "https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html",
            "https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html",
            "https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html",
            "https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html",
            "https://spring.io/projects/spring-ai"
    );

    private final WebFetchProperties properties = new WebFetchProperties(
            4,
            Duration.ofSeconds(15),
            4 * 1024 * 1024,
            80_000,
            300,
            "SwimmingBot/0.1 (+https://swimming.app)",
            new WebFetchProperties.Render(false, null, Duration.ofSeconds(20), 1000)
    );

    private final SourceFetchDispatcher service = new SourceFetchDispatcher(
            new WebFetchService(properties, new HtmlToMarkdownConverter(), Optional.empty()),
            List.of(),
            properties
    );

    @Test
    @DisplayName("Spring 공식 문서를 함께 수집해 마크다운으로 만든다")
    void fetchesSpringDocs() throws Exception {
        long startedAt = System.currentTimeMillis();
        List<SourceFetchResult> results = service.fetchAll(SPRING_DOCS);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        Path outDir = Path.of("build", "fetch-live");
        Files.createDirectories(outDir);

        System.out.printf("%n=== %d건, %.1f초 ===%n%n", results.size(), elapsedMs / 1000.0);
        System.out.printf("%-6s %-8s %-6s %-5s %-5s %s%n",
                "결과", "글자수", "잘림", "헤딩", "표", "제목");

        for (SourceFetchResult result : results) {
            if (!result.isSuccess()) {
                System.out.printf("%-6s %s %s  %s%n",
                        "실패", result.failure(), result.failureDetail(), result.requestedUrl());
                continue;
            }

            String markdown = result.document().markdown();
            System.out.printf("%-6s %-8d %-6s %-5d %-5d %s%n",
                    "성공",
                    markdown.length(),
                    result.document().truncated() ? "예" : "아니오",
                    markdown.lines().filter(l -> l.startsWith("#")).count(),
                    markdown.lines().filter(l -> l.startsWith("|")).count(),
                    result.document().title());

            String name = result.requestedUrl()
                    .replaceAll("^https?://", "")
                    .replaceAll("[^A-Za-z0-9]+", "_");
            Files.writeString(outDir.resolve(name + ".md"), markdown);
        }

        System.out.println("\n마크다운 저장 위치: " + outDir.toAbsolutePath());

        assertThat(results).hasSize(SPRING_DOCS.size());
        assertThat(results).allMatch(SourceFetchResult::isSuccess);
        assertThat(results).allSatisfy(result ->
                assertThat(result.document().markdown()).isNotBlank());
    }
}
