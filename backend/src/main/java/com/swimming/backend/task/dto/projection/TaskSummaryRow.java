package com.swimming.backend.task.dto.projection;

import com.swimming.backend.task.domain.TaskStatus;
import java.time.Instant;

/** 소유한 활성 Task의 표시 정보. 영속 Entity 대신 도메인 간 조회에 사용한다. */
public record TaskSummaryRow(Long id, Long folderId, String folderName, String title,
                             TaskStatus status, boolean priority, boolean urgent, Instant createdAt) {}
