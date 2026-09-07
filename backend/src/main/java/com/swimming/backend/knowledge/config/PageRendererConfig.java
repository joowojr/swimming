package com.swimming.backend.knowledge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.time.Duration;

/**
 * 렌더링 폴백이 켜져 있을 때만 Lambda 클라이언트를 만든다.
 *
 * <p>자격증명과 리전은 기본 체인이 EC2 인스턴스 역할과 IMDS에서 가져온다. 애플리케이션이
 * 들고 있을 비밀이 없다.
 */
@Configuration
@ConditionalOnProperty(
        name = "app.knowledge.fetch.render.enabled",
        havingValue = "true"
)
public class PageRendererConfig {

    /** 함수가 스스로 끊는 시간보다 늦게 포기해야 원인이 타임아웃인지 구분할 수 있다. */
    private static final Duration CALL_TIMEOUT_MARGIN = Duration.ofSeconds(5);

    @Bean
    public LambdaClient pageRendererLambdaClient(KnowledgeFetchProperties properties) {
        if (!StringUtils.hasText(properties.render().functionName())) {
            throw new IllegalStateException(
                    "app.knowledge.fetch.render.enabled=true 이면 function-name 이 필요하다."
            );
        }

        return LambdaClient.builder()
                // 동기 호출 하나에 이벤트 루프가 필요 없다. JDK 커넥션으로 충분하다.
                .httpClient(UrlConnectionHttpClient.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(properties.render().timeout().plus(CALL_TIMEOUT_MARGIN))
                        .build())
                .build();
    }
}
