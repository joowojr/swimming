package com.swimming.backend.knowledge.service.crawl.youtube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class YoutubeUrlParserTest {

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ"
    })
    @DisplayName("공유 방식이 달라도 같은 영상 ID를 뽑는다")
    void 링크_모양이_달라도_같은_영상_ID를_뽑는다(String url) {
        assertThat(YoutubeUrlParser.videoIdOf(URI.create(url))).contains(VIDEO_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90s",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890",
            "https://youtu.be/dQw4w9WgXcQ?si=abcdef",
            "https://www.youtube.com/watch?app=desktop&v=dQw4w9WgXcQ"
    })
    @DisplayName("재생 위치나 재생목록이 붙어도 영상 ID는 그대로다")
    void 부가_파라미터가_붙어도_영상_ID는_그대로다(String url) {
        assertThat(YoutubeUrlParser.videoIdOf(URI.create(url))).contains(VIDEO_ID);
    }

    @Test
    @DisplayName("같은 영상이면 어떤 모양으로 들어와도 정본 주소가 하나로 모인다")
    void 정본_주소가_하나로_모인다() {
        String fromShare = YoutubeUrlParser.videoIdOf(
                URI.create("https://youtu.be/dQw4w9WgXcQ?si=abcdef")
        ).map(YoutubeUrlParser::watchUrl).orElseThrow();

        String fromWatch = YoutubeUrlParser.videoIdOf(
                URI.create("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90s")
        ).map(YoutubeUrlParser::watchUrl).orElseThrow();

        assertThat(fromShare)
                .isEqualTo(fromWatch)
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/@somechannel",
            "https://www.youtube.com/playlist?list=PL1234567890",
            "https://www.youtube.com/",
            "https://www.youtube.com/watch?v=tooshort"
    })
    @DisplayName("영상을 가리키지 않는 유튜브 주소는 맡지 않는다")
    void 영상이_아닌_유튜브_주소는_비어_있다(String url) {
        assertThat(YoutubeUrlParser.videoIdOf(URI.create(url))).isEmpty();
    }

    @Test
    @DisplayName("유튜브가 아닌 주소는 호스트 판정부터 거짓이다")
    void 유튜브가_아닌_주소는_거짓이다() {
        URI uri = URI.create("https://tech.kakao.com/posts/777");

        assertThat(YoutubeUrlParser.isYoutubeHost(uri)).isFalse();
        assertThat(YoutubeUrlParser.videoIdOf(uri)).isEmpty();
    }

    @Test
    @DisplayName("호스트가 없는 주소에도 터지지 않는다")
    void 호스트가_없어도_터지지_않는다() {
        assertThat(YoutubeUrlParser.isYoutubeHost(URI.create("not-a-url"))).isFalse();
        assertThat(YoutubeUrlParser.videoIdOf(URI.create("not-a-url"))).isEmpty();
    }
}
