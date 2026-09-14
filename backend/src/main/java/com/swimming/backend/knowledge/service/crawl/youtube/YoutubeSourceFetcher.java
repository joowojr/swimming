package com.swimming.backend.knowledge.service.crawl.youtube;

import com.swimming.backend.common.config.google.GoogleProperties;
import com.swimming.backend.knowledge.dto.out.FetchedDocument;
import com.swimming.backend.knowledge.dto.out.SourceFetchResult;
import com.swimming.backend.knowledge.service.crawl.LambdaPageRendererClient;
import com.swimming.backend.knowledge.service.crawl.SourceFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 유튜브 영상을 공식 Data API v3로 받아 문서로 만든다.
 *
 * <p>시청 페이지를 크롤링하지 않는다. 본문이 HTML에 없고 플레이어 JavaScript가 그리기
 * 때문에, 일반 수집으로는 사이트 메뉴만 남는다. 제목·채널·설명·태그는 API가 그대로 준다.
 *
 * <p>Data API 메타데이터와 Lambda 자막 렌더링을 병렬로 요청한다. 자막은 공식 Data API의
 * {@code captions} 엔드포인트가 영상 소유자의 OAuth 토큰을 요구하기 때문에 Lambda의
 * Playwright 경로에서 가져온다.
 */
@Slf4j
@RequiredArgsConstructor
public class YoutubeSourceFetcher implements SourceFetcher {

    /** 이 수집기가 만든 문서임을 카드와 통계에서 구분하는 값. */
    private static final String SOURCE_TYPE = "youtube";

    private static final String VIDEOS_PATH = "/youtube/v3/videos";

    /** 구글 오류 응답의 {@code error.message}. 형식이 바뀌면 본문 앞부분으로 대신한다. */
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private static final int ERROR_BODY_LIMIT = 200;

    private final RestClient restClient;
    private final GoogleProperties.Youtube properties;
    private final Optional<LambdaPageRendererClient> pageRenderer;

    /**
     * 유튜브 도메인이라도 영상 URL이 아니면 맡지 않는다. 채널 홈이나 재생목록 페이지는
     * 영상 API로 할 수 있는 일이 없어 일반 수집으로 보내는 편이 낫다.
     */
    @Override
    public boolean supports(URI uri) {
        return YoutubeUrlParser.isYoutubeHost(uri) && YoutubeUrlParser.videoIdOf(uri).isPresent();
    }

    @Override
    public SourceFetchResult fetch(String requestedUrl, URI uri) {
        Optional<String> videoId = YoutubeUrlParser.videoIdOf(uri);
        if (videoId.isEmpty()) {
            return SourceFetchResult.failure(
                    requestedUrl,
                    SourceFetchResult.Failure.INVALID_URL,
                    uri.toString()
            );
        }

        return request(requestedUrl, videoId.get());
    }

    private SourceFetchResult request(String requestedUrl, String videoId) {
        String watchUrl = YoutubeUrlParser.watchUrl(videoId);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<SourceFetchResult> metadata = executor.submit(
                    () -> requestMetadata(requestedUrl, videoId)
            );
            Future<Optional<String>> transcript = executor.submit(
                    () -> pageRenderer.flatMap(renderer -> renderer.render(watchUrl))
            );

            SourceFetchResult metadataResult = metadata.get();
            Optional<String> transcriptHtml = transcript.get();
            if (!metadataResult.isSuccess()) {
                return metadataResult;
            }
            return mergeTranscript(metadataResult, transcriptHtml, requestedUrl, videoId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SourceFetchResult.failure(
                    requestedUrl,
                    SourceFetchResult.Failure.UNKNOWN,
                    "YouTube 병렬 수집이 중단되었습니다."
            );
        } catch (ExecutionException exception) {
            return unknown(requestedUrl, videoId, exception);
        }
    }

    private SourceFetchResult requestMetadata(String requestedUrl, String videoId) {
        try {
            return restClient.get()
                    .uri(builder -> builder
                            .path(VIDEOS_PATH)
                            .queryParam("part", "snippet")
                            .queryParam("id", videoId)
                            .queryParam("key", properties.apiKey())
                            .build())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            // 키가 URL에 있으므로 주소는 남기지 않는다. 구글이 원인을 본문에
                            // 담아 보내므로 상태 코드만으로는 키 문제인지 할당량인지 알 수 없다.
                            log.info(
                                    "[source-fetch] youtube api error videoId={} status={} message={}",
                                    videoId,
                                    response.getStatusCode().value(),
                                    errorMessage(response.bodyTo(String.class))
                            );
                            return SourceFetchResult.failure(
                                    requestedUrl,
                                    SourceFetchResult.Failure.HTTP_ERROR,
                                    String.valueOf(response.getStatusCode().value())
                            );
                        }

                        return toDocument(
                                requestedUrl,
                                videoId,
                                response.bodyTo(YoutubeVideoListResponse.class)
                        );
                    });

        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                return SourceFetchResult.failure(
                        requestedUrl,
                        SourceFetchResult.Failure.TIMEOUT,
                        properties.timeout().toString()
                );
            }
            return unknown(requestedUrl, videoId, exception);

        } catch (RuntimeException exception) {
            return unknown(requestedUrl, videoId, exception);
        }
    }

    private SourceFetchResult mergeTranscript(
            SourceFetchResult metadataResult,
            Optional<String> transcriptHtml,
            String requestedUrl,
            String videoId
    ) {
        if (transcriptHtml.isEmpty()) {
            log.info("[source-fetch] youtube transcript unavailable videoId={}", videoId);
            return metadataResult;
        }

        FetchedDocument document = metadataResult.document();
        String transcript = org.jsoup.Jsoup.parse(transcriptHtml.get()).text().strip();
        if (!StringUtils.hasText(transcript)) {
            return metadataResult;
        }

        log.info(
                "[source-fetch] youtube merged videoId={} transcriptLength={}",
                videoId, transcript.length()
        );
        return SourceFetchResult.success(requestedUrl, new FetchedDocument(
                document.url(),
                document.canonicalUrl(),
                document.title(),
                document.author(),
                document.publishedAt(),
                document.sourceType(),
                document.markdown() + "\n\n## 자막\n\n" + transcript,
                document.truncated()
        ));
    }

    /**
     * 삭제되었거나 비공개인 영상은 200에 빈 목록으로 온다. 오류가 아니라 가져올 내용이
     * 없는 것이므로 본문 없음으로 처리한다.
     */
    private SourceFetchResult toDocument(
            String requestedUrl,
            String videoId,
            YoutubeVideoListResponse body
    ) {
        YoutubeVideoListResponse.Item item = firstItem(body);
        if (item == null || item.snippet() == null) {
            return SourceFetchResult.failure(
                    requestedUrl,
                    SourceFetchResult.Failure.SOURCE_EMPTY_CONTENT,
                    videoId
            );
        }

        YoutubeVideoListResponse.Snippet snippet = item.snippet();
        String markdown = markdown(snippet);

        if (!StringUtils.hasText(markdown)) {
            return SourceFetchResult.failure(
                    requestedUrl,
                    SourceFetchResult.Failure.SOURCE_EMPTY_CONTENT,
                    videoId
            );
        }

        String watchUrl = YoutubeUrlParser.watchUrl(videoId);

        return SourceFetchResult.success(requestedUrl, new FetchedDocument(
                watchUrl,
                watchUrl,
                snippet.title(),
                snippet.channelTitle(),
                null,
                SOURCE_TYPE,
                markdown,
                false
        ));
    }

    /**
     * 제목만 남는 영상은 소화할 내용이 없다고 본다. 설명도 태그도 없으면 빈 문자열을
     * 돌려주고 호출한 쪽이 본문 없음으로 처리한다.
     */
    private String markdown(YoutubeVideoListResponse.Snippet snippet) {
        boolean hasDescription = StringUtils.hasText(snippet.description());
        boolean hasTags = snippet.tags() != null && !snippet.tags().isEmpty();

        if (!hasDescription && !hasTags) {
            return "";
        }

        StringBuilder markdown = new StringBuilder();
        if (StringUtils.hasText(snippet.title())) {
            markdown.append("# ").append(snippet.title()).append("\n\n");
        }

        List<String> facts = facts(snippet);
        if (!facts.isEmpty()) {
            facts.forEach(fact -> markdown.append("- ").append(fact).append('\n'));
            markdown.append('\n');
        }

        if (hasDescription) {
            markdown.append("## 설명\n\n").append(snippet.description().strip()).append('\n');
        }

        return markdown.toString().strip();
    }

    private List<String> facts(YoutubeVideoListResponse.Snippet snippet) {
        List<String> facts = new ArrayList<>();

        if (StringUtils.hasText(snippet.channelTitle())) {
            facts.add("채널: " + snippet.channelTitle());
        }
        if (snippet.tags() != null && !snippet.tags().isEmpty()) {
            facts.add("태그: " + String.join(", ", snippet.tags()));
        }

        return facts;
    }

    /**
     * 오류 본문에서 사람이 읽을 한 줄만 꺼낸다.
     *
     * <p>본문 전체를 찍으면 스택처럼 길어지고, 버리면 401이 키 문제인지 권한 문제인지
     * 알 수 없다. 응답 구조를 record로 받지 않는 것은 오류 형식이 우리 계약이 아니기 때문이다.
     */
    String errorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "(본문 없음)";
        }

        Matcher matcher = ERROR_MESSAGE.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return body.length() <= ERROR_BODY_LIMIT ? body.strip() : body.substring(0, ERROR_BODY_LIMIT);
    }

    private YoutubeVideoListResponse.Item firstItem(YoutubeVideoListResponse body) {
        if (body == null || body.items() == null || body.items().isEmpty()) {
            return null;
        }
        return body.items().getFirst();
    }

    private boolean isTimeout(ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException;
    }

    private SourceFetchResult unknown(String requestedUrl, String videoId, Exception exception) {
        log.info(
                "[source-fetch] youtube failed videoId={} reason={}",
                videoId, exception.toString()
        );
        return SourceFetchResult.failure(
                requestedUrl,
                SourceFetchResult.Failure.UNKNOWN,
                exception.getMessage()
        );
    }
}
