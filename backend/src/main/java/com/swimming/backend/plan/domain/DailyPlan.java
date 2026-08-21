package com.swimming.backend.plan.domain;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
public class DailyPlan {

    private final Long id;
    private final Long userId;
    private final LocalDate planDate;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final List<DailyPlanItem> items;

    private DailyPlan(
            Long id,
            Long userId,
            LocalDate planDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<DailyPlanItem> items
    ) {
        this.id = id;
        this.userId = userId;
        this.planDate = planDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.items = new ArrayList<>(items);
        this.items.sort((left, right) -> Integer.compare(left.getOrderIdx(), right.getOrderIdx()));
    }

    public static DailyPlan create(Long userId, LocalDate planDate) {
        return new DailyPlan(null, userId, planDate, null, null, List.of());
    }

    public static DailyPlan restore(
            Long id,
            Long userId,
            LocalDate planDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<DailyPlanItem> items
    ) {
        return new DailyPlan(id, userId, planDate, createdAt, updatedAt, items);
    }

    public void addItem(DailyPlanItem item) {
        if (item.getTaskId() != null && containsTask(item.getTaskId())) {
            throw new IllegalArgumentException("같은 Task를 계획에 중복 추가할 수 없습니다");
        }
        item.changeOrder(items.size());
        items.add(item);
    }

    public void reorder(List<Long> itemIds) {
        Set<Long> currentIds = new HashSet<>(items.stream().map(DailyPlanItem::getId).toList());
        if (itemIds.size() != items.size()
                || new HashSet<>(itemIds).size() != itemIds.size()
                || !currentIds.equals(new HashSet<>(itemIds))) {
            throw new IllegalArgumentException("계획 항목 순서가 올바르지 않습니다");
        }
        for (int orderIdx = 0; orderIdx < itemIds.size(); orderIdx++) {
            getItem(itemIds.get(orderIdx)).changeOrder(orderIdx);
        }
        items.sort((left, right) -> Integer.compare(left.getOrderIdx(), right.getOrderIdx()));
    }

    public void removeItem(Long itemId) {
        items.remove(getItem(itemId));
        for (int index = 0; index < items.size(); index++) {
            items.get(index).changeOrder(index);
        }
    }

    public DailyPlanItem getItem(Long itemId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("계획 항목을 찾을 수 없습니다"));
    }

    public boolean containsTask(Long taskId) {
        return items.stream().anyMatch(item -> Objects.equals(item.getTaskId(), taskId));
    }

    public List<DailyPlanItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
