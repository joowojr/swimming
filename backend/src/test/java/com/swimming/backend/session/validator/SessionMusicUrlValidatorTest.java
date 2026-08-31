package com.swimming.backend.session.validator;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionMusicUrlValidatorTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=video",
            "https://youtube.com/playlist?list=playlist",
            "https://www.youtube.com/shorts/video",
            "https://www.youtube.com/embed/video",
            "https://www.youtube-nocookie.com/embed/video",
            "https://youtu.be/video"
    })
    @DisplayName("비어 있거나 재생 가능한 YouTube URL을 허용한다")
    void acceptsSupportedMusicUrl(String musicUrl) {
        assertThatCode(() -> SessionMusicUrlValidator.validate(musicUrl))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "not-a-url",
            "https://example.com/music",
            "https://www.youtube.com/watch?v=",
            "https://www.youtube.com/playlist?list=",
            "https://www.youtube.com/shorts/",
            "https://www.youtube-nocookie.com/watch?v=video",
            "https://youtu.be/"
    })
    @DisplayName("재생 대상을 가리키지 않는 음악 URL을 거부한다")
    void rejectsUnsupportedMusicUrl(String musicUrl) {
        assertThatThrownBy(() -> SessionMusicUrlValidator.validate(musicUrl))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MUSIC_URL));
    }
}
