package com.swimming.backend.project.domain;

import com.swimming.backend.common.entity.BaseTimeEntity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private ProjectTag tag;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Builder
    private Project(
            Long userId,
            ProjectTag tag,
            String name,
            String description,
            LocalDate targetDate
    ) {
        this.userId = userId;
        this.tag = tag;
        this.name = name;
        this.description = description;
        this.targetDate = targetDate;
        this.status = ProjectStatus.IN_PROGRESS;
    }

    public void update(
            String name,
            String description,
            LocalDate targetDate,
            ProjectStatus status,
            ProjectTag tag
    ) {
        this.name = name;
        this.description = description;
        this.targetDate = targetDate;
        this.status = status;
        this.tag = tag;
    }
}
