package com.swimming.backend.plan.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.plan.domain.DailyPlanItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "daily_plan_items",
        uniqueConstraints = @UniqueConstraint(
                name = "daily_plan_items_plan_task_unique",
                columnNames = {"daily_plan_id", "task_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPlanItemEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_plan_id", nullable = false)
    private DailyPlanEntity dailyPlan;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    private DailyPlanItemEntity(DailyPlanEntity dailyPlan, DailyPlanItem item) {
        this.dailyPlan = dailyPlan;
        apply(item);
    }

    static DailyPlanItemEntity from(DailyPlanEntity dailyPlan, DailyPlanItem item) {
        return new DailyPlanItemEntity(dailyPlan, item);
    }

    void apply(DailyPlanItem item) {
        this.taskId = item.getTaskId();
        this.title = item.getTitle();
        this.orderIdx = item.getOrderIdx();
    }

    public DailyPlanItem toDomain() {
        return DailyPlanItem.restore(
                id,
                taskId,
                title,
                orderIdx,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
