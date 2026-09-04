package com.swimming.backend.task.usecase;

import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.domain.TaskOrderingScope;
import com.swimming.backend.task.domain.TaskPlacement;
import com.swimming.backend.task.domain.TaskPlacementChange;
import com.swimming.backend.task.dto.in.TaskMatrixPageQuery;
import com.swimming.backend.task.dto.in.TaskPlacementRequest;
import com.swimming.backend.task.dto.TaskPlacementResult;
import com.swimming.backend.task.dto.out.TaskMatrixItemResponse;
import com.swimming.backend.task.dto.out.TaskMatrixPageResponse;
import com.swimming.backend.task.dto.out.TaskPlacementResponse;
import com.swimming.backend.task.service.TaskMatrixCursorCodec;
import com.swimming.backend.task.service.TaskOrderingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskOrderingUseCase {

    private final TaskOrderingService taskOrderingService;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public TaskMatrixPageResponse getMatrixPage(Long userId, TaskMatrixPageQuery query) {
        TaskMatrixCursorCodec.DecodedCursor cursor = query.cursor() == null
                ? null
                : TaskMatrixCursorCodec.decode(query.cursor(), query.section());
        List<Task> fetched = taskOrderingService.getMatrixPage(
                userId,
                query.section(),
                cursor == null ? null : cursor.matrixRank(),
                cursor == null ? null : cursor.taskId(),
                query.status(),
                query.size() + 1
        );
        boolean hasNext = fetched.size() > query.size();
        List<Task> page = hasNext ? fetched.subList(0, query.size()) : fetched;
        List<TaskMatrixItemResponse> items = page.stream()
                .map(task -> TaskMatrixItemResponse.from(
                        task,
                        TaskMatrixCursorCodec.encode(query.section(), task.getMatrixRank(), task.getId())
                ))
                .toList();
        String nextCursor = hasNext
                ? items.get(items.size() - 1).positionCursor()
                : null;
        return new TaskMatrixPageResponse(query.section(), items, nextCursor, hasNext);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskPlacementResponse move(Long userId, Long taskId, TaskPlacementRequest request) {
        TaskOrderingScope scope = TaskOrderingScope.fromValue(request.scope());
        TaskPlacementResult result = switch (scope) {
            case MATRIX -> {
                TaskMatrixSection targetSection = TaskMatrixSection.fromQuery(request.targetSection());
                TaskPlacement placement = taskOrderingService.preparePlacement(
                        userId,
                        taskId,
                        targetSection,
                        request.previousTaskId(),
                        request.nextTaskId()
                );
                TaskPlacementChange change = placement.move();
                yield taskOrderingService.applyPlacement(userId, change);
            }
        };
        Task task = result.task();
        String positionCursor = TaskMatrixCursorCodec.encode(
                result.targetSection(),
                task.getMatrixRank(),
                task.getId()
        );
        return new TaskPlacementResponse(
                scope,
                TaskMatrixItemResponse.from(task, positionCursor),
                result.sourceSection(),
                result.targetSection(),
                result.rebalanced() ? List.of(result.targetSection()) : List.of()
        );
    }
}
