package com.swimming.backend.calendar.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.calendar.domain.DailyPlanItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(
        name = "daily_plan_items",
        uniqueConstraints = @UniqueConstraint(
                name = "daily_plan_items_user_date_task_unique",
                columnNames = {"user_id", "plan_date", "task_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPlanItemEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    private DailyPlanItemEntity(Long userId, LocalDate planDate, DailyPlanItem item) {
        this.userId = userId;
        this.planDate = planDate;
        apply(item);
    }

    public static DailyPlanItemEntity from(Long userId, LocalDate planDate, DailyPlanItem item) {
        return new DailyPlanItemEntity(userId, planDate, item);
    }

    void apply(DailyPlanItem item) {
        this.taskId = item.getTaskId();
        this.orderIdx = item.getOrderIdx();
    }

    public void updateDate(LocalDate planDate, int orderIdx) {
        this.planDate = planDate;
        this.orderIdx = orderIdx;
    }

    public void changeOrder(int orderIdx) {
        this.orderIdx = orderIdx;
    }

    public DailyPlanItem toDomain() {
        return DailyPlanItem.restore(
                id,
                taskId,
                orderIdx,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
