package com.swimming.backend.task.service;

import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.repository.TaskRepository;
import com.swimming.backend.task.repository.entity.TaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 여러 건을 한 번에 만들 때의 매트릭스 순위 채번. */
class TaskOrderingBatchRankTest {

    private TaskRepository taskRepository;
    private TaskOrderingService taskOrderingService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        taskOrderingService = new TaskOrderingService(taskRepository);
    }

    @Test
    @DisplayName("같은 영역은 현재 순위를 한 번만 읽고 1024씩 올린다")
    void readsOncePerSection() {
        when(taskRepository.findTopByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
                eq(1L), anyBoolean(), anyBoolean())).thenReturn(Optional.empty());

        List<Long> ranks = taskOrderingService.nextRanks(1L, List.of(
                TaskMatrixSection.STANDARD,
                TaskMatrixSection.STANDARD,
                TaskMatrixSection.STANDARD));

        assertThat(ranks).containsExactly(1024L, 2048L, 3072L);
        verify(taskRepository, times(1))
                .findTopByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
                        1L, false, false);
    }

    @Test
    @DisplayName("영역이 다르면 각자 자기 영역의 순위를 이어받는다")
    void countsSectionsSeparately() {
        TaskEntity standardTop = mock(TaskEntity.class);
        when(standardTop.getMatrixRank()).thenReturn(5120L);
        when(taskRepository.findTopByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
                1L, false, false)).thenReturn(Optional.of(standardTop));
        when(taskRepository.findTopByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
                1L, true, false)).thenReturn(Optional.empty());

        List<Long> ranks = taskOrderingService.nextRanks(1L, List.of(
                TaskMatrixSection.STANDARD,
                TaskMatrixSection.PRIORITY,
                TaskMatrixSection.STANDARD));

        assertThat(ranks).containsExactly(6144L, 1024L, 7168L);
    }

    @Test
    @DisplayName("담을 것이 없으면 조회하지 않는다")
    void readsNothingForEmptyInput() {
        assertThat(taskOrderingService.nextRanks(1L, List.of())).isEmpty();
        verify(taskRepository, times(0))
                .findTopByUser_IdAndDeletedFalseAndPriorityAndUrgentOrderByMatrixRankDescIdDesc(
                        eq(1L), anyBoolean(), anyBoolean());
    }
}
