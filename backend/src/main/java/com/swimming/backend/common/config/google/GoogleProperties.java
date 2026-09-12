package com.swimming.backend.common.config.google;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 구글에서 받아 쓰는 것들의 설정.
 *
 * <p>로그인 검증과 영상 정보 수집은 쓰는 도메인이 다르지만 자격증명의 출처가 같다.
 * 발급처가 하나면 설정도 한자리에 둔다. 값은 {@code google.yml} 하나에만 적고, 프로필이
 * {@code spring.config.import}로 끌어다 쓴다.
 *
 * @param auth    구글 로그인으로 받은 ID 토큰을 검증하는 데 쓴다
 * @param youtube YouTube Data API v3로 영상 정보를 받아오는 데 쓴다
 */
@Validated
@ConfigurationProperties("app.google")
public record GoogleProperties(

        @Valid @NotNull Auth auth,
        @Valid @NotNull Youtube youtube
) {

    /**
     * @param clientId 구글 클라우드 콘솔에서 발급한 OAuth 클라이언트 ID.
     *                 ID 토큰의 {@code aud}가 이 값인지 확인한다.
     */
    public record Auth(@NotBlank String clientId) {
    }

    /**
     * 키가 있으면 유튜브 전용 수집기를 쓰고, 없으면 유튜브 링크도 일반 웹 수집으로 간다.
     *
     * <p>켜고 끄는 스위치를 따로 두지 않는다. 키 없이 켜는 조합은 링크를 저장할 때마다
     * 인증 오류를 내는 쓸모없는 상태고, 스위치가 있으면 키를 넣지 않은 환경이 그 상태로
     * 기동하려다 실패한다. 켜는 조건은 키 하나로 충분하다.
     *
     * @param apiKey  구글 클라우드 콘솔에서 발급한 YouTube Data API v3 키
     * @param timeout 영상 정보 요청 제한 시간
     */
    public record Youtube(
            String apiKey,
            @NotNull Duration timeout
    ) {
    }
}
