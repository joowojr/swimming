package com.swimming.backend.task.usecase;

import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.dto.in.CreateTaskWithPlanRequest;
import com.swimming.backend.task.dto.in.CreateTasksBatchRequest;
import com.swimming.backend.task.dto.in.NewTaskSpec;
import com.swimming.backend.task.dto.in.DeleteTasksRequest;
import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.task.dto.in.TaskResponse;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.service.TaskCursorCodec;
import com.swimming.backend.task.dto.in.TaskSort;
import com.swimming.backend.task.dto.in.UpdateTaskInfoRequest;
import com.swimming.backend.task.dto.in.UpdateTaskStatusRequest;
import com.swimming.backend.task.dto.in.UpdateTaskTitleRequest;
import com.swimming.backend.task.dto.in.UpdateTaskPriorityRequest;
import com.swimming.backend.task.dto.in.UpdateTaskUrgentRequest;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TaskUseCase {

    private final TaskService taskService;
    private final TaskOrderingService taskOrderingService;
    private final FolderService folderService;

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse createWithOptionalPlan(Long userId, CreateTaskWithPlanRequest request) {
        Long folderId = request.folderId() == null
                ? null
                : folderService.getReference(userId, request.folderId()).id();
        long matrixRank = taskOrderingService.nextRank(userId, request.priority(), request.urgent());
        Task task = taskService.create(
                userId, folderId, request.title().trim(), request.priority(), request.urgent(), matrixRank);
        if (request.planDate() != null) {
            taskService.plan(userId, List.of(task.getId()), request.planDate());
            task = taskService.getOne(userId, task.getId());
        }
        return TaskResponse.from(task);
    }

    /**
     * 모달에서 담은 할 일을 한 번에 만든다. 하나라도 실패하면 아무것도 만들어지지 않는다.
     *
     * <p>단건 생성을 건수만큼 반복하지 않는다. 폴더 소유권은 한 번에 확인하고, 매트릭스 순위는
     * 섹션마다 한 번만 읽어 메모리에서 올리며, 캘린더는 날짜별로 한 번에 담는다. 순위를 건마다
     * 읽으면 쿼리가 건수만큼 늘고 값이 flush 시점에 의존하게 된다.
     *
     * <p>새 할 일은 섹션 맨 위로 가므로, 담은 순서대로 순위를 올리면 마지막에 담은 것이 맨 위에
     * 온다. 하나씩 만들었을 때와 같은 결과다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<TaskResponse> createBatch(Long userId, CreateTasksBatchRequest request) {
        List<CreateTaskWithPlanRequest> drafts = request.tasks();

        folderService.validateOwnerships(userId, drafts.stream()
                .map(CreateTaskWithPlanRequest::folderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        List<Long> ranks = taskOrderingService.nextRanks(userId, drafts.stream()
                .map(draft -> TaskMatrixSection.from(draft.priority(), draft.urgent()))
                .toList());

        List<NewTaskSpec> specs = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            CreateTaskWithPlanRequest draft = drafts.get(index);
            specs.add(new NewTaskSpec(
                    draft.folderId(), draft.title().trim(), draft.priority(), draft.urgent(), ranks.get(index)));
        }

        // 넘긴 순서 그대로 돌아오므로 요청 항목과 같은 자리에서 짝지을 수 있다.
        List<Task> created = taskService.createAll(userId, specs);

        Map<LocalDate, List<Long>> taskIdsByDate = new LinkedHashMap<>();
        for (int index = 0; index < drafts.size(); index++) {
            LocalDate planDate = drafts.get(index).planDate();
            if (planDate == null) {
                continue;
            }
            taskIdsByDate.computeIfAbsent(planDate, date -> new ArrayList<>())
                    .add(created.get(index).getId());
        }
        if (taskIdsByDate.isEmpty()) {
            return created.stream().map(TaskResponse::from).toList();
        }
        taskIdsByDate.forEach((date, taskIds) -> taskService.plan(userId, taskIds, date));

        // 캘린더 날짜까지 담긴 값으로 돌려준다. 순서는 요청 순서 그대로다.
        Map<Long, Task> plannedById = new HashMap<>();
        taskService.getAllByIds(userId, created.stream().map(Task::getId).toList())
                .forEach(task -> plannedById.put(task.getId(), task));
        return created.stream()
                .map(task -> TaskResponse.from(plannedById.get(task.getId())))
                .toList();
    }

    /**
     * 폴더에 담긴 할 일을 최근 순으로 한 페이지 읽는다.
     *
     * <p>폴더 정보와 나누어 둔다. 목록은 이어 읽으며 여러 번 부르는데, 한 응답에 묶으면
     * 다음 페이지를 받을 때마다 폴더 정보까지 다시 실려 온다.
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public CursorPage<TaskSummaryResponse> getPageByFolder(
            Long userId,
            Long folderId,
            int size,
            String cursor
    ) {
        FolderReference folder = folderService.getReference(userId, folderId);

        // 한 건 더 읽어 다음 장이 있는지 본다. 총 개수를 세지 않아도 된다.
        return CursorPage.of(
                taskService.getPageByFolder(
                        folder.id(),
                        StringUtils.hasText(cursor) ? TaskCursorCodec.decode(cursor) : null,
                        size + 1
                ),
                size,
                TaskSummaryResponse::from,
                last -> TaskCursorCodec.encode(last.getCreatedAt(), last.getId())
        );
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskResponse> getList(Long userId, TaskSort sort) {
        return taskService.getAll(userId, sort.toSort()).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updateTitle(
            Long userId,
            Long taskId,
            UpdateTaskTitleRequest request
    ) {
        return TaskResponse.from(taskService.updateTitle(userId, taskId, request.title()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updateStatus(
            Long userId,
            Long taskId,
            UpdateTaskStatusRequest request
    ) {
        return TaskResponse.from(taskService.updateStatus(userId, taskId, request.status()));
    }

    /** 수정 모달의 저장 하나를 처리한다. 제목·폴더·중요·즉시·캘린더 날짜를 한 트랜잭션에서 바꾼다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updateInfo(Long userId, Long taskId, UpdateTaskInfoRequest request) {
        Long folderId = request.folderId() == null
                ? null
                : folderService.getReference(userId, request.folderId()).id();

        Task current = taskService.getOne(userId, taskId);
        Long matrixRank = current.isPriority() != request.priority() || current.isUrgent() != request.urgent()
                ? taskOrderingService.nextRank(userId, request.priority(), request.urgent())
                : null;
        return TaskResponse.from(taskService.updateInfo(
                userId, taskId, request.title(), folderId, request.priority(), request.urgent(), matrixRank,
                request.planDate()));
    }

    /** @deprecated updateInfo의 priority를 쓴다. */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updatePriority(Long userId, Long taskId, UpdateTaskPriorityRequest request) {
        return TaskResponse.from(taskOrderingService.updatePriority(userId, taskId, request.priority()));
    }

    /** @deprecated updateInfo의 urgent를 쓴다. */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRED)
    public TaskResponse updateUrgent(Long userId, Long taskId, UpdateTaskUrgentRequest request) {
        return TaskResponse.from(taskOrderingService.updateUrgent(userId, taskId, request.urgent()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteTasks(Long userId, DeleteTasksRequest request) {
        List<Long> taskIds = request.taskIds().stream().distinct().toList();
        taskService.deleteAll(userId, taskIds);
    }

}
