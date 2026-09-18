package com.swimming.backend.knowledge.controller;

import com.swimming.backend.common.security.AuthUser;
import com.swimming.backend.knowledge.dto.in.CategoryPreviewRequest;
import com.swimming.backend.knowledge.dto.in.CategoryPreviewResponse;
import com.swimming.backend.knowledge.dto.in.CategoryReplaceRequest;
import com.swimming.backend.knowledge.dto.in.CategoryReplaceResponse;
import com.swimming.backend.knowledge.usecase.KnowledgeCategoryUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Folder에 모인 링크를 묶음으로 나눈다.
 *
 * <p>Category는 언제나 Folder에 속하므로 경로가 Folder 아래에 있다. folderId 없이
 * Source만으로 분류하는 경로는 두지 않는다.
 */
@Tag(
        name = "지식 링크 분류",
        description = "Folder의 링크를 Category로 묶는다. 초안은 `POST /preview`, 확정은 `PUT`."
)
@RestController
@RequestMapping("/api/folders/{folderId}/knowledge/categories")
@RequiredArgsConstructor
public class FolderKnowledgeCategoryController {

    private final KnowledgeCategoryUseCase categoryUseCase;

    /**
     * 201이 아니라 200이다. 아무것도 만들지 않고 사용자가 검토할 초안만 돌려준다.
     *
     * <p>읽기처럼 보이지만 GET을 쓰지 않는다. LLM을 호출하는 비싼 작업이고 같은 폴더에
     * 두 번 불러도 결과가 다를 수 있어 캐시되면 안 된다.
     */
    @PostMapping("/preview")
    public ResponseEntity<CategoryPreviewResponse> preview(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long folderId,
            @Valid @RequestBody CategoryPreviewRequest request
    ) {
        return ResponseEntity.ok(
                categoryUseCase.preview(authUser.id(), folderId, request.sourceIds())
        );
    }

    /**
     * 폴더의 Category 구성을 요청 값으로 통째로 바꾼다.
     *
     * <p>기존 Category는 soft delete하고 요청 항목을 전부 새로 만든다. 부분 수정 경로를
     * 두지 않는다 — 화면이 최종 구성을 보내면 서버는 그대로 교체한다.
     */
    @PutMapping
    public ResponseEntity<CategoryReplaceResponse> replace(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long folderId,
            @Valid @RequestBody CategoryReplaceRequest request
    ) {
        return ResponseEntity.ok(
                categoryUseCase.replace(authUser.id(), folderId, request)
        );
    }
}
