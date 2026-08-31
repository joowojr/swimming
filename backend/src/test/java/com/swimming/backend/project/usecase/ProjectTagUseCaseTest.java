package com.swimming.backend.project.usecase;

import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.dto.ProjectTagNameRequest;
import com.swimming.backend.project.dto.ProjectTagResponse;
import com.swimming.backend.project.service.ProjectTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectTagUseCaseTest {

    private ProjectTagService projectTagService;
    private ProjectTagUseCase projectTagUseCase;

    @BeforeEach
    void setUp() {
        projectTagService = mock(ProjectTagService.class);
        projectTagUseCase = new ProjectTagUseCase(projectTagService);
    }

    @Test
    @DisplayName("인증 사용자의 태그를 생성한다")
    void createsProjectTag() {
        when(projectTagService.create(org.mockito.ArgumentMatchers.any(ProjectTag.class)))
                .thenReturn(ProjectTag.restore(3L, 1L, "취준", null, null));

        ProjectTagResponse response = projectTagUseCase.create(
                1L,
                new ProjectTagNameRequest(" 취준 ")
        );

        assertThat(response).isEqualTo(new ProjectTagResponse(3L, "취준"));
    }

    @Test
    @DisplayName("소유한 태그 이름을 변경한다")
    void updatesProjectTag() {
        ProjectTag tag = ProjectTag.restore(3L, 1L, "취준", null, null);
        tag.rename("이직");
        when(projectTagService.updateName(1L, 3L, " 이직 ")).thenReturn(tag);

        ProjectTagResponse response = projectTagUseCase.updateName(
                1L,
                3L,
                new ProjectTagNameRequest(" 이직 ")
        );

        assertThat(response).isEqualTo(new ProjectTagResponse(3L, "이직"));
        verify(projectTagService).updateName(1L, 3L, " 이직 ");
    }

    @Test
    @DisplayName("소유한 태그 삭제를 서비스에 위임한다")
    void deletesProjectTag() {
        projectTagUseCase.delete(1L, 3L);

        verify(projectTagService).delete(1L, 3L);
    }
}
