package com.swimming.backend.knowledge.dto.in;

import com.swimming.backend.knowledge.dto.out.SourceFetchResult;

/**
 * 링크마다 어떻게 됐는지 그대로 돌려준다. 하나가 실패해도 나머지는 저장된다.
 */
public record SourceCollectResponse(java.util.List<Item> items) {

    /** 링크를 어떻게 처리했는가. 그 문서의 소화가 어디까지 갔는지는 {@link SourceResponse#status()}다. */
    public enum Result {

        /** 새로 저장했다. */
        CREATED,

        /** 같은 문서가 이미 있어 저장하지 않았다. */
        ALREADY_SAVED,

        /** 링크를 가져오지 못했다. */
        FAILED
    }

    /**
     * @param source 가져오기에 성공했을 때만 채운다
     * @param reason 실패했을 때만 채운다
     */
    public record Item(
            String url,
            Result result,
            SourceResponse source,
            SourceFetchResult.Failure reason
    ) {

        public static Item created(String url, SourceResponse source) {
            return new Item(url, Result.CREATED, source, null);
        }

        public static Item alreadySaved(String url, SourceResponse source) {
            return new Item(url, Result.ALREADY_SAVED, source, null);
        }

        public static Item failed(String url, SourceFetchResult.Failure reason) {
            return new Item(url, Result.FAILED, null, reason);
        }
    }
}
