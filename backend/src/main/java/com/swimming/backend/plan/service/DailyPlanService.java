package com.swimming.backend.plan.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.plan.domain.DailyPlanItem;
import com.swimming.backend.plan.repository.DailyPlanItemBatchRepository;
import com.swimming.backend.plan.repository.DailyPlanItemRepository;
import com.swimming.backend.plan.repository.entity.DailyPlanItemEntity;
import com.swimming.backend.plan.dto.projection.DailyPlanItemQueryRow;
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
