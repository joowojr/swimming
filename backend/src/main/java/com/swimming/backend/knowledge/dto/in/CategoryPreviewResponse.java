package com.swimming.backend.knowledge.dto.in;

import java.util.List;
import java.util.UUID;

/**
 * 사용자가 검토할 묶음 초안.
 *
 * <p>아직 노드도 관계도 저장하지 않았으므로 묶음에 id가 없다. 사용자가 이름을 고치고
 * Source를 옮긴 뒤 PUT으로 보내야 그때 노드가 생긴다.
 *
 * <p>나눌 만한 묶음이 없으면 {@code categories}가 빈 채로 돌아온다. 억지로 나눈 결과보다
 * 아직 나눌 때가 아니라는 답이 정직하다.
 */
public record CategoryPreviewResponse(List<Category> categories) {

    /**
     * @param sourceIds 최소 1개. 문서 하나뿐인 묶음도 유효하다
     */
    public record Category(String title, List<UUID> sourceIds) {
    }
}
