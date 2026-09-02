package com.swimming.backend.note.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.domain.Note;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmResponse;
import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.service.TaskOrganizerService;
import com.swimming.backend.note.service.NoteService;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOrganizerUseCaseTest {

    private TaskService taskService;
    private TaskOrderingService taskOrderingService;
    private TaskOrganizerService taskOrganizerService;
    private NoteService noteService;
    private FolderService folderService;
    private TaskOrganizerUseCase taskOrganizerUseCase;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        taskOrderingService = mock(TaskOrderingService.class);
        taskOrganizerService = mock(TaskOrganizerService.class);
        noteService = mock(NoteService.class);
        folderService = mock(FolderService.class);
        taskOrganizerUseCase = new TaskOrganizerUseCase(
                taskService,
                taskOrderingService,
                taskOrganizerService,
                noteService,
                folderService
        );
    }

    @Test
    @DisplayName("프로젝트와 Task를 한 번에 조회해 Preview 응답으로 변환한다")
    void previewsTasksFromJoinedFolderContext() {
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
                                "Task Organizer API 연결"
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

        assertThat(input.folders()).extracting(folder -> folder.id())
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

    @Test
    @DisplayName("LLM이 반환한 Folder ID가 사용자 Folder와 일치하는 suggestion만 Preview에 포함한다")
    void filtersSuggestionsWithUnknownFolderIds() {
        when(taskService.getTaskOrganizerContext(1L)).thenReturn(List.of(
                row(10L, "Swimming", null, null, null)
        ));
        when(taskOrganizerService.organize(any()))
                .thenReturn(new TaskOrganizeResult(
                        List.of(
                                new TaskOrganizeResult.TaskSuggestion(
                                        "CREATE_TASK",
                                        "정상 원문",
                                        10L,
                                        "정상 Task"
                                ),
                                new TaskOrganizeResult.TaskSuggestion(
                                        "CREATE_TASK",
                                        "잘못된 ID 원문",
                                        999L,
                                        "제외할 Task"
                                ),
                                new TaskOrganizeResult.TaskSuggestion(
                                        "CREATE_TASK",
                                        "ID 없는 원문",
                                        null,
                                        "제외할 Task"
                                )
                        ),
                        List.of(new TaskOrganizeResult.UnclassifiedItem(
                                "미분류 원문",
                                "미분류 항목"
                        ))
                ));

        TaskOrganizeResponse response = taskOrganizerUseCase.preview(
                1L,
                new TaskOrganizeRequest("정상 원문\n잘못된 ID 원문\nID 없는 원문\n미분류 원문")
        );

        assertThat(response.suggestions()).containsExactly(
                new TaskOrganizeResponse.TaskSuggestionResponse(
                        "정상 원문",
                        10L,
                        "Swimming",
                        "정상 Task"
                )
        );
        assertThat(response.unclassified()).containsExactly(
                new TaskOrganizeResponse.UnclassifiedResponse(
                        "미분류 원문",
                        "미분류 항목"
                )
        );
    }

    @Test
    @DisplayName("승인한 Task를 생성하고 원문 Note는 변경하지 않는다")
    void confirmsSelectedTasksWithoutUpdatingSourceNote() {
        Note note = note("장소조회 캐시 테스트 아직 못함\n운동화 주문");
        when(noteService.getOne(1L, 7L, NoteStatus.ACTIVE)).thenReturn(note);
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.createFromNote(1L, 10L, 7L, "장소 조회 캐시 테스트", false, false, 1024L))
                .thenReturn(Task.restore(
                        41L,
                        1L,
                        10L,
                        7L,
                        "장소 조회 캐시 테스트",
                        TaskStatus.TODO,
                        0,
                        null,
                        null
                ));

        TaskOrganizeConfirmResponse response = taskOrganizerUseCase.confirm(
                1L,
                new TaskOrganizeConfirmRequest(
                        7L,
                        List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                "장소조회 캐시 테스트 아직 못함",
                                10L,
                                "장소 조회 캐시 테스트"
                        ))
                )
        );

        assertThat(response.createdTasks()).containsExactly(
                new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                        41L,
                        10L,
                        "장소 조회 캐시 테스트"
                )
        );
        assertThat(note.getContent()).isEqualTo("장소조회 캐시 테스트 아직 못함\n운동화 주문");
        assertThat(note.isDeleted()).isFalse();
        verify(folderService).validateOwnership(1L, 10L);
        verify(taskService).createFromNote(1L, 10L, 7L, "장소 조회 캐시 테스트", false, false, 1024L);
    }

    @Test
    @DisplayName("미분류 항목은 프로젝트 소유권 검증 없이 사용자 소유 Task로 생성한다")
    void confirmsUnclassifiedTaskWithoutFolder() {
        Note note = note("운동화 주문");
        when(noteService.getOne(1L, 7L, NoteStatus.ACTIVE)).thenReturn(note);
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.createFromNote(1L, null, 7L, "운동화 주문", false, false, 1024L))
                .thenReturn(Task.restore(
                        42L,
                        1L,
                        null,
                        7L,
                        "운동화 주문",
                        TaskStatus.TODO,
                        0,
                        null,
                        null
                ));

        TaskOrganizeConfirmResponse response = taskOrganizerUseCase.confirm(
                1L,
                new TaskOrganizeConfirmRequest(
                        7L,
                        List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                "운동화 주문",
                                null,
                                "운동화 주문"
                        ))
                )
        );

        assertThat(response.createdTasks()).containsExactly(
                new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                        42L,
                        null,
                        "운동화 주문"
                )
        );
        verify(folderService, never()).validateOwnership(any(), any());
        verify(taskService).createFromNote(1L, null, 7L, "운동화 주문", false, false, 1024L);
    }

    @Test
    @DisplayName("분류·미분류 항목이 섞이면 값이 있는 프로젝트만 소유권을 확인한다")
    void validatesOnlyPresentFolderIds() {
        Note note = note("캐시 테스트\n운동화 주문");
        when(noteService.getOne(1L, 7L, NoteStatus.ACTIVE)).thenReturn(note);
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.createFromNote(1L, 10L, 7L, "캐시 테스트", false, false, 1024L))
                .thenReturn(Task.restore(
                        41L, 1L, 10L, 7L, "캐시 테스트",
                        TaskStatus.TODO, 0, null, null
                ));
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.createFromNote(1L, null, 7L, "운동화 주문", false, false, 1024L))
                .thenReturn(Task.restore(
                        42L, 1L, null, 7L, "운동화 주문",
                        TaskStatus.TODO, 0, null, null
                ));

        taskOrganizerUseCase.confirm(
                1L,
                new TaskOrganizeConfirmRequest(
                        7L,
                        List.of(
                                new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                        "캐시 테스트", 10L, "캐시 테스트"
                                ),
                                new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                        "운동화 주문", null, "운동화 주문"
                                )
                        )
                )
        );

        verify(folderService).validateOwnership(1L, 10L);
        verify(taskService).createFromNote(1L, 10L, 7L, "캐시 테스트", false, false, 1024L);
        verify(taskService).createFromNote(1L, null, 7L, "운동화 주문", false, false, 1024L);
    }

    @Test
    @DisplayName("다른 사용자의 Folder가 포함되면 미분류 Task도 생성하기 전에 요청 전체를 거부한다")
    void rejectsAnotherUsersFolderBeforeCreatingAnyTask() {
        when(noteService.getOne(1L, 7L, NoteStatus.ACTIVE))
                .thenReturn(note("다른 프로젝트 Task\n운동화 주문"));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FOLDER_NOT_FOUND))
                .when(folderService).validateOwnership(1L, 99L);

        assertThatThrownBy(() -> taskOrganizerUseCase.confirm(
                1L,
                new TaskOrganizeConfirmRequest(
                        7L,
                        List.of(
                                new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                        "다른 프로젝트 Task", 99L, "다른 프로젝트 Task"
                                ),
                                new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                        "운동화 주문", null, "운동화 주문"
                                )
                        )
                )
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOLDER_NOT_FOUND));

        verify(taskService, never()).createFromNote(
                any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyLong());
    }

    @Test
    @DisplayName("승인한 Task가 원문 전체를 사용해도 Note를 유지한다")
    void keepsNoteWhenEverySourceIsApproved() {
        Note note = note("장소조회 캐시 테스트 아직 못함");
        when(noteService.getOne(1L, 7L, NoteStatus.ACTIVE)).thenReturn(note);
        when(taskOrderingService.nextRank(1L, false, false)).thenReturn(1024L);
        when(taskService.createFromNote(1L, 10L, 7L, "장소 조회 캐시 테스트", false, false, 1024L))
                .thenReturn(Task.restore(
                        41L,
                        1L,
                        10L,
                        7L,
                        "장소 조회 캐시 테스트",
                        TaskStatus.TODO,
                        0,
                        null,
                        null
                ));

        taskOrganizerUseCase.confirm(
                1L,
                new TaskOrganizeConfirmRequest(
                        7L,
                        List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                "장소조회 캐시 테스트 아직 못함",
                                10L,
                                "장소 조회 캐시 테스트"
                        ))
                )
        );

        assertThat(note.getContent()).isEqualTo("장소조회 캐시 테스트 아직 못함");
        assertThat(note.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("승인 원문이 현재 Note와 일치하지 않으면 Task를 생성하지 않는다")
    void rejectsSourceThatIsNotInCurrentNote() {
        when(noteService.getOne(1L, 7L, NoteStatus.ACTIVE))
                .thenReturn(note("현재 원문"));

        assertThatThrownBy(() -> taskOrganizerUseCase.confirm(
                1L,
                new TaskOrganizeConfirmRequest(
                        7L,
                        List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                "변경된 원문",
                                10L,
                                "Task"
                        ))
                )
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TASK_ORGANIZER_SELECTION)
        );

        verify(taskService, never()).createFromNote(
                any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyLong());
    }

    private TaskOrganizerContextRow row(
            Long folderId,
            String folderName,
            Long taskId,
            String taskTitle,
            TaskStatus taskStatus
    ) {
        return new TaskOrganizerContextRow(
                folderId,
                folderName,
                "설명",
                taskId,
                taskTitle,
                taskStatus
        );
    }

    private Note note(String content) {
        return Note.restore(
                7L,
                1L,
                content,
                NoteStatus.ACTIVE,
                false,
                NoteContextType.DEFAULT,
                null,
                null,
                null,
                null
        );
    }
}
