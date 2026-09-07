package com.swimming.backend.knowledge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 웹 문서 수집 정책.
 *
 * @param concurrency      동시에 여는 요청 수. 사용자가 한 번에 여러 URL을 저장할 때
 *                         상대 서버를 몰아치지 않도록 제한한다.
 * @param timeout          연결과 읽기를 합친 요청 제한 시간
 * @param maxBodyBytes     내려받을 응답 본문의 상한. HTML 한 장이 이보다 크면 잘린다.
 * @param maxContentLength 마크다운으로 변환한 뒤의 글자 수 상한. LLM 입력 비용을 막는다.
 * @param minMeaningfulLength 링크를 뺀 실제 문장이 이보다 짧으면 본문 추출에 실패한 것으로 보고
 *                           메타데이터(og:description)로 대체한다. 실측에서 로그인 벽 페이지가
 *                           183~271자, 가장 짧은 정상 문서가 2,154자였다.
 * @param userAgent        요청에 밝히는 신원
 * @param render           JS 렌더링 폴백 설정
 */
@Validated
@ConfigurationProperties("app.knowledge.fetch")
public record KnowledgeFetchProperties(

        @NotNull @Min(1) @Max(16) Integer concurrency,
        @NotNull Duration timeout,
        @NotNull @Min(64 * 1024) Integer maxBodyBytes,
        @NotNull @Min(1000) Integer maxContentLength,
        @NotNull @Min(1) Integer minMeaningfulLength,
        @NotBlank String userAgent,
        @Valid @NotNull Render render
) {

    /**
     * jsoup은 JavaScript를 실행하지 않아 클라이언트 렌더링 페이지에서 빈 껍데기를 받는다.
     * 그런 경우에만 헤드리스 브라우저로 다시 받는다.
     *
     * @param enabled          폴백 사용 여부. 꺼두면 브라우저를 아예 띄우지 않는다.
     * @param timeout          렌더링 제한 시간
     * @param minContentLength 마크다운이 이 길이 미만이면 렌더링에 실패한 것으로 보고 다시 받는다.
     *                         측정값 기준으로 렌더링이 필요했던 페이지가 74자와 322자,
     *                         정상이지만 짧았던 페이지가 3,750자였다.
     */
    public record Render(
            boolean enabled,
            @NotNull Duration timeout,
            @NotNull @Min(1) Integer minContentLength
    ) {
    }
}
