package com.swimming.backend.knowledge.live;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import com.swimming.backend.knowledge.config.WebFetchProperties;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.service.crawl.HtmlToMarkdownConverter;
import com.swimming.backend.knowledge.service.crawl.LambdaPageRendererClient;
import com.swimming.backend.knowledge.service.crawl.SourceFetchDispatcher;
import com.swimming.backend.knowledge.service.crawl.WebFetchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원하는 링크를 직접 넣어 파싱 결과를 확인한다.
 *
 * <pre>
 * ./gradlew fetchUrl -Purls="https://tech.kakao.com/posts/777"
 * ./gradlew fetchUrl -Purls="https://a.com/x,https://b.com/y"
 * ./gradlew fetchUrl -Purls="..." -Prender=false     # 렌더링 폴백 끄고
 * ./gradlew fetchUrl -Purls="..." -Plines=40          # 앞 40줄만 (기본은 전체)
 * </pre>
 *
 * 마크다운 전체를 그대로 출력하고, 같은 내용을 {@code build/fetch-live/} 에 파일로도 남긴다.
 */
@Tag("fetch-live")
class SourceFetchPlaygroundTest {
    /** 렌더링 폴백은 실제 Lambda를 부른다. AWS 자격증명이 있는 셸에서만 의미가 있다. */
    private static final String RENDER_FUNCTION_NAME = System.getenv()
            .getOrDefault("KNOWLEDGE_FETCH_RENDER_FUNCTION_NAME", "swimming-prod-page-renderer");

    private static LambdaClient lambdaClient(WebFetchProperties properties) {
        return LambdaClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(properties.render().timeout().plusSeconds(5))
                        .build())
                .build();
    }


    private static final String DEFAULT_URL = "https://docs.spring.io/spring-ai/reference/api/chatclient.html";

    @Test
    @DisplayName("-Purls 로 넘긴 링크를 수집해 마크다운을 보여준다")
    void fetchGivenUrls() throws Exception {
        List<String> urls = Arrays.stream(System.getProperty("urls", DEFAULT_URL).split(","))
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .toList();

        boolean render = Boolean.parseBoolean(System.getProperty("render", "true"));
        // 0이면 자르지 않고 전체를 출력한다.
        int lines = Integer.parseInt(System.getProperty("lines", "0"));

        SourceFetchDispatcher service = service(render);
        Path outDir = Path.of("build", "fetch-live");
        Files.createDirectories(outDir);

        System.out.printf("%n렌더링 폴백: %s | 링크 %d개%n", render ? "켬" : "끔", urls.size());

        List<SourceFetchResult> results = service.fetchAll(urls);

        for (SourceFetchResult result : results) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println(result.requestedUrl());
            System.out.println("=".repeat(100));

            if (!result.isSuccess()) {
                System.out.printf("실패: %s (%s)%n", result.failure(), result.failureDetail());
                continue;
            }

            FetchedDocument document = result.document();
            String markdown = document.markdown();

            System.out.printf("제목      : %s%n", document.title());
            System.out.printf("작성자    : %s%n", document.author());
            System.out.printf("발행일    : %s%n", document.publishedAt());
            System.out.printf("종류      : %s%n", document.sourceType());
            System.out.printf("최종 URL  : %s%n", document.url());
            System.out.printf("canonical : %s%n", document.canonicalUrl());
            System.out.printf("글자 수   : %d%s%n", markdown.length(),
                    document.truncated() ? " (상한에 걸려 잘림)" : "");
            System.out.printf("헤딩/표   : %d / %d줄%n",
                    markdown.lines().filter(line -> line.startsWith("#")).count(),
                    markdown.lines().filter(line -> line.startsWith("|")).count());

            Path file = outDir.resolve(fileName(result.requestedUrl()));
            Files.writeString(file, markdown);

            long totalLines = markdown.lines().count();

            if (lines > 0 && lines < totalLines) {
                System.out.printf("%n--- 마크다운 (%d줄 중 앞 %d줄) ---%n", totalLines, lines);
                markdown.lines().limit(lines).forEach(System.out::println);
                System.out.printf("%n... 나머지 %d줄 생략%n", totalLines - lines);
            } else {
                System.out.printf("%n--- 마크다운 전체 (%d줄) ---%n", totalLines);
                System.out.println(markdown);
            }

            System.out.printf("%n파일 저장: %s%n", file.toAbsolutePath());
        }

        assertThat(results).hasSize(urls.size());
    }

    private SourceFetchDispatcher service(boolean render) {
        WebFetchProperties properties = new WebFetchProperties(
                4,
                Duration.ofSeconds(15),
                4 * 1024 * 1024,
                80_000,
                300,
                "SwimmingBot/0.1 (+https://swimming.app)",
                new WebFetchProperties.Render(render, RENDER_FUNCTION_NAME, Duration.ofSeconds(20), 1000)
        );

        WebFetchService webFetchService = new WebFetchService(
                properties,
                new HtmlToMarkdownConverter(),
                render
                        ? Optional.of(new LambdaPageRendererClient(
                                properties,
                                lambdaClient(properties),
                                new ObjectMapper()
                        ))
                        : Optional.empty()
        );

        return new SourceFetchDispatcher(webFetchService, List.of(), properties);
    }

    private String fileName(String url) {
        String name = url.replaceAll("^https?://", "").replaceAll("[^A-Za-z0-9]+", "_");
        return (name.length() > 120 ? name.substring(0, 120) : name) + ".md";
    }
}
