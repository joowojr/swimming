package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.dto.in.SourceDetailResponse;
import com.swimming.backend.knowledge.service.KnowledgeSourceService;
import com.swimming.backend.knowledge.service.SourceConceptReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Source 하나를 원문 정보까지 붙여 돌려준다.
 *
 * <p>개념과 목적은 목록과 같은 곳에서 읽는다. Source에 붙은 관계를 읽는 규칙이 두 벌로
 * 갈라지면 상세와 목록이 다른 개념을 보여 줄 수 있다.
 */
@Service
@RequiredArgsConstructor
public class SourceDetailUseCase {

    private final KnowledgeSourceService sourceService;
    private final SourceConceptReader conceptReader;

    public SourceDetailResponse get(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);

        return SourceDetailResponse.of(source, conceptReader.read(source));
    }
}
