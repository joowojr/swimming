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

import java.time.Instant;
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
                .findAllActiveOrderByPinnedAtDescCreatedAtDesc(
                        userId,
                        FolderStatus.ARCHIVED
                )
                .stream()
                .map(FolderEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Folder getOne(Long userId, Long folderId) {
        return getOwnedFolderEntity(userId, folderId).toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public FolderReference getReference(Long userId, Long folderId) {
        return FolderReference.from(getOwnedFolderEntity(userId, folderId).toDomain());
    }

    /** 호출자의 짧은 저장 트랜잭션에서 폴더별 변경을 직렬화한다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void lockOwned(Long userId, Long folderId) {
        folderRepository.findOwnedForUpdate(userId, folderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void validateOwnership(Long userId, Long folderId) {
        getOwnedFolderEntity(userId, folderId);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public void validateOwnerships(Long userId, List<Long> folderIds) {
        Set<Long> uniquefolderIds = Set.copyOf(folderIds);
        if (uniquefolderIds.isEmpty()) {
            return;
        }
        if (folderRepository.countOwnedActiveByIds(userId, uniquefolderIds)
                != uniquefolderIds.size()) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
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
        FolderEntity entity = getOwnedFolderEntity(userId, folderId);
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
        FolderEntity entity = getOwnedFolderEntity(userId, folderId);
        if (folderRepository.countActiveTasks(userId, folderId) > 0) {
            throw new BusinessException(ErrorCode.FOLDER_HAS_TASKS);
        }
        // 링크는 folder_id가 NOT NULL이라 폴더에서 떼어 둘 자리가 없다. 폴더만 지우면
        // 삭제된 폴더를 가리키는 행이 남는다.
        if (entity.hasSource()) {
            throw new BusinessException(ErrorCode.FOLDER_HAS_SOURCES);
        }
        entity.delete();
        folderRepository.flush();
    }

    /**
     * 이 폴더에 살아 있는 링크가 있는지 기록한다. 값은 링크 도메인이 정해 넘긴다.
     *
     * <p>folder는 왜 켜지고 꺼지는지 모른 채 자기 컬럼만 쓴다. folder가 knowledge에
     * 물어보면 의존 방향이 뒤집힌다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void updateHasSource(Long userId, Long folderId, boolean hasSource) {
        getOwnedFolderEntity(userId, folderId).updateHasSource(hasSource);
        folderRepository.flush();
    }

    /**
     * 폴더를 고정하거나 해제한다. 고정하면 그 시각을 남겨 목록에서 최근 고정 순으로 쓴다.
     *
     * <p>save를 부르지 않고 관리 Entity를 그대로 바꿔 변경 감지로 반영한다. flush는 응답이
     * 갱신된 updated_at을 담도록 쓰기 시점만 앞당긴다. update와 같은 방식이다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Folder pin(Long userId, Long folderId, boolean pinned) {
        FolderEntity entity = getOwnedFolderEntity(userId, folderId);
        entity.updatePinnedAt(pinned ? Instant.now() : null);
        folderRepository.flush();
        return entity.toDomain();
    }

    private FolderEntity getOwnedFolderEntity(Long userId, Long folderId) {
        return folderRepository.findByIdAndUser_IdAndDeletedFalse(folderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
    }

    private FolderTagEntity getOwnedTagEntity(Long userId, FolderTag tag) {
        if (tag == null) {
            return null;
        }
        return folderTagRepository.findByIdAndUserId(tag.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_TAG_NOT_FOUND));
    }

    private FolderTagEntity getOwnedTagEntity(Long userId, Long tagId) {
        return folderTagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_TAG_NOT_FOUND));
    }
}
