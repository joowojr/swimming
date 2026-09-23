package com.swimming.backend.agentwork.interfaces.api.swimming;

import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.application.port.LinkedSource;
import com.swimming.backend.agentwork.application.port.WorkItem;
import com.swimming.backend.agentwork.application.port.WorkItemId;
import com.swimming.backend.agentwork.application.port.WorkItemReader;
import com.swimming.backend.task.dto.projection.TaskSummaryRow;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.knowledge.domain.KnowledgeSource;
import com.swimming.backend.knowledge.service.data.KnowledgeSourceService;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SwimmingTaskWorkItemReader implements WorkItemReader {
    private final TaskService taskService;
    private final TaskSourceService taskSourceService;
    private final KnowledgeSourceService knowledgeSourceService;

    @Override
    public List<WorkItem> readAllOwned(Long userId) {
        return taskService.getActiveSummaries(userId).stream().map(this::toWorkItem).toList();
    }

    @Override
    public Map<WorkItemId, WorkItem> readAll(Long userId, Collection<WorkItemId> ids) {
        Map<WorkItemId, Long> taskIds = new LinkedHashMap<>();
        for (WorkItemId id : ids) {
            if (id.type() != WorkResourceType.SWIMMING_TASK) {
                continue;
            }
            try {
                long taskId = Long.parseLong(id.id());
                if (taskId > 0) {
                    taskIds.put(id, taskId);
                }
            } catch (NumberFormatException ignored) {
                // Swimming Task id는 양수 bigint다. 잘못된 식별자는 조회 결과에서 제외한다.
            }
        }
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = taskIds.values().stream().distinct().toList();
        Map<Long, TaskSummaryRow> tasks = taskService.getActiveSummaries(userId, distinctIds).stream()
                .collect(Collectors.toMap(TaskSummaryRow::id, Function.identity()));
        Map<WorkItemId, WorkItem> result = new LinkedHashMap<>();
        taskIds.forEach((requestedId, taskId) -> {
            TaskSummaryRow task = tasks.get(taskId);
            if (task != null) {
                result.put(requestedId, toWorkItem(task));
            }
        });
        return result;
    }

    @Override
    public List<LinkedSource> readLinkedSources(Long userId, WorkItemId id) {
        // 소유하지 않은 Task의 연결은 읽지 않는다. 식별자 정규화는 readAll을 따른다.
        WorkItem task = readAll(userId, List.of(id)).get(id);
        if (task == null) {
            return List.of();
        }
        List<UUID> sourceIds = taskSourceService.getSourceIds(Long.parseLong(task.id()));
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, KnowledgeSource> sources = knowledgeSourceService.getOwnedAll(userId, sourceIds).stream()
                .collect(Collectors.toMap(KnowledgeSource::getId, Function.identity()));
        return sourceIds.stream().map(sources::get).filter(Objects::nonNull)
                // 제목의 주인은 SOURCE 노드다.
                .map(source -> new LinkedSource(source.getId(), source.getNode().getTitle(), source.getUrl(),
                        source.getSummary()))
                .toList();
    }

    private WorkItem toWorkItem(TaskSummaryRow task) {
        return WorkItem.builder().type(WorkResourceType.SWIMMING_TASK).id(task.id().toString())
                .title(task.title()).containerId(task.folderId()).containerName(task.folderName())
                .status(toBoardStatus(task.status())).important(task.priority()).urgent(task.urgent())
                .createdAt(task.createdAt()).build();
    }

    private int toBoardStatus(TaskStatus status) {
        return switch (status) {
            case DONE -> 1;
            case HOLD -> 2;
            default -> 0;
        };
    }
}
