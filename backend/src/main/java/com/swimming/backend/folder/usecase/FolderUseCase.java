package com.swimming.backend.folder.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderStatusFilter;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.dto.CreateFolderRequest;
import com.swimming.backend.folder.dto.FolderDetailResponse;
import com.swimming.backend.folder.dto.FolderResponse;
import com.swimming.backend.folder.dto.PinFolderRequest;
import com.swimming.backend.folder.dto.UpdateFolderRequest;
import com.swimming.backend.folder.dto.UpdateFolderTagRequest;
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
        FolderTag tag = resolveTag(userId, request.tagId(), request.newTagName());
        Folder folder = Folder.create(
                userId,
                tag,
                request.name(),
                request.description(),
                request.targetDate()
        );
        return FolderResponse.from(folderService.create(folder));
    }

    public List<FolderResponse> getAll(Long userId, FolderStatusFilter filter) {
        return folderService.getAll(userId, filter)
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
                request.name(),
                request.description(),
                request.targetDate()
        ));
    }

    /** 새 태그를 만들 때도 같은 트랜잭션이라, 폴더 반영이 실패하면 태그도 남지 않는다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public FolderResponse updateTag(Long userId, Long folderId, UpdateFolderTagRequest request) {
        FolderTag tag = resolveTag(userId, request.tagId(), request.newTagName());
        return FolderResponse.from(folderService.updateTag(userId, folderId, tag));
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

    /** 기존 태그(tagId)나 새 태그(newTagName) 중 하나를 고른다. 둘 다 없으면 태그가 없다. */
    private FolderTag resolveTag(Long userId, Long tagId, String newTagName) {
        if (tagId != null && newTagName != null) {
            throw new BusinessException(ErrorCode.FOLDER_TAG_SELECTION_CONFLICT);
        }
        if (newTagName != null) {
            return folderTagService.create(FolderTag.create(userId, newTagName));
        }
        if (tagId != null) {
            return folderTagService.getOne(userId, tagId);
        }
        return null;
    }
}
