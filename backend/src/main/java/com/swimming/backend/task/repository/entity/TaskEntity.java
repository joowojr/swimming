package com.swimming.backend.task.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.project.repository.entity.ProjectEntity;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    private TaskEntity(Task task, ProjectEntity project) {
        this.project = project;
        this.title = task.getTitle();
        this.status = task.getStatus();
        this.orderIdx = task.getOrderIdx();
    }

    public static TaskEntity from(Task task, ProjectEntity project) {
        return new TaskEntity(task, project);
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
                project.getId(),
                title,
                status,
                orderIdx,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
