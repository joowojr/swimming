package com.swimming.backend.plan.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.plan.domain.DailyPlan;
import com.swimming.backend.plan.domain.DailyPlanItem;
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
public class DailyPlanEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @OneToMany(mappedBy = "dailyPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIdx ASC")
    private List<DailyPlanItemEntity> items = new ArrayList<>();

    private DailyPlanEntity(DailyPlan dailyPlan) {
        this.userId = dailyPlan.getUserId();
        this.planDate = dailyPlan.getPlanDate();
        for (DailyPlanItem item : dailyPlan.getItems()) {
            items.add(DailyPlanItemEntity.from(this, item));
        }
    }

    public static DailyPlanEntity from(DailyPlan dailyPlan) {
        return new DailyPlanEntity(dailyPlan);
    }

    public void apply(DailyPlan dailyPlan) {
        Set<Long> retainedIds = new HashSet<>(dailyPlan.getItems().stream()
                .map(DailyPlanItem::getId)
                .filter(itemId -> itemId != null)
                .toList());
        items.removeIf(item -> item.getId() != null && !retainedIds.contains(item.getId()));

        Map<Long, DailyPlanItemEntity> entitiesById = new HashMap<>();
        for (DailyPlanItemEntity item : items) {
            if (item.getId() != null) {
                entitiesById.put(item.getId(), item);
            }
        }
        for (DailyPlanItem item : dailyPlan.getItems()) {
            DailyPlanItemEntity entity = item.getId() == null ? null : entitiesById.get(item.getId());
            if (entity == null) {
                items.add(DailyPlanItemEntity.from(this, item));
            } else {
                entity.apply(item);
            }
        }
        items.sort((left, right) -> Integer.compare(left.getOrderIdx(), right.getOrderIdx()));
    }

    public DailyPlan toDomain() {
        return DailyPlan.restore(
                id,
                userId,
                planDate,
                getCreatedAt(),
                getUpdatedAt(),
                items.stream().map(DailyPlanItemEntity::toDomain).toList()
        );
    }
}
