package com.swimming.backend.knowledge.usecase;

import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 저장한 링크를 읽었는지 표시한다.
 *
 * <p>읽은 시각은 서버 시계에서 뽑는다. 클라이언트가 보낸 시각을 믿으면 기기 시계가 어긋난
 * 만큼 "이번 주에 읽은 링크"를 세는 집계가 흔들린다.
 */
@Service
@RequiredArgsConstructor
public class SourceReadUseCase {

    private final KnowledgeSourceService sourceService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRED)
    public void markRead(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);

        sourceService.markRead(source, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void markUnread(Long userId, UUID sourceId) {
        KnowledgeSource source = sourceService.getOwned(sourceId, userId);

        sourceService.markUnread(source);
    }
}
