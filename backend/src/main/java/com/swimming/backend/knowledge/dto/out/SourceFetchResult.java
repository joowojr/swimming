package com.swimming.backend.knowledge.dto.out;

/**
 * URL 하나의 수집 결과.
 *
 * <p>한 URL이 실패해도 나머지는 계속 진행한다. 실패를 예외로 던지지 않고 결과로 돌려주는
 * 이유이며, "Source 저장은 AI 처리 성공 여부와 독립적이다"라는 적재 원칙과 같은 맥락이다.
 */
public record SourceFetchResult(
        String requestedUrl,
        FetchedDocument document,
        Failure failure,
        String failureDetail
) {

    public enum Failure {

        /** URL 형식이 아니거나 http/https가 아니다. */
        INVALID_URL,

        /** 내부망·루프백 등 서버가 접근하면 안 되는 주소다. */
        BLOCKED_ADDRESS,

        /** 응답이 HTML이 아니다. */
        UNSUPPORTED_CONTENT_TYPE,

        /** 4xx / 5xx 응답. */
        HTTP_ERROR,

        /** 제한 시간을 넘겼다. */
        TIMEOUT,

        /** 본문을 추출했지만 남는 내용이 없다. */
        EMPTY_CONTENT,

        /** 그 밖의 오류. */
        UNKNOWN
    }

    public static SourceFetchResult success(String requestedUrl, FetchedDocument document) {
        return new SourceFetchResult(requestedUrl, document, null, null);
    }

    public static SourceFetchResult failure(
            String requestedUrl,
            Failure failure,
            String failureDetail
    ) {
        return new SourceFetchResult(requestedUrl, null, failure, failureDetail);
    }

    public boolean isSuccess() {
        return document != null;
    }
}
