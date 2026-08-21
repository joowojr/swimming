package com.swimming.backend.plan.domain;

import com.swimming.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(
        name = "daily_plans",
        uniqueConstraints = @UniqueConstraint(
                name = "daily_plans_user_date_unique",
                columnNames = {"user_id", "plan_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPlan extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @OneToMany(mappedBy = "dailyPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIdx ASC")
    private List<DailyPlanItem> items = new ArrayList<>();

    private DailyPlan(Long userId, LocalDate planDate) {
        this.userId = userId;
        this.planDate = planDate;
    }

    public static DailyPlan create(Long userId, LocalDate planDate, List<Long> taskIds) {
        DailyPlan dailyPlan = new DailyPlan(userId, planDate);
        dailyPlan.replaceItems(taskIds);
        return dailyPlan;
    }

    public void replaceItems(List<Long> taskIds) {
        Set<Long> requestedTaskIds = new HashSet<>(taskIds);
        items.removeIf(item -> !requestedTaskIds.contains(item.getTaskId()));

        Map<Long, DailyPlanItem> itemsByTaskId = new HashMap<>();
        for (DailyPlanItem item : items) {
            itemsByTaskId.put(item.getTaskId(), item);
        }

        for (int orderIdx = 0; orderIdx < taskIds.size(); orderIdx++) {
            Long taskId = taskIds.get(orderIdx);
            DailyPlanItem item = itemsByTaskId.get(taskId);
            if (item == null) {
                item = DailyPlanItem.create(this, taskId, orderIdx);
                items.add(item);
                itemsByTaskId.put(taskId, item);
            } else {
                item.changeOrder(orderIdx);
            }
        }

        items.sort((left, right) -> Integer.compare(left.getOrderIdx(), right.getOrderIdx()));
    }
}
