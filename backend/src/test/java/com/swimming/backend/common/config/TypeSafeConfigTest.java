package com.swimming.backend.common.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.swimming.backend.common.client.TypeSafeClient;
import com.swimming.backend.common.client.dto.SystemOneRequest;
import com.swimming.backend.common.client.dto.SystemOneResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class TypeSafeConfigTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(TypeSafeClient.class);
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
    }

    @AfterEach
    void tearDown() {
        logger.setLevel(previousLevel);
    }

    @Test
    @DisplayName("DEBUG면 요청·응답 본문을 남기고, 응답은 그대로 역직렬화되며 API 키는 남기지 않는다")
    void DEBUG면_본문을_남긴다(CapturedOutput output) {
        logger.setLevel(Level.DEBUG);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.typesafe.ai")
                .requestInterceptor(TypeSafeConfig::logExchange);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).bufferContent().build();
        TypeSafeClient client = new TypeSafeClient(builder.build(), "secret-key");
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone")).andRespond(withSuccess("""
                {"model":"jev-1.13.0","answers":{"assignment":{"type":"choice","choice":"NEW",
                "probabilities":{"1":0.2,"NEW":0.8},"confidence":0.7}},
                "usage":{"input_tokens":10,"output_tokens":2}}
                """, MediaType.APPLICATION_JSON));

        SystemOneResponse response = client.systemOne(new SystemOneRequest(
                Map.of("summary", "요약"), "jev-1.13.0",
                Map.of("assignment", new SystemOneRequest.Choice("지시", Map.of("1", "MCP", "NEW", "새로")))));

        assertThat(((SystemOneResponse.Choice) response.answers().get("assignment")).choice()).isEqualTo("NEW");
        assertThat(output).contains("request: POST https://api.typesafe.ai/v1/systemone", "\"summary\":\"요약\"");
        assertThat(output).contains("response: 200 OK", "\"choice\":\"NEW\"");
        assertThat(output).doesNotContain("secret-key");
    }

    @Test
    @DisplayName("DEBUG가 아니면 본문을 남기지 않는다")
    void DEBUG가_아니면_남기지_않는다(CapturedOutput output) {
        logger.setLevel(Level.INFO);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.typesafe.ai")
                .requestInterceptor(TypeSafeConfig::logExchange);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeClient client = new TypeSafeClient(builder.build(), "secret-key");
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone")).andRespond(withSuccess(
                "{\"model\":\"jev-1.13.0\",\"answers\":{}}", MediaType.APPLICATION_JSON));

        client.systemOne(new SystemOneRequest(Map.of("summary", "요약"), "jev-1.13.0", Map.of()));

        assertThat(output).doesNotContain("request: POST", "response: 200");
    }
}
