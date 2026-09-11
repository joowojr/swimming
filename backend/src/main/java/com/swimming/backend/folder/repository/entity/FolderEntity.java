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

import java.time.Instant;
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

    /**
     * 이 폴더에 살아 있는 링크가 있는지. 링크 도메인이 갱신하는 파생 상태다.
     *
     * <p>도메인 {@link Folder}에 두지 않는다. 사용자가 편집하는 속성이 아니라서
     * {@link #apply}가 지나가며 덮으면 안 된다.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "has_source", nullable = false)
    private boolean hasSource;

    /**
     * 이 폴더를 고정한 시각. 고정하지 않았으면 null이다.
     *
     * <p>{@code hasSource}와 같은 이유로 {@link #apply}가 옮기지 않는다. 폴더 수정과
     * 고정은 서로 다른 API라, 폴더를 수정했다고 고정이 풀리면 안 된다.
     */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

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

    /** hasSource와 pinnedAt은 옮기지 않는다. 폴더 수정이 다른 API의 값을 덮으면 안 된다. */
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

    public boolean hasSource() {
        return hasSource;
    }

    public void updateHasSource(boolean hasSource) {
        this.hasSource = hasSource;
    }

    /** 고정하면 시각을 남기고, 해제하면 지운다. 이미 고정한 폴더를 다시 고정하면 시각을 새로 쓴다. */
    public void updatePinnedAt(Instant pinnedAt) {
        this.pinnedAt = pinnedAt;
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
                hasSource,
                pinnedAt,
                getCreatedAt(),
                getUpdatedAt()
        );
    }
}
