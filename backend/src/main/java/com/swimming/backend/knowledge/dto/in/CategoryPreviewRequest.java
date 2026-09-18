package com.swimming.backend.knowledge.dto.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 어떤 링크를 묶을지 정해 보낸다.
 *
 * <p>Folder 전체를 서버가 알아서 읽지 않는다. 그러면 사용자가 화면에서 보고 고른 범위와
 * 모델이 본 범위가 어긋나고, 링크가 많은 폴더에서 일부만 정리하려는 의사를 전할 길이 없다.
 *
 * <p>개수는 여기서 거른다. 6개 미만이면 나눌 것이 없어 문서 하나짜리 묶음만 나오고,
 * 상한은 한 번에 보내는 LLM 입력이 무한정 커지지 않게 막는다. 세는 쿼리를 따로 두지 않고
 * Bean Validation으로 끝낸다.
 */
public record CategoryPreviewRequest(

        @NotNull
        @Size(min = 6, max = 50)
        List<UUID> sourceIds
) {
}
