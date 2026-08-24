package com.swimming.backend.task.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    private TaskEntity(Task task) {
        this.projectId = task.getProjectId();
        this.title = task.getTitle();
        this.status = task.getStatus();
        this.orderIdx = task.getOrderIdx();
    }

    public static TaskEntity from(Task task) {
        return new TaskEntity(task);
    }

    public void apply(Task task) {
        this.title = task.getTitle();
        this.status = task.getStatus();
        this.orderIdx = task.getOrderIdx();
    }

    public void changeStatus(TaskStatus status) {
        this.status = status;
    }

    public void changeOrder(int orderIdx) {
        this.orderIdx = orderIdx;
    }

    public Task toDomain() {
        return Task.restore(
                id,
                projectId,
                title,
                status,
                orderIdx,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
