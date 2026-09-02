package com.swimming.backend.folder.repository.entity;

import com.swimming.backend.common.entity.BaseTimeEntity;
import com.swimming.backend.folder.domain.FolderTag;
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
        name = "folder_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_folder_tags_user_name",
                columnNames = {"user_id", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FolderTagEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String name;

    private FolderTagEntity(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public static FolderTagEntity from(FolderTag folderTag) {
        return new FolderTagEntity(folderTag.getUserId(), folderTag.getName());
    }

    public void updateName(String name) {
        this.name = name;
    }

    public FolderTag toDomain() {
        return FolderTag.restore(id, userId, name, getCreatedAt(), getUpdatedAt());
    }
}
