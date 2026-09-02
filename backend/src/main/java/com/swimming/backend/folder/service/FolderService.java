package com.swimming.backend.folder.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.dto.FolderReference;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.FolderTagRepository;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.folder.repository.entity.FolderTagEntity;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FolderTagRepository folderTagRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder create(Folder folder) {
        FolderTagEntity tagEntity = getOwnedTagEntity(folder.getUserId(), folder.getTag());
        User user = entityManager.getReference(User.class, folder.getUserId());
        return folderRepository.saveAndFlush(
                FolderEntity.from(folder, user, tagEntity)
        ).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Folder> getAll(Long userId) {
        return folderRepository
                .findAllByUser_IdAndStatusNotAndDeletedFalseOrderByCreatedAtDesc(
                        userId,
                        FolderStatus.ARCHIVED
                )
                .stream()
                .map(FolderEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Folder getOne(Long userId, Long folderId) {
        return getOwnedProjectEntity(userId, folderId).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public FolderReference getReference(Long userId, Long folderId) {
        return FolderReference.from(getOwnedProjectEntity(userId, folderId).toDomain());
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void validateOwnership(Long userId, Long folderId) {
        getOwnedProjectEntity(userId, folderId);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void validateOwnerships(Long userId, List<Long> folderIds) {
        Set<Long> uniquefolderIds = Set.copyOf(folderIds);
        if (uniquefolderIds.isEmpty()) {
            return;
        }
        if (folderRepository.countOwnedActiveByIds(userId, uniquefolderIds)
                != uniquefolderIds.size()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder update(
            Long userId,
            Long folderId,
            Long tagId,
            String name,
            String description,
            LocalDate targetDate,
            FolderStatus status
    ) {
        FolderEntity entity = getOwnedProjectEntity(userId, folderId);
        FolderTagEntity tagEntity = tagId == null
                ? null
                : getOwnedTagEntity(userId, tagId);
        Folder folder = entity.toDomain();
        folder.update(
                name,
                description,
                targetDate,
                status,
                tagEntity == null ? null : tagEntity.toDomain()
        );
        entity.apply(folder, tagEntity);
        folderRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, Long folderId) {
        FolderEntity entity = getOwnedProjectEntity(userId, folderId);
        if (folderRepository.countActiveTasks(userId, folderId) > 0) {
            throw new BusinessException(ErrorCode.PROJECT_HAS_TASKS);
        }
        entity.delete();
        folderRepository.flush();
    }

    private FolderEntity getOwnedProjectEntity(Long userId, Long folderId) {
        return folderRepository.findByIdAndUser_IdAndDeletedFalse(folderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private FolderTagEntity getOwnedTagEntity(Long userId, FolderTag tag) {
        if (tag == null) {
            return null;
        }
        return folderTagRepository.findByIdAndUserId(tag.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND));
    }

    private FolderTagEntity getOwnedTagEntity(Long userId, Long tagId) {
        return folderTagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_TAG_NOT_FOUND));
    }
}
