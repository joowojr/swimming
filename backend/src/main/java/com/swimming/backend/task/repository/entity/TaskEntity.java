package com.swimming.backend.task.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.note.repository.entity.NoteEntity;
import com.swimming.backend.folder.repository.entity.FolderEntity;
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
    @JoinColumn(name = "folder_id")
    private FolderEntity folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_note_id")
    private NoteEntity sourceNote;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "is_priority", nullable = false)
    private boolean priority;

    @Column(name = "is_urgent", nullable = false)
    private boolean urgent;

    @Column(name = "order_idx", nullable = false)
    private int orderIdx;

    @Column(name = "matrix_rank", nullable = false)
    private long matrixRank;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    private TaskEntity(
            Task task,
            User user,
            FolderEntity folder,
            NoteEntity sourceNote
    ) {
        this.user = user;
        this.folder = folder;
        this.sourceNote = sourceNote;
        this.title = task.getTitle();
        this.status = task.getStatus();
        this.priority = task.isPriority();
        this.urgent = task.isUrgent();
        this.orderIdx = task.getOrderIdx();
        this.matrixRank = task.getMatrixRank();
        this.deleted = false;
    }

    public static TaskEntity from(
            Task task,
            User user,
            FolderEntity folder,
            NoteEntity sourceNote
    ) {
        return new TaskEntity(task, user, folder, sourceNote);
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateStatus(TaskStatus status) {
        this.status = status;
    }

    public void updatePriority(boolean priority) {
        this.priority = priority;
    }

    public void updateUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public void updateMatrixRank(long matrixRank) {
        this.matrixRank = matrixRank;
    }

    public void applyPlacement(Task task) {
        this.priority = task.isPriority();
        this.urgent = task.isUrgent();
        this.matrixRank = task.getMatrixRank();
    }

    public void delete() {
        this.deleted = true;
    }

    public Task toDomain() {
        return Task.restore(
                id,
                user.getId(),
                folder == null ? null : folder.getId(),
                sourceNote == null ? null : sourceNote.getId(),
                title,
                status,
                priority,
                urgent,
                orderIdx,
                matrixRank,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
