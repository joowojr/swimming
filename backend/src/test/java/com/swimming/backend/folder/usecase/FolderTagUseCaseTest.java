package com.swimming.backend.folder.usecase;

import com.swimming.backend.folder.domain.FolderTag;
import com.swimming.backend.folder.dto.FolderTagNameRequest;
import com.swimming.backend.folder.dto.FolderTagResponse;
import com.swimming.backend.folder.service.FolderTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderTagUseCaseTest {

    private FolderTagService folderTagService;
    private FolderTagUseCase folderTagUseCase;

    @BeforeEach
    void setUp() {
        folderTagService = mock(FolderTagService.class);
        folderTagUseCase = new FolderTagUseCase(folderTagService);
    }

    @Test
    @DisplayName("인증 사용자의 태그를 생성한다")
    void createsFolderTag() {
        when(folderTagService.create(org.mockito.ArgumentMatchers.any(FolderTag.class)))
                .thenReturn(FolderTag.restore(3L, 1L, "취준", null, null));

        FolderTagResponse response = folderTagUseCase.create(
                1L,
                new FolderTagNameRequest(" 취준 ")
        );

        assertThat(response).isEqualTo(new FolderTagResponse(3L, "취준"));
    }

    @Test
    @DisplayName("소유한 태그 이름을 변경한다")
    void updatesFolderTag() {
        FolderTag tag = FolderTag.restore(3L, 1L, "취준", null, null);
        tag.rename("이직");
        when(folderTagService.updateName(1L, 3L, " 이직 ")).thenReturn(tag);

        FolderTagResponse response = folderTagUseCase.updateName(
                1L,
                3L,
                new FolderTagNameRequest(" 이직 ")
        );

        assertThat(response).isEqualTo(new FolderTagResponse(3L, "이직"));
        verify(folderTagService).updateName(1L, 3L, " 이직 ");
    }

    @Test
    @DisplayName("소유한 태그 삭제를 서비스에 위임한다")
    void deletesFolderTag() {
        folderTagUseCase.delete(1L, 3L);

        verify(folderTagService).delete(1L, 3L);
    }
}
