package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.config.WebFetchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;

/**
 * JavaScript로 본문을 그리는 페이지를 Lambda의 헤드리스 브라우저로 다시 받는다.
 *
 * <p>모든 페이지에 쓰지 않는다. 브라우저는 요청당 비용이 크고, 측정한 13개 사이트 중
 * 실제로 필요한 곳은 둘뿐이었다. {@link WebFetchService}가 jsoup 결과가 비어 있을 때만 부른다.
 *
 * <p>브라우저를 백엔드 컨테이너 안에서 띄우지 않는다. 그러려면 읽기 전용 파일시스템과
 * {@code cap_drop: ALL}, 768MB 메모리 상한을 모두 되돌려야 한다. 렌더링 하나 때문에
 * 애플리케이션 전체의 보안 경계를 무르는 대신 실행 위치를 Lambda로 옮겼다.
 *
 * <p>실패는 예외로 올리지 않는다. 폴백이 실패해도 원문 수집 결과는 그대로 쓸 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "app.knowledge.fetch.render.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class LambdaPageRendererClient {

    private final WebFetchProperties properties;
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    /**
     * @return 렌더링된 HTML. 함수를 부르지 못했거나 렌더링에 실패하면 비어 있다.
     */
    public Optional<String> render(String url) {
        try {
            InvokeResponse response = lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(properties.render().functionName())
                    .payload(SdkBytes.fromUtf8String(requestPayload(url)))
                    .build());

            // 함수가 예외로 끝나면 payload에 스택이 담긴다. 우리 계약의 응답이 아니다.
            if (StringUtils.hasText(response.functionError())) {
                log.info(
                        "[source-render] function error url={} type={} payload={}",
                        url, response.functionError(), response.payload().asUtf8String()
                );
                return Optional.empty();
            }

            return readHtml(url, response.payload().asUtf8String());

        } catch (RuntimeException exception) {
            log.info("[source-render] failed url={} reason={}", url, exception.toString());
            return Optional.empty();
        }
    }

    private String requestPayload(String url) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("url", url);
        request.put("timeoutMs", properties.render().timeout().toMillis());
        // 원문 수집과 같은 신원으로 요청한다. Notion은 이 값에 정적 공개 문서를 돌려준다.
        request.put("userAgent", properties.userAgent());

        return request.toString();
    }

    private Optional<String> readHtml(String url, String payload) {
        try {
            JsonNode body = objectMapper.readTree(payload);

            if (body.hasNonNull("error")) {
                log.info(
                        "[source-render] rejected url={} error={}", url, body.get("error").asString()
                );
                return Optional.empty();
            }

            String html = body.path("html").asString(null);
            if (!StringUtils.hasText(html)) {
                return Optional.empty();
            }

            if (body.path("truncated").asBoolean(false)) {
                log.info("[source-render] truncated url={}", url);
            }

            return Optional.of(html);

        } catch (Exception exception) {
            log.info("[source-render] unreadable response url={} reason={}", url, exception.toString());
            return Optional.empty();
        }
    }
}
