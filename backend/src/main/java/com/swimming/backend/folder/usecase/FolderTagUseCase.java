package com.swimming.backend.folder.usecase;

import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.dto.FolderTagNameRequest;
import com.swimming.backend.folder.dto.FolderTagResponse;
import com.swimming.backend.folder.service.FolderTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderTagUseCase {

    private final FolderTagService folderTagService;

    public List<FolderTagResponse> getAll(Long userId) {
        return folderTagService.getAll(userId)
                .stream()
                .map(FolderTagResponse::from)
                .toList();
    }

    public FolderTagResponse create(Long userId, FolderTagNameRequest request) {
        return FolderTagResponse.from(
                folderTagService.create(FolderTag.create(userId, request.name()))
        );
    }

    public FolderTagResponse updateName(
            Long userId,
            Long tagId,
            FolderTagNameRequest request
    ) {
        return FolderTagResponse.from(folderTagService.updateName(
                userId,
                tagId,
                request.name()
        ));
    }

    public void delete(Long userId, Long tagId) {
        folderTagService.delete(userId, tagId);
    }
}
