package com.swimming.backend.calendar.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.calendar.domain.DailyPlanItem;
import com.swimming.backend.calendar.repository.DailyPlanItemBatchRepository;
import com.swimming.backend.calendar.repository.DailyPlanItemRepository;
import com.swimming.backend.calendar.repository.entity.DailyPlanItemEntity;
import com.swimming.backend.calendar.dto.projection.DailyPlanItemQueryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DailyPlanService {
    private final DailyPlanItemRepository dailyPlanItemRepository;
    private final DailyPlanItemBatchRepository dailyPlanItemBatchRepository;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<DailyPlanItemQueryRow> getRows(Long userId, LocalDate fromDate, LocalDate toDate) {
        return dailyPlanItemRepository.findRows(userId, fromDate, toDate);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<DailyPlanItem> getItems(Long userId, LocalDate planDate) {
        return dailyPlanItemRepository.findAllByUserIdAndPlanDateOrderByOrderIdxAsc(userId, planDate)
                .stream()
                .map(DailyPlanItemEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DailyPlanItem save(Long userId, LocalDate planDate, DailyPlanItem item) {
        return dailyPlanItemRepository.save(DailyPlanItemEntity.from(userId, planDate, item)).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void saveAll(Long userId, LocalDate planDate, List<DailyPlanItem> items) {
        dailyPlanItemBatchRepository.insertAll(userId, planDate, items);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void reorder(Long userId, LocalDate planDate, Map<Long, Integer> orderIdxByItemId) {
        List<DailyPlanItemEntity> items =
                dailyPlanItemRepository.findAllByUserIdAndPlanDateOrderByOrderIdxAsc(userId, planDate);
        items.forEach(item -> {
            Integer orderIdx = orderIdxByItemId.get(item.getId());
            if (orderIdx != null) item.changeOrder(orderIdx);
        });
        dailyPlanItemRepository.saveAll(items);
    }

    /**
     * 아직 계획에 없는 Task를 그 날짜의 맨 뒤에 담는다. 이미 있으면 아무것도 하지 않는다.
     * 계획 항목 id를 모르는 화면(폴더 목록)에서 날짜를 고를 때 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void addTaskIfAbsent(Long userId, LocalDate planDate, Long taskId) {
        if (containsAnyTasks(userId, planDate, List.of(taskId))) {
            return;
        }
        save(userId, planDate, DailyPlanItem.restore(null, taskId, getItems(userId, planDate).size(), null, null));
    }

    /**
     * 계획 항목을 다른 날짜로 옮기고 옮기기 전 날짜를 돌려준다.
     * 대상 날짜의 맨 뒤에 붙인다. 같은 날짜에 같은 Task를 두 번 담을 수 없다는 규칙은 여기에도 적용된다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public LocalDate moveItemDate(Long userId, Long itemId, Long taskId, LocalDate toDate) {
        DailyPlanItemEntity item = dailyPlanItemRepository
                .findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND));
        if (!item.toDomain().getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_ITEM);
        }

        LocalDate fromDate = item.getPlanDate();
        if (fromDate.equals(toDate)) {
            return fromDate;
        }
        if (containsAnyTasks(userId, toDate, List.of(taskId))) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_PLAN_TASKS);
        }

        item.updateDate(toDate, getItems(userId, toDate).size());
        dailyPlanItemRepository.flush();
        return fromDate;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, LocalDate planDate, Long itemId) {
        DailyPlanItemEntity item = dailyPlanItemRepository
                .findByIdAndUserIdAndPlanDate(itemId, userId, planDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND));
        dailyPlanItemRepository.delete(item);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean containsAnyTasks(Long userId, LocalDate planDate, List<Long> taskIds) {
        Set<Long> uniqueTaskIds = Set.copyOf(taskIds);
        if (uniqueTaskIds.isEmpty()) {
            return false;
        }
        return dailyPlanItemRepository.countDistinctTaskIds(userId, planDate, uniqueTaskIds) > 0;
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean containsAllTasks(Long userId, LocalDate planDate, List<Long> taskIds) {
        Set<Long> uniqueTaskIds = Set.copyOf(taskIds);
        if (uniqueTaskIds.isEmpty()) {
            return true;
        }
        return dailyPlanItemRepository.countDistinctTaskIds(
                userId,
                planDate,
                uniqueTaskIds
        ) == uniqueTaskIds.size();
    }
}
