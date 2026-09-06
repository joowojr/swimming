package com.swimming.backend.common.dto;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;

import java.util.List;
import java.util.function.Function;

/**
 * 커서로 이어 읽는 목록의 응답 모양.
 *
 * <p>목록 API가 늘어날 때마다 봉투가 조금씩 달라지지 않도록 한 자리에 모은다. 모양은 먼저
 * 만들어진 Task Matrix 조회를 따른다.
 *
 * @param nextCursor 다음 페이지가 없으면 null
 * @param hasNext    {@code nextCursor != null}과 같다. 클라이언트가 커서의 존재로
 *                   유추하지 않아도 되게 함께 준다
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    /**
     * 한 건 더 읽어 온 결과를 페이지로 자른다. 총 개수를 세지 않아도 다음 장이 있는지 알 수
     * 있어, 목록 API마다 같은 판단을 다시 짜지 않게 한다.
     *
     * @param fetched  {@code size + 1}개를 요청해 받은 결과
     * @param toItem   응답 항목으로 바꾼다
     * @param cursorOf 페이지의 마지막 항목에서 다음 커서를 만든다
     */
    public static <S, T> CursorPage<T> of(
            List<S> fetched,
            int size,
            Function<S, T> toItem,
            Function<S, String> cursorOf
    ) {
        boolean hasNext = fetched.size() > size;
        List<S> page = hasNext ? fetched.subList(0, size) : fetched;

        return new CursorPage<>(
                page.stream().map(toItem).toList(),
                hasNext ? cursorOf.apply(page.getLast()) : null,
                hasNext
        );
    }

    /** 목록 API가 받은 개수 상한을 같은 기준으로 검사한다. */
    public static int validateSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }

        return size;
    }
}
