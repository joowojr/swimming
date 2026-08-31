package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.repository.ProjectRepository;
import com.swimming.backend.project.repository.ProjectTagRepository;
import com.swimming.backend.project.repository.entity.ProjectTagEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectTagServiceTest {

    private ProjectTagRepository projectTagRepository;
    private ProjectRepository projectRepository;
    private ProjectTagService projectTagService;

    @BeforeEach
    void setUp() {
        projectTagRepository = mock(ProjectTagRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectTagService = new ProjectTagService(projectTagRepository, projectRepository);
        when(projectTagRepository.saveAndFlush(any(ProjectTagEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectTagRepository.deleteOwnedTag(any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("태그 이름의 앞뒤 공백을 제거해 순수 도메인으로 반환한다")
    void createsTrimmedProjectTag() {
        when(projectTagRepository.saveAndFlush(any(ProjectTagEntity.class)))
                .thenAnswer(invocation -> {
                    ProjectTagEntity entity = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entity, "id", 3L);
                    return entity;
                });

        ProjectTag tag = projectTagService.create(ProjectTag.create(1L, " 취준 "));

        assertThat(tag.getId()).isEqualTo(3L);
        assertThat(tag.getUserId()).isEqualTo(1L);
        assertThat(tag.getName()).isEqualTo("취준");
    }

    @Test
    @DisplayName("같은 사용자는 같은 이름의 태그를 중복 생성할 수 없다")
    void rejectsDuplicateProjectTagName() {
        when(projectTagRepository.existsByUserIdAndName(1L, "취준")).thenReturn(true);

        assertThatThrownBy(() -> projectTagService.create(ProjectTag.create(1L, "취준")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_ALREADY_EXISTS));
        verify(projectTagRepository, never()).saveAndFlush(any(ProjectTagEntity.class));
    }

    @Test
    @DisplayName("동시 생성으로 태그 이름이 중복되어도 충돌 예외로 변환한다")
    void convertsDuplicateConstraintViolationToBusinessException() {
        when(projectTagRepository.saveAndFlush(any(ProjectTagEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> projectTagService.create(ProjectTag.create(1L, "취준")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("사용자의 태그 선택지를 이름순 순수 도메인 목록으로 조회한다")
    void returnsUsersProjectTags() {
        when(projectTagRepository.findAllByUserIdOrderByNameAsc(1L))
                .thenReturn(List.of(tagEntity(1L, "사이드 프로젝트"), tagEntity(2L, "취준")));

        List<ProjectTag> tags = projectTagService.getAll(1L);

        assertThat(tags).extracting(ProjectTag::getName)
                .containsExactly("사이드 프로젝트", "취준");
    }

    @Test
    @DisplayName("사용자가 소유한 태그 이름을 변경한다")
    void updatesOwnedProjectTagName() {
        ProjectTagEntity entity = tagEntity(3L, "취준");
        when(projectTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(java.util.Optional.of(entity));

        ProjectTag updated = projectTagService.updateName(1L, 3L, " 이직 ");

        assertThat(updated.getName()).isEqualTo("이직");
        verify(projectTagRepository).findByIdAndUserId(3L, 1L);
        verify(projectTagRepository).flush();
    }

    @Test
    @DisplayName("다른 태그와 같은 이름으로 변경할 수 없다")
    void rejectsDuplicateProjectTagNameOnUpdateName() {
        ProjectTagEntity entity = tagEntity(3L, "취준");
        when(projectTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(java.util.Optional.of(entity));
        when(projectTagRepository.existsByUserIdAndNameAndIdNot(1L, "이직", 3L))
                .thenReturn(true);

        assertThatThrownBy(() -> projectTagService.updateName(1L, 3L, "이직"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROJECT_TAG_ALREADY_EXISTS));
        verify(projectTagRepository, never()).flush();
    }

    @Test
    @DisplayName("태그를 연결 프로젝트에서 해제한 뒤 삭제한다")
    void clearsTagFromProjectsBeforeDeletingIt() {
        projectTagService.delete(1L, 3L);

        var inOrder = org.mockito.Mockito.inOrder(projectRepository, projectTagRepository);
        inOrder.verify(projectRepository).clearTagFromOwnedProjects(1L, 3L);
        inOrder.verify(projectTagRepository).deleteOwnedTag(3L, 1L);
        verify(projectTagRepository, never()).findByIdAndUserId(3L, 1L);
    }

    @Test
    @DisplayName("다른 사용자의 태그는 수정하거나 삭제할 수 없다")
    void rejectsUnownedProjectTagChanges() {
        when(projectTagRepository.findByIdAndUserId(3L, 2L)).thenReturn(java.util.Optional.empty());
        when(projectTagRepository.deleteOwnedTag(3L, 2L)).thenReturn(0);

        assertThatThrownBy(() -> projectTagService.updateName(2L, 3L, "취준"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_NOT_FOUND));
        assertThatThrownBy(() -> projectTagService.delete(2L, 3L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_NOT_FOUND));
        verify(projectRepository).clearTagFromOwnedProjects(2L, 3L);
    }

    private ProjectTagEntity tagEntity(Long id, String name) {
        ProjectTagEntity entity = ProjectTagEntity.from(ProjectTag.create(1L, name));
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
