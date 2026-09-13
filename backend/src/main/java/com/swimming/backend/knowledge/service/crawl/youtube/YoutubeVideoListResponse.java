package com.swimming.backend.knowledge.service.crawl.youtube;

import java.time.Instant;
import java.util.List;

/**
 * YouTube Data API v3 {@code videos.list} 응답 중 수집에 쓰는 부분.
 *
 * <p>응답 전체를 옮기지 않는다. 통계나 썸네일처럼 지금 쓰지 않는 값은 두지 않는다.
 *
 * @param items 요청한 영상. 삭제되었거나 비공개면 비어서 온다.
 */
record YoutubeVideoListResponse(List<Item> items) {

    record Item(Snippet snippet, ContentDetails contentDetails) {
    }

    /**
     * @param publishedAt  게시 시각
     * @param channelTitle 올린 채널 이름. 문서의 작성자로 쓴다.
     * @param tags         올린 사람이 붙인 태그. 설명이 짧은 영상에서 주제를 가늠할 단서가 된다.
     */
    record Snippet(
            String title,
            String description,
            String channelTitle,
            Instant publishedAt,
            List<String> tags
    ) {
    }

    /**
     * @param duration ISO-8601 기간 문자열(예: {@code PT12M34S})
     */
    record ContentDetails(String duration) {
    }
}
