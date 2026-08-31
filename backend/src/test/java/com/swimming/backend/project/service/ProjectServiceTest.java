package com.swimming.backend.project.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.repository.ProjectRepository;
import com.swimming.backend.project.repository.ProjectTagRepository;
import com.swimming.backend.project.repository.entity.ProjectEntity;
import com.swimming.backend.project.repository.entity.ProjectTagEntity;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectTagRepository projectTagRepository;
    private EntityManager entityManager;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectTagRepository = mock(ProjectTagRepository.class);
        entityManager = mock(EntityManager.class);
        projectService = new ProjectService(
                projectRepository,
                projectTagRepository,
                entityManager
        );
        when(entityManager.getReference(eq(User.class), anyLong()))
                .thenAnswer(invocation -> user(invocation.getArgument(1)));
        when(projectRepository.saveAndFlush(any(ProjectEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("인증된 사용자의 프로젝트를 생성해 순수 도메인으로 반환한다")
    void createsProjectForAuthenticatedUser() {
        when(projectRepository.saveAndFlush(any(ProjectEntity.class))).thenAnswer(invocation -> {
            ProjectEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 10L);
            return entity;
        });

        Project project = projectService.create(Project.create(
                1L,
                null,
                " 새 프로젝트 ",
                " 프로젝트 설명 ",
                LocalDate.of(2026, 9, 30)
        ));

        assertThat(project.getId()).isEqualTo(10L);
        assertThat(project.getName()).isEqualTo("새 프로젝트");
        assertThat(project.getDescription()).isEqualTo("프로젝트 설명");
        assertThat(project.getTargetDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("사용자가 소유한 태그를 프로젝트에 하나 연결한다")
    void createsProjectWithOwnedTag() {
        ProjectTagEntity tag = tagEntity(3L, 1L, "취준");
        when(projectTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(tag));

        Project project = projectService.create(Project.create(
                1L, tag.toDomain(), "프로젝트", "설명", null
        ));

        assertThat(project.getTag().getId()).isEqualTo(3L);
        assertThat(project.getTag().getName()).isEqualTo("취준");
    }

    @Test
    @DisplayName("다른 사용자의 태그를 프로젝트에 연결할 수 없다")
    void rejectsAnotherUsersTag() {
        when(projectTagRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

        Project project = Project.create(
                1L,
                com.swimming.backend.project.domain.ProjectTag.restore(3L, 1L, "취준", null, null),
                "프로젝트",
                "설명",
                null
        );

        assertThatThrownBy(() -> projectService.create(project))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_TAG_NOT_FOUND));
        verify(projectRepository, never()).saveAndFlush(any(ProjectEntity.class));
    }

    @Test
    @DisplayName("사용자의 보관되지 않은 프로젝트만 조회한다")
    void returnsOnlyNonArchivedProjectsForUser() {
        ProjectEntity newest = projectEntity(1L, "두 번째 프로젝트", null, null);
        ProjectEntity oldest = projectEntity(1L, "첫 번째 프로젝트", LocalDate.of(2026, 10, 1), null);
        when(projectRepository.findAllByUser_IdAndStatusNotAndDeletedFalseOrderByCreatedAtDesc(
                1L, ProjectStatus.ARCHIVED
        )).thenReturn(List.of(newest, oldest));

        List<Project> projects = projectService.getAll(1L);

        assertThat(projects).extracting(Project::getName)
                .containsExactly("두 번째 프로젝트", "첫 번째 프로젝트");
    }

    @Test
    @DisplayName("사용자가 소유한 프로젝트 상세를 순수 도메인으로 조회한다")
    void returnsOwnedProjectDetail() {
        ProjectEntity entity = projectEntity(1L, "프로젝트", null, null);
        when(projectRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Project result = projectService.getOne(1L, 10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("프로젝트");
    }

    @Test
    @DisplayName("프로젝트를 수정하면서 목표일을 제거할 수 있다")
    void updatesProjectAndCanRemoveTargetDate() {
        ProjectEntity entity = projectEntity(1L, "기존 프로젝트", LocalDate.of(2026, 8, 31), null);
        when(projectRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Project result = projectService.update(
                1L, 10L, null, " 수정 프로젝트 ", " 수정 설명 ", null,
                ProjectStatus.IN_PROGRESS
        );

        assertThat(result.getName()).isEqualTo("수정 프로젝트");
        assertThat(result.getDescription()).isEqualTo("수정 설명");
        assertThat(result.getTargetDate()).isNull();
        assertThat(result.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        verify(projectRepository).findByIdAndUser_IdAndDeletedFalse(10L, 1L);
        verify(projectRepository).flush();
    }

    @Test
    @DisplayName("프로젝트에서 선택한 태그를 해제할 수 있다")
    void removesTagFromProject() {
        ProjectTagEntity tag = tagEntity(3L, 1L, "취준");
        ProjectEntity entity = projectEntity(1L, "프로젝트", null, tag);
        when(projectRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Project result = projectService.update(
                1L, 10L, null, "프로젝트", "설명", null,
                ProjectStatus.IN_PROGRESS
        );

        assertThat(result.getTag()).isNull();
    }

    @Test
    @DisplayName("프로젝트를 삭제하지 않고 보관 상태로 변경한다")
    void archivesProjectWithoutDeletingIt() {
        ProjectEntity entity = projectEntity(1L, "프로젝트", null, null);
        when(projectRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        Project result = projectService.update(
                1L, 10L, null, "프로젝트", "설명", null,
                ProjectStatus.ARCHIVED
        );

        assertThat(result.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectRepository, never()).delete(any(ProjectEntity.class));
    }

    @Test
    @DisplayName("다른 사용자의 프로젝트 존재 여부를 노출하지 않는다")
    void hidesWhetherAnotherUsersProjectExists() {
        when(projectRepository.findByIdAndUser_IdAndDeletedFalse(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getOne(2L, 10L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    @DisplayName("프로젝트를 soft delete하고 연결된 데이터를 보존한다")
    void softDeletesOwnedProject() {
        ProjectEntity entity = projectEntity(1L, "프로젝트", null, null);
        when(projectRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(entity));

        projectService.delete(1L, 10L);

        assertThat(entity.isDeleted()).isTrue();
        verify(projectRepository).findByIdAndUser_IdAndDeletedFalse(10L, 1L);
        verify(projectRepository).flush();
        verify(projectRepository, never()).delete(entity);
    }

    private ProjectEntity projectEntity(
            Long userId,
            String name,
            LocalDate targetDate,
            ProjectTagEntity tag
    ) {
        Project project = Project.create(
                userId,
                tag == null ? null : tag.toDomain(),
                name,
                "설명",
                targetDate
        );
        ProjectEntity entity = ProjectEntity.from(project, user(userId), tag);
        ReflectionTestUtils.setField(entity, "id", 10L);
        return entity;
    }

    private User user(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        return user;
    }

    private ProjectTagEntity tagEntity(Long id, Long userId, String name) {
        ProjectTagEntity entity = ProjectTagEntity.from(
                com.swimming.backend.project.domain.ProjectTag.create(userId, name)
        );
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
