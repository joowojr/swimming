package com.swimming.backend.knowledge.service.crawl.youtube;

import com.swimming.backend.common.config.google.GoogleProperties;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.service.crawl.LambdaPageRendererClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YoutubeSourceFetcherTest {

    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final String REQUESTED_URL = "https://youtu.be/dQw4w9WgXcQ?si=abcdef";
    private static final URI REQUESTED_URI = URI.create(REQUESTED_URL);

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private YoutubeSourceFetcher fetcher;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://www.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new YoutubeSourceFetcher(
                builder.build(),
                new GoogleProperties.Youtube("test-key", Duration.ofSeconds(10)),
                Optional.empty()
        );
    }

    @Test
    @DisplayName("영상 URL만 맡고 채널 홈이나 일반 문서는 맡지 않는다")
    void 영상_URL만_맡는다() {
        assertThat(fetcher.supports(REQUESTED_URI)).isTrue();
        assertThat(fetcher.supports(URI.create("https://www.youtube.com/shorts/dQw4w9WgXcQ"))).isTrue();
        assertThat(fetcher.supports(URI.create("https://www.youtube.com/@somechannel"))).isFalse();
        assertThat(fetcher.supports(URI.create("https://tech.kakao.com/posts/777"))).isFalse();
    }

    @Test
    @DisplayName("영상 정보를 제목·채널과 본문으로 옮기고 게시일·길이는 제외한다")
    void 영상_정보를_문서로_옮긴다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andExpect(queryParam("id", VIDEO_ID))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess(videoJson(), MediaType.APPLICATION_JSON));

        SourceFetchResult result = fetcher.fetch(REQUESTED_URL, REQUESTED_URI);
        server.verify();

        assertThat(result.isSuccess()).isTrue();

        FetchedDocument document = result.document();
        assertThat(document.title()).isEqualTo("스프링 트랜잭션 전파 정리");
        assertThat(document.author()).isEqualTo("스프링 채널");
        assertThat(document.publishedAt()).isNull();
        assertThat(document.sourceType()).isEqualTo("youtube");
        assertThat(document.truncated()).isFalse();
        assertThat(document.markdown())
                .contains("# 스프링 트랜잭션 전파 정리")
                .contains("채널: 스프링 채널")
                .contains("태그: spring, transaction")
                .contains("전파 옵션을 예제로 설명합니다.")
                .doesNotContain("게시일:")
                .doesNotContain("길이:");
    }

    @Test
    @DisplayName("Data API와 Lambda 자막을 합쳐 하나의 본문으로 만든다")
    void 메타데이터와_자막을_합친다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andRespond(withSuccess(videoJson(), MediaType.APPLICATION_JSON));

        LambdaPageRendererClient renderer = org.mockito.Mockito.mock(LambdaPageRendererClient.class);
        org.mockito.Mockito.when(renderer.render("https://www.youtube.com/watch?v=" + VIDEO_ID))
                .thenReturn(Optional.of("<article><p>자막 첫 문장입니다.</p><p>자막 두 번째 문장입니다.</p></article>"));
        fetcher = new YoutubeSourceFetcher(
                builder.build(),
                new GoogleProperties.Youtube("test-key", Duration.ofSeconds(10)),
                Optional.of(renderer)
        );

        SourceFetchResult result = fetcher.fetch(REQUESTED_URL, REQUESTED_URI);
        server.verify();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.document().markdown())
                .contains("## 자막")
                .contains("자막 첫 문장입니다. 자막 두 번째 문장입니다.");
    }

    @Test
    @DisplayName("공유 링크로 들어와도 정본 주소는 watch 주소 하나로 모인다")
    void 정본_주소는_watch_주소다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andRespond(withSuccess(videoJson(), MediaType.APPLICATION_JSON));

        FetchedDocument document = fetcher.fetch(REQUESTED_URL, REQUESTED_URI).document();

        assertThat(document.url()).isEqualTo("https://www.youtube.com/watch?v=" + VIDEO_ID);
        assertThat(document.canonicalUrl()).isEqualTo(document.url());
    }

    @Test
    @DisplayName("삭제되었거나 비공개인 영상은 본문 없음으로 처리한다")
    void 목록이_비어_있으면_본문_없음이다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        SourceFetchResult result = fetcher.fetch(REQUESTED_URL, REQUESTED_URI);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.SOURCE_EMPTY_CONTENT);
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("설명도 태그도 없는 영상은 소화할 내용이 없다고 본다")
    void 설명과_태그가_없으면_본문_없음이다() {
        String json = """
                {"items":[{"snippet":{"title":"제목만 있는 영상","channelTitle":"어떤 채널"}}]}
                """;
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        SourceFetchResult result = fetcher.fetch(REQUESTED_URL, REQUESTED_URI);

        assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.SOURCE_EMPTY_CONTENT);
    }

    @Test
    @DisplayName("할당량 초과 같은 4xx는 다시 시도하지 않는다")
    void 할당량_초과는_재시도하지_않는다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        SourceFetchResult result = fetcher.fetch(REQUESTED_URL, REQUESTED_URI);

        assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.HTTP_ERROR);
        assertThat(result.failureDetail()).isEqualTo("403");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("API 쪽 5xx는 다시 시도할 수 있는 실패로 남긴다")
    void 서버_오류는_재시도할_수_있다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        SourceFetchResult result = fetcher.fetch(REQUESTED_URL, REQUESTED_URI);

        assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.HTTP_ERROR);
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("영상이 아닌 유튜브 주소는 API를 부르지 않는다")
    void 영상이_아니면_API를_부르지_않는다() {
        URI channel = URI.create("https://www.youtube.com/@somechannel");

        SourceFetchResult result = fetcher.fetch(channel.toString(), channel);

        assertThat(result.failure()).isEqualTo(SourceFetchResult.Failure.INVALID_URL);
        server.verify();
    }

    @Test
    @DisplayName("실패 로그에 구글이 알려준 원인을 남긴다")
    void 실패_원인을_로그에_남긴다() {
        String body = """
                {"error":{"code":401,"message":"API key not valid. Please pass a valid API key.",
                 "errors":[{"reason":"unauthorized"}]}}
                """;

        assertThat(fetcher.errorMessage(body))
                .isEqualTo("API key not valid. Please pass a valid API key.");
        assertThat(fetcher.errorMessage("")).isEqualTo("(본문 없음)");
        assertThat(fetcher.errorMessage("형식이 바뀐 응답")).isEqualTo("형식이 바뀐 응답");
    }

    private String videoJson() {
        return """
                {
                  "items": [
                    {
                      "snippet": {
                        "title": "스프링 트랜잭션 전파 정리",
                        "description": "전파 옵션을 예제로 설명합니다.",
                        "channelTitle": "스프링 채널",
                        "publishedAt": "2025-03-04T05:06:07Z",
                        "tags": ["spring", "transaction"]
                      },
                      "contentDetails": { "duration": "PT12M34S" }
                    }
                  ]
                }
                """;
    }
}
