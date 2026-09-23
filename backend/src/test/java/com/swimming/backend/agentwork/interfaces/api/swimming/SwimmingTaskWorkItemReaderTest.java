package com.swimming.backend.agentwork.interfaces.api.swimming;

import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.application.port.LinkedSource;
import com.swimming.backend.agentwork.application.port.WorkItemId;
import com.swimming.backend.knowledge.domain.KnowledgeNode;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.task.dto.projection.TaskSummaryRow;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskSourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SwimmingTaskWorkItemReaderTest {
    private final TaskService taskService = mock(TaskService.class);
    private final TaskSourceService taskSourceService = mock(TaskSourceService.class);
    private final KnowledgeSourceService knowledgeSourceService = mock(KnowledgeSourceService.class);
    private final SwimmingTaskWorkItemReader reader =
            new SwimmingTaskWorkItemReader(taskService, taskSourceService, knowledgeSourceService);

    @Test
    @DisplayName("Task 표시 정보를 배치 조회하고 리소스 식별자를 정규화한다")
    void readsOwnedTasksInBatch() {
        when(taskService.getActiveSummaries(1L, List.of(7L, 8L)))
                .thenReturn(List.of(new TaskSummaryRow(7L, 3L, "업무", "구현", com.swimming.backend.task.domain.TaskStatus.TODO,
                        true, false, java.time.Instant.parse("2026-09-18T00:00:00Z"))));
        WorkItemId first = id("007");
        var result = reader.readAll(1L, List.of(first, id("7"), id("8")));
        assertThat(result).containsOnlyKeys(first, id("7"));
        assertThat(result.get(first).id()).isEqualTo("7");
        assertThat(result.get(first).containerName()).isEqualTo("업무");
        assertThat(result.get(first).important()).isTrue();
        verify(taskService).getActiveSummaries(1L, List.of(7L, 8L));
    }

    @Test
    @DisplayName("존재하지 않거나 타인·삭제된 Task는 조회 결과에서 제외한다")
    void excludesUnavailableTasks() {
        when(taskService.getActiveSummaries(1L, List.of(7L))).thenReturn(List.of());
        assertThat(reader.readAll(1L, List.of(id("7")))).isEmpty();
    }

    @Test
    @DisplayName("잘못된 Task 식별자와 빈 목록은 DB 조회 없이 제외한다")
    void excludesInvalidIds() {
        assertThat(reader.readAll(1L, List.of(id("abc"), id("0"), id("-1"), id("9223372036854775808"))))
                .isEmpty();
        assertThat(reader.readAll(1L, List.of())).isEmpty();
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("연결된 지식을 붙인 순서대로 돌려주고 지워졌거나 타인의 문서는 뺀다")
    void readsLinkedSourcesInOrder() {
        UUID first = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(taskService.getActiveSummaries(1L, List.of(7L))).thenReturn(List.of(task(7L)));
        when(taskSourceService.getSourceIds(7L)).thenReturn(List.of(second, deleted, first));
        List<KnowledgeSource> owned = List.of(source(first, "첫 문서"), source(second, "둘째 문서"));
        when(knowledgeSourceService.getOwnedAll(1L, List.of(second, deleted, first))).thenReturn(owned);

        assertThat(reader.readLinkedSources(1L, id("7"))).extracting(LinkedSource::id, LinkedSource::title)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(second, "둘째 문서"),
                        org.assertj.core.groups.Tuple.tuple(first, "첫 문서"));
    }

    @Test
    @DisplayName("소유하지 않은 Task의 연결 지식은 조회하지 않는다")
    void skipsLinkedSourcesOfUnownedTask() {
        when(taskService.getActiveSummaries(1L, List.of(7L))).thenReturn(List.of());

        assertThat(reader.readLinkedSources(1L, id("7"))).isEmpty();
        verifyNoInteractions(taskSourceService, knowledgeSourceService);
    }

    private TaskSummaryRow task(Long id) {
        return new TaskSummaryRow(id, 3L, "업무", "구현", com.swimming.backend.task.domain.TaskStatus.TODO,
                false, false, java.time.Instant.parse("2026-09-18T00:00:00Z"));
    }

    private KnowledgeSource source(UUID id, String title) {
        KnowledgeNode node = mock(KnowledgeNode.class);
        when(node.getTitle()).thenReturn(title);
        KnowledgeSource source = mock(KnowledgeSource.class);
        when(source.getId()).thenReturn(id);
        when(source.getNode()).thenReturn(node);
        when(source.getUrl()).thenReturn("https://example.com/" + id);
        when(source.getSummary()).thenReturn("요약");
        return source;
    }

    private WorkItemId id(String value) {
        return WorkItemId.builder().type(WorkResourceType.SWIMMING_TASK).id(value).build();
    }
}
