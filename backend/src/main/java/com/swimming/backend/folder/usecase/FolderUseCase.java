package com.swimming.backend.folder.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.folder.domain.Folder;
import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.dto.CreateFolderRequest;
import com.swimming.backend.common.dto.CursorPage;
import com.swimming.backend.folder.dto.FolderDetailResponse;
import com.swimming.backend.folder.dto.FolderProgressResponse;
import com.swimming.backend.folder.dto.FolderResponse;
import com.swimming.backend.folder.dto.UpdateFolderRequest;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.folder.service.FolderTagService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.service.TaskCursorCodec;
import com.swimming.backend.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderUseCase {

    private final FolderService folderService;
    private final FolderTagService folderTagService;
    private final TaskService taskService;

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

    /**
     * @param size   할 일 첫 페이지의 크기
     * @param cursor null이면 첫 페이지
     */
    public FolderDetailResponse getOne(Long userId, Long folderId, int size, String cursor) {
        var folder = folderService.getOne(userId, folderId);

        // 한 건 더 읽어 다음 장이 있는지 본다.
        CursorPage<TaskSummaryResponse> tasks = CursorPage.of(
                taskService.getPageByFolder(
                        folder.getId(),
                        StringUtils.hasText(cursor) ? TaskCursorCodec.decode(cursor) : null,
                        size + 1
                ),
                size,
                TaskSummaryResponse::from,
                last -> TaskCursorCodec.encode(last.getCreatedAt(), last.getId())
        );

        return FolderDetailResponse.from(folder, progressOf(folder.getId()), tasks);
    }

    /** 진척은 방금 읽은 페이지가 아니라 폴더 전체에서 센다. */
    private FolderProgressResponse progressOf(Long folderId) {
        TaskService.TaskCounts counts = taskService.countByFolder(folderId);

        return new FolderProgressResponse(
                (int) counts.total(),
                (int) counts.completed(),
                counts.total() == 0 ? 0 : (int) (counts.completed() * 100 / counts.total())
        );
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
                request.targetDate(),
                request.status()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void delete(Long userId, Long folderId) {
        folderService.delete(userId, folderId);
    }
}
