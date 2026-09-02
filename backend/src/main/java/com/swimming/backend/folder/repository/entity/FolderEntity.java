package com.swimming.backend.folder.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;
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
public class FolderEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_tag_id")
    private FolderTagEntity tag;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FolderStatus status;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    private FolderEntity(Folder folder, User user, FolderTagEntity tag) {
        this.user = user;
        this.tag = tag;
        this.name = folder.getName();
        this.description = folder.getDescription();
        this.targetDate = folder.getTargetDate();
        this.status = folder.getStatus();
        this.deleted = folder.isDeleted();
    }

    public static FolderEntity from(
            Folder folder,
            User user,
            FolderTagEntity tag
    ) {
        return new FolderEntity(folder, user, tag);
    }

    public void apply(Folder folder, FolderTagEntity tag) {
        this.tag = tag;
        this.name = folder.getName();
        this.description = folder.getDescription();
        this.targetDate = folder.getTargetDate();
        this.status = folder.getStatus();
        this.deleted = folder.isDeleted();
    }

    public void delete() {
        this.deleted = true;
    }

    public Folder toDomain() {
        return Folder.restore(
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
