package com.swimming.backend.knowledge.dto.out;

import java.time.Instant;

/**
 * 수집·변환이 끝난 문서 하나.
 *
 * @param url          리다이렉트를 따라간 최종 URL
 * @param canonicalUrl 중복 판정 기준이 되는 URL
 * @param markdown     본문만 남겨 마크다운으로 바꾼 내용
 * @param truncated    길이 상한에 걸려 뒤가 잘렸는지
 */
public record FetchedDocument(
        String url,
        String canonicalUrl,
        String title,
        String author,
        Instant publishedAt,
        String sourceType,
        String markdown,
        boolean truncated
) {
}
