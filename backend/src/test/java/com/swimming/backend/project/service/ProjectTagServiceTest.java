package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.ProjectTag;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectTagServiceTest {

    private ProjectTagRepository projectTagRepository;
    private ProjectTagService projectTagService;

    @BeforeEach
    void setUp() {
        projectTagRepository = mock(ProjectTagRepository.class);
        projectTagService = new ProjectTagService(projectTagRepository);
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

    private ProjectTagEntity tagEntity(Long id, String name) {
        ProjectTagEntity entity = ProjectTagEntity.from(ProjectTag.create(1L, name));
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
