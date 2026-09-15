package com.swimming.backend.task.dto.in;

/**
 * 한 번에 여러 건을 만들 때 Service에 넘기는 한 건의 명세.
 *
 * <p>매트릭스 순위(matrixRank)는 UseCase가 정해서 넘긴다. 순위 채번은 TaskOrderingService의
 * 몫이고, 여러 건을 만들 때는 섹션마다 한 번만 읽어 메모리에서 올려야 하기 때문이다.
 * 폴더 안 순번(orderIdx)은 Service가 정한다.
 */
public record NewTaskSpec(
        Long folderId,
        String title,
        boolean priority,
        boolean urgent,
        long matrixRank
) {
}
