package com.swimming.backend.calendar.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.domain.DailyPlanItem;
import com.swimming.backend.calendar.dto.in.CreateDailyPlanItemsRequest;
import com.swimming.backend.calendar.dto.in.NewDailyPlanTask;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskMatrixSection;
import com.swimming.backend.task.dto.in.NewTaskSpec;
import com.swimming.backend.calendar.dto.in.DailyPlanItemResponse;
import com.swimming.backend.calendar.dto.in.DailyPlanResponse;
import com.swimming.backend.calendar.dto.projection.DailyPlanItemQueryRow;
import com.swimming.backend.calendar.service.DailyPlanService;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyPlanUseCase {
    private final DailyPlanService dailyPlanService;
    private final TaskService taskService;
    private final TaskOrderingService taskOrderingService;
    private final FolderService folderService;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<DailyPlanResponse> getRange(Long userId, LocalDate fromDate, LocalDate toDate) {
        Map<LocalDate, List<DailyPlanItemQueryRow>> rowsByDate = dailyPlanService.getRows(userId, fromDate, toDate)
                .stream()
                .collect(Collectors.groupingBy(DailyPlanItemQueryRow::planDate));

        List<DailyPlanResponse> responses = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            List<DailyPlanItemResponse> items = rowsByDate.getOrDefault(date, List.of())
                    .stream()
                    .map(DailyPlanItemResponse::from)
                    .toList();
            responses.add(new DailyPlanResponse(date, items));
        }
        return responses;
    }

    /**
     * 날짜에 할 일을 담는다. 이미 있는 할 일(taskIds)이거나 새로 만들 할 일(tasks)이고, 둘 중 하나만 온다.
     * 모달의 "모두 추가" 한 번이라 한 트랜잭션에서 끝낸다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public DailyPlanResponse addItems(Long userId, LocalDate date, CreateDailyPlanItemsRequest request) {
        List<Long> taskIds = request.taskIds();
        List<NewDailyPlanTask> drafts = request.tasks();
        boolean linksExistingTasks = taskIds != null && !taskIds.isEmpty() && drafts == null;
        boolean createsTasks = drafts != null && !drafts.isEmpty() && taskIds == null;
        if (linksExistingTasks == createsTasks) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_ITEM);
        }

        List<Long> plannedTaskIds = linksExistingTasks
                ? linkExistingTasks(userId, date, taskIds)
                : createTasks(userId, drafts);

        dailyPlanService.saveAll(userId, date, plannedTaskIds.stream()
                .map(DailyPlanItem::createTask)
                .toList());
        return loadPlanResponse(userId, date);
    }

    /** 이미 있는 할 일을 담는다. 같은 날짜에 같은 할 일을 두 번 담을 수 없다. */
    private List<Long> linkExistingTasks(Long userId, LocalDate date, List<Long> taskIds) {
        if (new HashSet<>(taskIds).size() != taskIds.size()
                || dailyPlanService.containsAnyTasks(userId, date, taskIds)) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_TASKS);
        }
        if (taskService.getReferences(userId, taskIds).size() != new HashSet<>(taskIds).size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return taskIds;
    }

    /**
     * 새 할 일을 만들고 그 id를 담은 순서대로 돌려준다.
     * 폴더 소유권은 한 번에 확인하고, 매트릭스 순위는 영역마다 한 번만 읽는다.
     */
    private List<Long> createTasks(Long userId, List<NewDailyPlanTask> drafts) {
        folderService.validateOwnerships(userId, drafts.stream()
                .map(NewDailyPlanTask::folderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        List<Long> ranks = taskOrderingService.nextRanks(userId, drafts.stream()
                .map(draft -> TaskMatrixSection.from(draft.priority(), draft.urgent()))
                .toList());

        List<NewTaskSpec> specs = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            NewDailyPlanTask draft = drafts.get(index);
            specs.add(new NewTaskSpec(
                    draft.folderId(), draft.title().trim(), draft.priority(), draft.urgent(), ranks.get(index)));
        }
        return taskService.createAll(userId, specs).stream().map(Task::getId).toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteItem(Long userId, LocalDate date, Long itemId) {
        dailyPlanService.delete(userId, date, itemId);
    }

    private DailyPlanResponse loadPlanResponse(Long userId, LocalDate date) {
        List<DailyPlanItemResponse> items = dailyPlanService.getRows(userId, date, date)
                .stream()
                .map(DailyPlanItemResponse::from)
                .toList();
        return new DailyPlanResponse(date, items);
    }
}
