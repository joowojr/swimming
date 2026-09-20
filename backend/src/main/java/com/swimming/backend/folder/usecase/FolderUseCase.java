package com.swimming.backend.folder.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.dto.CreateFolderRequest;
import com.swimming.backend.folder.dto.FolderDetailResponse;
import com.swimming.backend.folder.dto.FolderResponse;
import com.swimming.backend.folder.dto.PinFolderRequest;
import com.swimming.backend.folder.dto.UpdateFolderRequest;
import com.swimming.backend.folder.dto.UpdateFolderStatusRequest;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.folder.service.FolderTagService;
import com.swimming.backend.task.domain.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderUseCase {

    private final FolderService folderService;
    private final FolderTagService folderTagService;

    @Transactional(propagation = Propagation.REQUIRED)
    public FolderResponse create(Long userId, CreateFolderRequest request) {
        if (request.tagId() != null && request.newTagName() != null) {
            throw new BusinessException(ErrorCode.FOLDER_TAG_SELECTION_CONFLICT);
        }

        FolderTag tag = null;
        if (request.newTagName() != null) {
            tag = folderTagService.create(FolderTag.create(userId, request.newTagName()));
        } else if (request.tagId() != null) {
            tag = folderTagService.getOne(userId, request.tagId());
        }

        Folder folder = Folder.create(
                userId,
                tag,
                request.name(),
                request.description(),
                request.targetDate()
        );
        return FolderResponse.from(folderService.create(folder));
    }

    public List<FolderResponse> getAll(Long userId) {
        return folderService.getAll(userId)
                .stream()
                .map(FolderResponse::from)
                .toList();
    }

    public FolderDetailResponse getOne(Long userId, Long folderId) {
        var folder = folderService.getOne(userId, folderId);

        return FolderDetailResponse.from(folder);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public FolderResponse update(
            Long userId,
            Long folderId,
            UpdateFolderRequest request
    ) {
        return FolderResponse.from(folderService.update(
                userId,
                folderId,
                request.tagId(),
                request.name(),
                request.description(),
                request.targetDate()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public FolderResponse updateStatus(Long userId, Long folderId, UpdateFolderStatusRequest request) {
        Folder folder = folderService.getOne(userId, folderId);
        folder.updateStatus(request.status());
        return FolderResponse.from(folderService.updateStatus(folder));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public FolderResponse pin(Long userId, Long folderId, PinFolderRequest request) {
        return FolderResponse.from(
                folderService.pin(userId, folderId, request.pinned())
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, Long folderId) {
        folderService.delete(userId, folderId);
    }
}
