package com.swimming.backend.task.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.note.repository.entity.NoteEntity;
import com.swimming.backend.project.repository.entity.ProjectEntity;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.user.domain.User;
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_note_id")
    private NoteEntity sourceNote;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    private TaskEntity(
            Task task,
            User user,
            ProjectEntity project,
            NoteEntity sourceNote
    ) {
        this.user = user;
        this.project = project;
        this.sourceNote = sourceNote;
        this.title = task.getTitle();
        this.status = task.getStatus();
        this.orderIdx = task.getOrderIdx();
        this.deleted = false;
    }

    public static TaskEntity from(
            Task task,
            User user,
            ProjectEntity project,
            NoteEntity sourceNote
    ) {
        return new TaskEntity(task, user, project, sourceNote);
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateStatus(TaskStatus status) {
        this.status = status;
    }

    public void delete() {
        this.deleted = true;
    }

    public Task toDomain() {
        return Task.restore(
                id,
                user.getId(),
                project == null ? null : project.getId(),
                sourceNote == null ? null : sourceNote.getId(),
                title,
                status,
                orderIdx,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
