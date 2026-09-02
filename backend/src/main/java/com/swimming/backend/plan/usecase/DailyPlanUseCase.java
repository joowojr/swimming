package com.swimming.backend.plan.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.dto.CreateDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.DailyPlanItemResponse;
import com.swimming.backend.plan.dto.DailyPlanItemType;
import com.swimming.backend.plan.dto.DailyPlanResponse;
import com.swimming.backend.plan.dto.ReorderDailyPlanItemsRequest;
import com.swimming.backend.plan.dto.projection.DailyPlanItemQueryRow;
import com.swimming.backend.plan.service.DailyPlanService;
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
                    .map(this::toItemResponse)
                    .toList();
            responses.add(new DailyPlanResponse(date, items));
        }
        return responses;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DailyPlanResponse addItems(Long userId, LocalDate date, CreateDailyPlanItemsRequest request) {
        String title = request.title() == null ? null : request.title().trim();
        List<Long> taskIds = request.taskIds();
        boolean linksExistingTasks = taskIds != null && !taskIds.isEmpty()
                && request.folderId() == null && (title == null || title.isEmpty());
        boolean createsTask = taskIds == null && title != null && !title.isEmpty();
        if (!linksExistingTasks && !createsTask) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_ITEM);
        }

        int nextOrderIdx = dailyPlanService.getItems(userId, date).size();
        if (linksExistingTasks) {
            if (new HashSet<>(taskIds).size() != taskIds.size()
                    || dailyPlanService.containsAnyTasks(userId, date, taskIds)) {
                throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_TASKS);
            }
            if (taskService.getReferences(userId, taskIds).size() != new HashSet<>(taskIds).size()) {
                throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
            }
            List<DailyPlanItem> items = new ArrayList<>();
            for (Long taskId : taskIds) {
                items.add(DailyPlanItem.restore(null, taskId, nextOrderIdx++, null, null));
            }
            dailyPlanService.saveAll(userId, date, items);
        } else {
            Long folderId = request.folderId() == null
                    ? null
                    : folderService.getReference(userId, request.folderId()).id();
            long matrixRank = taskOrderingService.nextRank(userId, false, false);
            Long createdTaskId = taskService.create(
                    userId, folderId, title, false, false, matrixRank).getId();
            dailyPlanService.save(userId, date, DailyPlanItem.restore(null, createdTaskId, nextOrderIdx, null, null));
        }
        return loadPlanResponse(userId, date);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DailyPlanResponse reorder(Long userId, LocalDate date, ReorderDailyPlanItemsRequest request) {
        List<Long> currentItemIds = dailyPlanService.getItems(userId, date).stream()
                .map(DailyPlanItem::getId)
                .toList();
        List<Long> requestedItemIds = request.itemIds();
        if (currentItemIds.size() != requestedItemIds.size()
                || !new HashSet<>(currentItemIds).equals(new HashSet<>(requestedItemIds))) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_ITEM_ORDER);
        }

        Map<Long, Integer> orderIdxByItemId = new HashMap<>();
        for (int orderIdx = 0; orderIdx < requestedItemIds.size(); orderIdx++) {
            orderIdxByItemId.put(requestedItemIds.get(orderIdx), orderIdx);
        }
        dailyPlanService.reorder(userId, date, orderIdxByItemId);
        return loadPlanResponse(userId, date);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteItem(Long userId, LocalDate date, Long itemId) {
        dailyPlanService.delete(userId, date, itemId);
    }

    private DailyPlanResponse loadPlanResponse(Long userId, LocalDate date) {
        List<DailyPlanItemResponse> items = dailyPlanService.getRows(userId, date, date)
                .stream()
                .map(this::toItemResponse)
                .toList();
        return new DailyPlanResponse(date, items);
    }

    private DailyPlanItemResponse toItemResponse(DailyPlanItemQueryRow row) {
        return new DailyPlanItemResponse(
                row.id(),
                row.taskId(),
                (row.folderId() == null || row.folderIsDeleted())? DailyPlanItemType.AD_HOC : DailyPlanItemType.TASK,
                row.folderId(),
                row.folderName(),
                row.title(),
                row.status(),
                row.priority(),
                row.urgent(),
                row.orderIdx()
        );
    }
}
