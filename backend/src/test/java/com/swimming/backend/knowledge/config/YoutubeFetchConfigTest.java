package com.swimming.backend.knowledge.config;

import com.swimming.backend.common.config.google.GoogleProperties;
import com.swimming.backend.knowledge.service.crawl.youtube.YoutubeSourceFetcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설정 이름이 어긋나면 수집기가 조용히 등록되지 않고, 호출도 조용히 사라진다.
 * 키 유무가 실제로 빈 등록으로 이어지는지 여기서 확인한다.
 */
class YoutubeFetchConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(GoogleProperties.class, this::properties)
            .withUserConfiguration(YoutubeFetchConfig.class);

    @Test
    @DisplayName("키가 있으면 유튜브 수집기를 만든다")
    void 키가_있으면_수집기를_만든다() {
        runner.withPropertyValues("app.google.youtube.api-key=key")
                .run(context -> assertThat(context).hasSingleBean(YoutubeSourceFetcher.class));
    }

    @Test
    @DisplayName("키가 없어도 기동하고, 유튜브 링크는 일반 웹 수집으로 간다")
    void 키가_없어도_기동한다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(YoutubeSourceFetcher.class);
        });
    }

    @Test
    @DisplayName("키를 빈 값으로 둔 환경도 기동한다")
    void 키가_비어_있어도_기동한다() {
        runner.withPropertyValues("app.google.youtube.api-key=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(YoutubeSourceFetcher.class);
        });
    }

    @Test
    @DisplayName("공백만 든 키는 없는 것으로 본다")
    void 공백만_든_키는_없는_것으로_본다() {
        runner.withPropertyValues("app.google.youtube.api-key=   ").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(YoutubeSourceFetcher.class);
        });
    }

    private GoogleProperties properties() {
        return new GoogleProperties(
                new GoogleProperties.Auth("client-id"),
                new GoogleProperties.Youtube("key", Duration.ofSeconds(10))
        );
    }
}
