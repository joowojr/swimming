package com.swimming.backend.knowledge.config;

import com.swimming.backend.common.config.google.GoogleProperties;
import com.swimming.backend.knowledge.service.crawl.LambdaPageRendererClient;
import com.swimming.backend.knowledge.service.crawl.youtube.YoutubeSourceFetcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * API 키가 있을 때만 유튜브 전용 수집기를 만든다.
 *
 * <p>키가 없으면 빈이 없고, 유튜브 링크는 일반 웹 수집으로 떨어진다. 키를 넣지 않은
 * 환경에서 애플리케이션이 뜨지 않거나 링크 저장이 막히는 일이 없도록 한 것이다.
 *
 * <p>빈 문자열은 키가 없는 것으로 본다. {@code ${YOUTUBE_API_KEY:}} 가 빈 값으로 풀리면
 * 프로퍼티는 존재하므로, 존재 여부만 보는 조건으로는 걸러지지 않는다.
 */
@Configuration
@ConditionalOnExpression("'${app.google.youtube.api-key:}'.trim() != ''")
public class YoutubeFetchConfig {

    private static final String API_BASE_URL = "https://www.googleapis.com";

    /**
     * 순서를 명시해 둔다. 수집기가 늘어나면 이 숫자로 우선순위를 정한다.
     */
    @Bean
    @Order(100)
    public YoutubeSourceFetcher youtubeSourceFetcher(
            RestClient.Builder restClientBuilder,
            GoogleProperties properties,
            Optional<LambdaPageRendererClient> pageRenderer
    ) {
        GoogleProperties.Youtube youtube = properties.youtube();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(youtube.timeout());

        return new YoutubeSourceFetcher(
                restClientBuilder
                        .baseUrl(API_BASE_URL)
                        .requestFactory(requestFactory)
                        .build(),
                youtube,
                pageRenderer
        );
    }
}
