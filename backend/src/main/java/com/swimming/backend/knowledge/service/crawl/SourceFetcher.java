package com.swimming.backend.knowledge.service.crawl;

import com.swimming.backend.knowledge.dto.out.SourceFetchResult;

import java.net.URI;

/**
 * URL 하나를 문서로 만드는 한 가지 방법.
 *
 * <p>링크마다 본문이 있는 자리가 다르다. 일반 문서는 HTML 안에 있지만 유튜브는 시청
 * 페이지를 긁어봐야 플레이어 껍데기뿐이고 제목과 설명은 API로 받아야 한다. 수집 방식이
 * 갈리는 지점을 구현체로 나누고, 고르는 일은 {@link SourceFetchDispatcher}가 맡는다.
 *
 * <p>실패를 예외로 던지지 않는다. 한 링크의 실패가 나머지를 막지 않아야 한다는 수집
 * 원칙이 구현체에도 그대로 적용된다.
 */
public interface SourceFetcher {

    /**
     * 이 URL을 자신이 맡을지 판단한다.
     *
     * <p>호스트만 보고 참을 돌려주면 안 된다. 유튜브 도메인이라도 영상 URL이 아니면
     * 영상 API로 할 수 있는 일이 없다. 맡을 수 없는 링크는 여기서 걸러 일반 수집으로
     * 흘려보낸다.
     */
    boolean supports(URI uri);

    /**
     * @param requestedUrl 사용자가 입력한 원본 문자열. 결과를 요청과 짝지을 때 쓴다.
     * @param uri          {@code requestedUrl}을 파싱한 값
     */
    SourceFetchResult fetch(String requestedUrl, URI uri);
}
