package com.swimming.backend.project.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.project.domain.ProjectTag;
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

@Entity
@Table(
        name = "project_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_tags_user_name",
                columnNames = {"user_id", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTagEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String name;

    private ProjectTagEntity(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public static ProjectTagEntity from(ProjectTag projectTag) {
        return new ProjectTagEntity(projectTag.getUserId(), projectTag.getName());
    }

    public void apply(ProjectTag projectTag) {
        this.name = projectTag.getName();
    }

    public ProjectTag toDomain() {
        return ProjectTag.restore(id, userId, name, getCreatedAt(), getUpdatedAt());
    }
}
