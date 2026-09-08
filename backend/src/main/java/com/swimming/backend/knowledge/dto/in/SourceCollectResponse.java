package com.swimming.backend.knowledge.dto.in;

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
     * @param failureMessage 실패했을 때만 채우는 사용자용 안내
     * @param retryable 같은 요청을 다시 시도할 수 있는지
     */
    public record Item(
            String url,
            Result result,
            SourceResponse source,
            String failureMessage,
            boolean retryable
    ) {

        public static Item created(String url, SourceResponse source) {
            return new Item(url, Result.CREATED, source, null, false);
        }

        public static Item alreadySaved(String url, SourceResponse source) {
            return new Item(url, Result.ALREADY_SAVED, source, null, false);
        }

        public static Item failed(String url, String failureMessage, boolean retryable) {
            return new Item(url, Result.FAILED, null, failureMessage, retryable);
        }
    }
}
