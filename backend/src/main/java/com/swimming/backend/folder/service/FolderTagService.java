package com.swimming.backend.folder.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.repository.FolderRepository;
import com.swimming.backend.folder.repository.FolderTagRepository;
import com.swimming.backend.folder.repository.entity.FolderTagEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderTagService {

    private final FolderTagRepository folderTagRepository;
    private final FolderRepository folderRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public FolderTag create(FolderTag folderTag) {
        if (folderTagRepository.existsByUserIdAndName(folderTag.getUserId(), folderTag.getName())) {
            throw new BusinessException(ErrorCode.FOLDER_TAG_ALREADY_EXISTS);
        }

        try {
            return folderTagRepository
                    .saveAndFlush(FolderTagEntity.from(folderTag))
                    .toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.FOLDER_TAG_ALREADY_EXISTS);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public FolderTag getOne(Long userId, Long tagId) {
        return folderTagRepository.findByIdAndUserId(tagId, userId)
                .map(FolderTagEntity::toDomain)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_TAG_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<FolderTag> getAll(Long userId) {
        return folderTagRepository.findAllByUserIdOrderByNameAsc(userId)
                .stream()
                .map(FolderTagEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public FolderTag updateName(Long userId, Long tagId, String name) {
        FolderTagEntity entity = folderTagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_TAG_NOT_FOUND));
        String normalizedName = name.trim();
        if (folderTagRepository.existsByUserIdAndNameAndIdNot(
                userId,
                normalizedName,
                tagId
        )) {
            throw new BusinessException(ErrorCode.FOLDER_TAG_ALREADY_EXISTS);
        }

        try {
            entity.updateName(normalizedName);
            folderTagRepository.flush();
            return entity.toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.FOLDER_TAG_ALREADY_EXISTS, exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, Long tagId) {
        folderRepository.clearTagFromOwnedFolders(userId, tagId);
        if (folderTagRepository.deleteOwnedTag(tagId, userId) != 1) {
            throw new BusinessException(ErrorCode.FOLDER_TAG_NOT_FOUND);
        }
    }
}
