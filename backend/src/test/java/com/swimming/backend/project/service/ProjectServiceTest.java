package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.repository.ProjectRepository;
import com.swimming.backend.project.repository.ProjectTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectTagRepository projectTagRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectTagRepository = mock(ProjectTagRepository.class);
        projectService = new ProjectService(projectRepository, projectTagRepository);
    }

    @Test
    @DisplayName("인증된 사용자의 프로젝트를 생성한다")
    void createsProjectForAuthenticatedUser() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            ReflectionTestUtils.setField(project, "id", 10L);
            return project;
        });

        Project project = projectService.create(
                1L,
                " 새 프로젝트 ",
                " 프로젝트 설명 ",
                LocalDate.of(2026, 9, 30),
                null
        );

        assertThat(project.getId()).isEqualTo(10L);
        assertThat(project.getName()).isEqualTo("새 프로젝트");
        assertThat(project.getDescription()).isEqualTo("프로젝트 설명");
        assertThat(project.getTargetDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("사용자가 소유한 태그를 프로젝트에 하나 연결한다")
    void createsProjectWithOwnedTag() {
        ProjectTag tag = tag(3L, 1L, "취준");
        when(projectTagRepository.findByIdAndUserId(3L, 1L))
                .thenReturn(Optional.of(tag));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Project project = projectService.create(
                1L,
                "프로젝트",
                "설명",
                null,
                3L
        );

        assertThat(project.getTag()).isSameAs(tag);
    }

    @Test
    @DisplayName("다른 사용자의 태그를 프로젝트에 연결할 수 없다")
    void rejectsAnotherUsersTag() {
        when(projectTagRepository.findByIdAndUserId(3L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.create(
                1L,
                "프로젝트",
                "설명",
                null,
                3L
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.PROJECT_TAG_NOT_FOUND));
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("사용자의 보관되지 않은 프로젝트만 조회한다")
    void returnsOnlyNonArchivedProjectsForUser() {
        Project newest = project(1L, "두 번째 프로젝트", null);
        Project oldest = project(1L, "첫 번째 프로젝트", LocalDate.of(2026, 10, 1));
        when(projectRepository.findAllByUserIdAndStatusNotOrderByCreatedAtDesc(
                1L,
                ProjectStatus.ARCHIVED
        )).thenReturn(List.of(newest, oldest));

        List<Project> projects = projectService.getAll(1L);

        assertThat(projects).extracting(Project::getName)
                .containsExactly("두 번째 프로젝트", "첫 번째 프로젝트");
    }

    @Test
    @DisplayName("사용자가 소유한 프로젝트 상세를 조회한다")
    void returnsOwnedProjectDetail() {
        Project project = project(1L, "프로젝트", null);
        ReflectionTestUtils.setField(project, "id", 10L);
        when(projectRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(project));

        Project result = projectService.getOne(1L, 10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("프로젝트");
    }

    @Test
    @DisplayName("프로젝트를 수정하면서 목표일을 제거할 수 있다")
    void updatesProjectAndCanRemoveTargetDate() {
        Project project = project(1L, "기존 프로젝트", LocalDate.of(2026, 8, 31));
        when(projectRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(project));
        Project result = projectService.update(
                1L,
                10L,
                " 수정 프로젝트 ",
                " 수정 설명 ",
                null,
                ProjectStatus.IN_PROGRESS,
                null
        );

        assertThat(result.getName()).isEqualTo("수정 프로젝트");
        assertThat(result.getDescription()).isEqualTo("수정 설명");
        assertThat(result.getTargetDate()).isNull();
        assertThat(result.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("프로젝트에서 선택한 태그를 해제할 수 있다")
    void removesTagFromProject() {
        ProjectTag tag = tag(3L, 1L, "취준");
        Project project = Project.builder()
                .userId(1L)
                .tag(tag)
                .name("프로젝트")
                .description("설명")
                .build();
        when(projectRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(project));

        Project result = projectService.update(
                1L,
                10L,
                "프로젝트",
                "설명",
                null,
                ProjectStatus.IN_PROGRESS,
                null
        );

        assertThat(result.getTag()).isNull();
    }

    @Test
    @DisplayName("프로젝트를 삭제하지 않고 보관 상태로 변경한다")
    void archivesProjectWithoutDeletingIt() {
        Project project = project(1L, "프로젝트", null);
        when(projectRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(project));
        Project result = projectService.update(
                1L,
                10L,
                "프로젝트",
                "설명",
                null,
                ProjectStatus.ARCHIVED,
                null
        );

        assertThat(result.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    @DisplayName("다른 사용자의 프로젝트 존재 여부를 노출하지 않는다")
    void hidesWhetherAnotherUsersProjectExists() {
        when(projectRepository.findByIdAndUserId(10L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getOne(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    private Project project(Long userId, String name, LocalDate targetDate) {
        return Project.builder()
                .userId(userId)
                .name(name)
                .description("설명")
                .targetDate(targetDate)
                .build();
    }

    private ProjectTag tag(Long id, Long userId, String name) {
        ProjectTag tag = ProjectTag.builder()
                .userId(userId)
                .name(name)
                .build();
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }
}
