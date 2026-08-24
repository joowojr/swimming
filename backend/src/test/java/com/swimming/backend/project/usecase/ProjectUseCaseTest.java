package com.swimming.backend.project.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.project.domain.Project;
import com.swimming.backend.project.domain.ProjectStatus;
import com.swimming.backend.project.domain.ProjectTag;
import com.swimming.backend.project.dto.CreateProjectRequest;
import com.swimming.backend.project.dto.ProjectDetailResponse;
import com.swimming.backend.project.dto.ProjectResponse;
import com.swimming.backend.project.dto.UpdateProjectRequest;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.project.service.ProjectTagService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectUseCaseTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private ProjectTagService projectTagService;

    @Mock
    private TaskService taskService;

    private ProjectUseCase projectUseCase;

    @BeforeEach
    void setUp() {
        projectUseCase = new ProjectUseCase(
                projectService,
                projectTagService,
                taskService
        );
    }

    @Test
    @DisplayName("프로젝트를 생성하고 응답 DTO로 변환한다")
    void createsProjectAndMapsResponse() {
        LocalDate targetDate = LocalDate.of(2026, 9, 30);
        ProjectTag tag = ProjectTag.restore(3L, 1L, "취준", null, null);
        CreateProjectRequest request = new CreateProjectRequest(
                "프로젝트",
                "설명",
                targetDate,
                3L,
                null
        );
        Project project = Project.restore(
                10L, 1L, tag, "프로젝트", "설명", targetDate,
                ProjectStatus.IN_PROGRESS, null, null
        );
        when(projectTagService.getOne(1L, 3L)).thenReturn(tag);
        when(projectService.create(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectUseCase.create(1L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("프로젝트");
        assertThat(response.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.tag().id()).isEqualTo(3L);
        assertThat(response.tag().name()).isEqualTo("취준");
        verify(projectTagService).getOne(1L, 3L);
        verify(projectService).create(argThat(created ->
                created.getUserId().equals(1L)
                        && created.getTag().getId().equals(3L)
                        && created.getTargetDate().equals(targetDate)
        ));
    }

    @Test
    @DisplayName("새 태그를 생성한 뒤 같은 트랜잭션에서 프로젝트에 연결한다")
    void createsNewTagAndConnectsItToProject() {
        ProjectTag tag = ProjectTag.restore(4L, 1L, "포트폴리오", null, null);
        Project project = Project.restore(
                10L, 1L, tag, "프로젝트", "설명", null,
                ProjectStatus.IN_PROGRESS, null, null
        );
        CreateProjectRequest request = new CreateProjectRequest(
                "프로젝트",
                "설명",
                null,
                null,
                " 포트폴리오 "
        );
        when(projectTagService.create(any(ProjectTag.class))).thenReturn(tag);
        when(projectService.create(any(Project.class))).thenReturn(project);

        ProjectResponse response = projectUseCase.create(1L, request);

        assertThat(response.tag().id()).isEqualTo(4L);
        assertThat(response.tag().name()).isEqualTo("포트폴리오");
        verify(projectTagService).create(argThat(created ->
                created.getUserId().equals(1L) && created.getName().equals("포트폴리오")
        ));
        verify(projectService).create(argThat(created -> created.getTag().getId().equals(4L)));
    }

    @Test
    @DisplayName("기존 태그와 새 태그 이름을 동시에 지정할 수 없다")
    void rejectsExistingAndNewTagTogether() {
        CreateProjectRequest request = new CreateProjectRequest(
                "프로젝트",
                "설명",
                null,
                3L,
                "포트폴리오"
        );

        assertThatThrownBy(() -> projectUseCase.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(ErrorCode.PROJECT_TAG_SELECTION_CONFLICT));
        verify(projectTagService, never()).create(any(ProjectTag.class));
        verify(projectService, never()).create(any(Project.class));
    }

    @Test
    @DisplayName("프로젝트 목록을 응답 DTO 목록으로 변환한다")
    void returnsProjectListAsResponses() {
        when(projectService.getAll(1L)).thenReturn(List.of(
                project(10L, "첫 번째", "설명 1", null),
                project(11L, "두 번째", "설명 2", null)
        ));

        List<ProjectResponse> responses = projectUseCase.getAll(1L);

        assertThat(responses).extracting(ProjectResponse::name)
                .containsExactly("첫 번째", "두 번째");
    }

    @Test
    @DisplayName("프로젝트 상세에 Task 목록과 완료 Task 비율을 포함한다")
    void returnsProjectDetailAsResponse() {
        when(projectService.getOne(1L, 10L))
                .thenReturn(project(10L, "프로젝트", "설명", null));
        when(taskService.getSummaries(10L)).thenReturn(List.of(
                new TaskSummaryResponse(1L, "첫째", TaskStatus.DONE, 100, 0),
                new TaskSummaryResponse(2L, "둘째", TaskStatus.DOING, 50, 1)
        ));

        ProjectDetailResponse response = projectUseCase.getOne(1L, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.description()).isEqualTo("설명");
        assertThat(response.progress().totalTaskCount()).isEqualTo(2);
        assertThat(response.progress().completedTaskCount()).isEqualTo(1);
        assertThat(response.progress().completionPct()).isEqualTo(50);
        assertThat(response.tasks()).extracting(TaskSummaryResponse::title)
                .containsExactly("첫째", "둘째");
    }

    @Test
    @DisplayName("Task가 없는 프로젝트의 완료 비율은 0이다")
    void returnsZeroProgressWhenProjectHasNoTasks() {
        when(projectService.getOne(1L, 10L))
                .thenReturn(project(10L, "프로젝트", "설명", null));
        when(taskService.getSummaries(10L)).thenReturn(List.of());

        ProjectDetailResponse response = projectUseCase.getOne(1L, 10L);

        assertThat(response.progress().totalTaskCount()).isZero();
        assertThat(response.progress().completedTaskCount()).isZero();
        assertThat(response.progress().completionPct()).isZero();
        assertThat(response.tasks()).isEmpty();
    }

    @Test
    @DisplayName("프로젝트를 수정하고 응답 DTO로 변환한다")
    void updatesProjectAndMapsResponse() {
        UpdateProjectRequest request = new UpdateProjectRequest(
                "수정 프로젝트",
                "수정 설명",
                null,
                ProjectStatus.ARCHIVED,
                null
        );
        Project project = project(10L, "기존 프로젝트", "기존 설명", null);
        when(projectService.getOne(1L, 10L)).thenReturn(project);
        when(projectService.update(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectUseCase.update(1L, 10L, request);

        assertThat(response.status()).isEqualTo(ProjectStatus.ARCHIVED);
        verify(projectService).update(argThat(updated ->
                updated.getId().equals(10L)
                        && updated.getName().equals("수정 프로젝트")
                        && updated.getStatus() == ProjectStatus.ARCHIVED
        ));
    }

    private Project project(
            Long id,
            String name,
            String description,
            LocalDate targetDate
    ) {
        return Project.restore(
                id, 1L, null, name, description, targetDate,
                ProjectStatus.IN_PROGRESS, null, null
        );
    }
}
