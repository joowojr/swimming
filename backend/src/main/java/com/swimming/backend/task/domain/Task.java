package com.swimming.backend.task.domain;

import com.swimming.backend.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseTimeEntity {

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

    @Column(name = "completion_pct", nullable = false)
    private int completionPct;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    @Builder
    private Task(Long projectId, String title, int orderIdx) {
        this.projectId = projectId;
        this.title = title;
        this.status = TaskStatus.TODO;
        this.completionPct = 0;
        this.orderIdx = orderIdx;
    }

    public void update(String title, TaskStatus status, int completionPct) {
        this.title = title;
        this.status = status;
        this.completionPct = completionPct;
    }

    public void changeOrder(int orderIdx) {
        this.orderIdx = orderIdx;
    }
}
