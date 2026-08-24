package com.swimming.backend.note.usecase;

import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.service.TaskOrganizerService;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOrganizerUseCaseTest {

    private TaskService taskService;
    private TaskOrganizerService taskOrganizerService;
    private TaskOrganizerUseCase taskOrganizerUseCase;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        taskOrganizerService = mock(TaskOrganizerService.class);
        taskOrganizerUseCase = new TaskOrganizerUseCase(
                taskService,
                taskOrganizerService
        );
    }

    @Test
    @DisplayName("프로젝트와 Task를 한 번에 조회해 Preview 응답으로 변환한다")
    void previewsTasksFromJoinedProjectContext() {
        when(taskService.getTaskOrganizerContext(1L)).thenReturn(List.of(
                row(10L, "Swimming", 1L, "Note API 연결", TaskStatus.DOING),
                row(10L, "Swimming", 2L, "Preview 화면", TaskStatus.TODO),
                row(20L, "Task 없는 프로젝트", null, null, null)
        ));
        when(taskOrganizerService.organize(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TaskOrganizeResult(
                        List.of(new TaskOrganizeResult.TaskSuggestion(
                                "CREATE_TASK",
                                "할 일 정리 버튼 연결",
                                10L,
                                "Task Organizer API 연결",
                                0.95
                        )),
                        List.of(new TaskOrganizeResult.UnclassifiedItem(
                                "운동화 주문",
                                "운동화 주문"
                        ))
                ));

        TaskOrganizeResponse response = taskOrganizerUseCase.preview(
                1L,
                new TaskOrganizeRequest("할 일 정리 버튼 연결\n운동화 주문")
        );

        ArgumentCaptor<TaskOrganizerInput> inputCaptor =
                ArgumentCaptor.forClass(TaskOrganizerInput.class);
        verify(taskOrganizerService).organize(inputCaptor.capture());
        TaskOrganizerInput input = inputCaptor.getValue();

        assertThat(input.projects()).extracting(project -> project.id())
                .containsExactly(10L, 20L);
        assertThat(input.tasks()).extracting(task -> task.id())
                .containsExactly(1L, 2L);
        assertThat(response.suggestions()).containsExactly(
                new TaskOrganizeResponse.TaskSuggestionResponse(
                        "할 일 정리 버튼 연결",
                        10L,
                        "Swimming",
                        "Task Organizer API 연결"
                )
        );
        assertThat(response.unclassified()).containsExactly(
                new TaskOrganizeResponse.UnclassifiedResponse(
                        "운동화 주문",
                        "운동화 주문"
                )
        );
        verify(taskService).getTaskOrganizerContext(1L);
    }

    private TaskOrganizerContextRow row(
            Long projectId,
            String projectName,
            Long taskId,
            String taskTitle,
            TaskStatus taskStatus
    ) {
        return new TaskOrganizerContextRow(
                projectId,
                projectName,
                "설명",
                taskId,
                taskTitle,
                taskStatus
        );
    }
}
