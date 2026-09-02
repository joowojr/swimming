package com.swimming.backend.project.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "folders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_tag_id")
    private ProjectTagEntity tag;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    private ProjectEntity(Project project, User user, ProjectTagEntity tag) {
        this.user = user;
        this.tag = tag;
        this.name = project.getName();
        this.description = project.getDescription();
        this.targetDate = project.getTargetDate();
        this.status = project.getStatus();
        this.deleted = project.isDeleted();
    }

    public static ProjectEntity from(
            Project project,
            User user,
            ProjectTagEntity tag
    ) {
        return new ProjectEntity(project, user, tag);
    }

    public void apply(Project project, ProjectTagEntity tag) {
        this.tag = tag;
        this.name = project.getName();
        this.description = project.getDescription();
        this.targetDate = project.getTargetDate();
        this.status = project.getStatus();
        this.deleted = project.isDeleted();
    }

    public void delete() {
        this.deleted = true;
    }

    public Project toDomain() {
        return Project.restore(
                id,
                user.getId(),
                tag == null ? null : tag.toDomain(),
                name,
                description,
                targetDate,
                status,
                deleted,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
