package com.swimming.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class UrlUtilsTest {

    @Test
    @DisplayName("HTTP와 HTTPS URL을 URI로 파싱한다")
    void parsesHttpUrls() {
        assertThat(UrlUtils.parseHttpUrl("https://www.youtube.com/watch?v=example"))
                .contains(URI.create("https://www.youtube.com/watch?v=example"));
        assertThat(UrlUtils.parseHttpUrl("http://youtu.be/example"))
                .contains(URI.create("http://youtu.be/example"));
    }

    @Test
    @DisplayName("HTTP가 아니거나 형식이 잘못된 URL은 파싱하지 않는다")
    void rejectsUnsupportedUrls() {
        assertThat(UrlUtils.parseHttpUrl("ftp://youtube.com/example")).isEmpty();
        assertThat(UrlUtils.parseHttpUrl("not a url")).isEmpty();
        assertThat(UrlUtils.parseHttpUrl(" ")).isEmpty();
    }

    @Test
    @DisplayName("동일한 호스트와 서브도메인을 판별한다")
    void matchesHostAndSubdomain() {
        assertThat(UrlUtils.hasHostOrSubdomain(
                URI.create("https://youtube.com/watch?v=example"),
                "youtube.com"
        )).isTrue();
        assertThat(UrlUtils.hasHostOrSubdomain(
                URI.create("https://music.youtube.com/watch?v=example"),
                "youtube.com"
        )).isTrue();
        assertThat(UrlUtils.hasHostOrSubdomain(
                URI.create("https://youtube.com.example.org/watch?v=example"),
                "youtube.com"
        )).isFalse();
    }

    @Test
    @DisplayName("값이 있는 쿼리 파라미터만 확인한다")
    void findsNonEmptyQueryParameter() {
        assertThat(UrlUtils.hasNonEmptyQueryParameter(
                URI.create("https://youtube.com/watch?v=example&list=playlist"),
                "v"
        )).isTrue();
        assertThat(UrlUtils.hasNonEmptyQueryParameter(
                URI.create("https://youtube.com/watch?v="),
                "v"
        )).isFalse();
        assertThat(UrlUtils.hasNonEmptyQueryParameter(
                URI.create("https://youtube.com/watch?list=playlist"),
                "v"
        )).isFalse();
    }
}
