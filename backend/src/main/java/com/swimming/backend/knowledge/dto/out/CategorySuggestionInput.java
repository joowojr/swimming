package com.swimming.backend.knowledge.dto.out;

import java.util.List;
import java.util.UUID;

/**
 * Category 초안을 만들 때 모델에게 주는 것.
 *
 * <p>원문은 담지 않는다. 이미 소화해 둔 요약과 개념이면 묶는 판단에는 충분하고, 문서
 * 수십 개의 본문을 한 요청에 실으면 입력이 감당할 수 없게 커진다.
 *
 * <p>모델에게는 Source를 UUID가 아니라 <b>1부터 시작하는 번호</b>로 가리키게 한다. UUID를
 * 받아 적게 하면 한 글자만 틀려도 어느 문서인지 알 수 없는데, 그 위험을 감수할 이유가 없다.
 * 번호는 {@code items}에서의 자리이므로 따로 들고 있지 않는다. {@code sourceId}는
 * 직렬화에서 빠지고, 돌아온 번호를 문서로 되돌리는 데만 쓴다.
 */
public record CategorySuggestionInput(String folderName, List<Item> items) {

    /**
     * @param topic    소화가 끝난 Source면 반드시 있다
     * @param subjects 최대 5개
     */
    public record Item(
            UUID sourceId,
            String title,
            String summary,
            String topic,
            List<String> subjects
    ) {
    }

    /**
     * 모델이 돌려준 번호를 문서로 되돌린다.
     *
     * @return 범위를 벗어난 번호면 비어 있다. 모델이 없는 문서를 지어낸 것이다
     */
    public UUID sourceIdAt(Integer index) {
        if (index == null || index < 1 || index > items.size()) {
            return null;
        }

        return items.get(index - 1).sourceId();
    }
}
