package com.swimming.backend.plan.domain;

import com.swimming.backend.common.entity.BaseTimeEntity;
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
public class DailyPlanItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_plan_id", nullable = false)
    private DailyPlan dailyPlan;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    private DailyPlanItem(DailyPlan dailyPlan, Long taskId, int orderIdx) {
        this.dailyPlan = dailyPlan;
        this.taskId = taskId;
        this.orderIdx = orderIdx;
    }

    static DailyPlanItem create(DailyPlan dailyPlan, Long taskId, int orderIdx) {
        return new DailyPlanItem(dailyPlan, taskId, orderIdx);
    }

    void changeOrder(int orderIdx) {
        this.orderIdx = orderIdx;
    }
}
