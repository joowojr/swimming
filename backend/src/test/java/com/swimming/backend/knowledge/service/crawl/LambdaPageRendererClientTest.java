package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.config.KnowledgeFetchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LambdaPageRendererClientTest {

    private static final String URL = "https://tech.kakao.com/posts/1";
    private static final String FUNCTION_NAME = "swimming-prod-page-renderer";
    private static final String USER_AGENT = "SwimmingBot/0.1 (+https://swimming.app)";

    private LambdaClient lambdaClient;
    private LambdaPageRendererClient renderer;

    @BeforeEach
    void setUp() {
        lambdaClient = mock(LambdaClient.class);

        KnowledgeFetchProperties properties = new KnowledgeFetchProperties(
                4,
                Duration.ofSeconds(15),
                4 * 1024 * 1024,
                80_000,
                300,
                USER_AGENT,
                new KnowledgeFetchProperties.Render(
                        true, FUNCTION_NAME, Duration.ofSeconds(20), 1000
                )
        );

        renderer = new LambdaPageRendererClient(properties, lambdaClient, new ObjectMapper());
    }

    private void givenResponse(String payload) {
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(
                InvokeResponse.builder().payload(SdkBytes.fromUtf8String(payload)).build()
        );
    }

    @Test
    @DisplayName("렌더링한 HTML을 돌려준다")
    void returnsRenderedHtml() {
        givenResponse("{\"html\":\"<html><body>본문</body></html>\",\"truncated\":false}");

        assertThat(renderer.render(URL)).contains("<html><body>본문</body></html>");
    }

    @Test
    @DisplayName("URL과 타임아웃, 수집과 같은 User-Agent를 함께 보낸다")
    void sendsRequestPayload() throws Exception {
        givenResponse("{\"html\":\"<html></html>\"}");

        renderer.render(URL);

        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        org.mockito.Mockito.verify(lambdaClient).invoke(captor.capture());

        InvokeRequest request = captor.getValue();
        assertThat(request.functionName()).isEqualTo(FUNCTION_NAME);

        JsonNode payload = new ObjectMapper().readTree(request.payload().asUtf8String());
        assertThat(payload.get("url").asString()).isEqualTo(URL);
        assertThat(payload.get("timeoutMs").asLong()).isEqualTo(20_000);
        assertThat(payload.get("userAgent").asString())
                .as("렌더링만 다른 봇으로 보이지 않게 한다")
                .isEqualTo(USER_AGENT);
    }

    @Test
    @DisplayName("함수가 error를 주면 폴백을 포기하고 원문 결과를 쓰게 둔다")
    void ignoresErrorResult() {
        givenResponse("{\"error\":\"TIMEOUT\"}");

        assertThat(renderer.render(URL)).isEmpty();
    }

    @Test
    @DisplayName("렌더러가 받은 HTTP 오류 페이지는 본문으로 쓰지 않는다")
    void ignoresRenderedHttpError() {
        givenResponse("{\"error\":\"HTTP_ERROR\",\"status\":403}");

        assertThat(renderer.render(URL)).isEmpty();
    }

    @Test
    @DisplayName("함수가 예외로 끝나도 예외를 올리지 않는다")
    void ignoresFunctionError() {
        when(lambdaClient.invoke(any(InvokeRequest.class))).thenReturn(
                InvokeResponse.builder()
                        .functionError("Unhandled")
                        .payload(SdkBytes.fromUtf8String("{\"errorMessage\":\"boom\"}"))
                        .build()
        );

        assertThat(renderer.render(URL)).isEmpty();
    }

    @Test
    @DisplayName("함수를 부르지 못해도 예외를 올리지 않는다")
    void ignoresInvocationFailure() {
        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenThrow(SdkClientException.create("no credentials"));

        assertThat(renderer.render(URL)).isEmpty();
    }

    @Test
    @DisplayName("응답이 계약을 벗어나면 비어 있는 결과로 본다")
    void ignoresUnreadableResponse() {
        givenResponse("not json");
        assertThat(renderer.render(URL)).isEmpty();

        givenResponse("{\"html\":\"\"}");
        assertThat(renderer.render(URL)).isEmpty();
    }

    @Test
    @DisplayName("잘린 응답도 그대로 쓴다. 앞부분만으로도 본문 추출이 되는 경우가 있다")
    void usesTruncatedHtml() {
        givenResponse("{\"html\":\"<html>앞부분\",\"truncated\":true}");

        Optional<String> rendered = renderer.render(URL);

        assertThat(rendered).contains("<html>앞부분");
    }
}
