package com.swimming.backend.note.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmResponse;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.out.FolderContext;
import com.swimming.backend.note.dto.out.LlmCallSnapshot;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.repository.TaskOrganizerRunRepository;
import com.swimming.backend.note.repository.entity.TaskOrganizerRunEntity;
import com.swimming.backend.task.domain.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOrganizerRunServiceTest {

    private final JsonMapper objectMapper = JsonMapper.shared();
    private final TaskOrganizerRunRepository repository = mock(TaskOrganizerRunRepository.class);
    private final TaskOrganizerRunService service =
            new TaskOrganizerRunService(repository, objectMapper);

    @Test
    @DisplayName("Preview 입력·결과와 LLM usage를 한 실행 스냅샷으로 저장한다")
    void storesPreviewRunSnapshot() {
        TaskOrganizerInput input = new TaskOrganizerInput(
                "내일 테스트 작성",
                LocalDate.of(2026, 9, 10),
                List.of(new FolderContext(10L, "Swimming", "개발")),
                List.of(new TaskContext(20L, 10L, "기존 Task", TaskStatus.TODO))
        );
        TaskOrganizeResponse preview = new TaskOrganizeResponse(
                null,
                List.of(new TaskOrganizeResponse.TaskSuggestionResponse(
                        "item-1", "내일 테스트 작성", 10L, "Swimming", "테스트 작성",
                        LocalDate.of(2026, 9, 11)
                )),
                List.of()
        );
        LlmCallSnapshot call = new LlmCallSnapshot(
                "ORGANIZE", "OPENAI", "gpt-5.6-luna", "hash", 120L,
                100, 20L, 5L, 10
        );
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.savePreview(
                1L, 7L, NoteContextType.DEFAULT, null, input, preview, call
        );

        ArgumentCaptor<TaskOrganizerRunEntity> captor =
                ArgumentCaptor.forClass(TaskOrganizerRunEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        TaskOrganizerRunEntity saved = captor.getValue();
        assertThat(saved.getStatus().name()).isEqualTo("PREVIEWED");
        assertThat(saved.getInputSnapshot().path("memo").asString())
                .isEqualTo("내일 테스트 작성");
        assertThat(saved.getInputSnapshot().path("contextId").isNull()).isTrue();
        assertThat(saved.getPreviewSnapshot().path("suggestions").get(0)
                .path("itemId").asString()).isEqualTo("item-1");
        assertThat(saved.getLlmCalls().get(0).path("cacheWriteTokens").asLong())
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("Confirm 시 선택 항목은 승인하고 나머지 Preview 항목은 제외로 기록한다")
    void recordsApprovedAndExcludedItems() {
        TaskOrganizeResponse preview = new TaskOrganizeResponse(
                null,
                List.of(new TaskOrganizeResponse.TaskSuggestionResponse(
                        "approved-item", "원문 A", 10L, "Swimming", "수정 전", null
                )),
                List.of(new TaskOrganizeResponse.UnclassifiedResponse(
                        "excluded-item", "원문 B", "제외할 항목", null
                ))
        );
        TaskOrganizerRunEntity run = TaskOrganizerRunEntity.previewed(
                1L,
                7L,
                objectMapper.createObjectNode(),
                objectMapper.valueToTree(preview),
                objectMapper.createArrayNode()
        );
        when(repository.findByIdAndUserIdAndNoteId(5L, 1L, 7L))
                .thenReturn(Optional.of(run));

        service.confirm(
                1L,
                7L,
                new TaskOrganizeConfirmRequest(
                        5L,
                        7L,
                        List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                "approved-item", "원문 A", 20L, "수정 후"
                        ))
                ),
                List.of(new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                        41L, 20L, "수정 후", TaskStatus.TODO, false, false
                ))
        );

        assertThat(run.getStatus().name()).isEqualTo("CONFIRMED");
        assertThat(run.getFeedbackSnapshot().path("items").toString())
                .contains("\"decision\":\"APPROVED\"")
                .contains("\"finalTitle\":\"수정 후\"")
                .contains("\"finalFolderId\":20")
                .contains("\"createdTaskId\":41")
                .contains("\"decision\":\"EXCLUDED\"");
    }

    @Test
    @DisplayName("Preview에 없는 itemId로 Confirm하면 거절한다")
    void rejectsUnknownPreviewItem() {
        TaskOrganizeResponse preview = new TaskOrganizeResponse(
                null,
                List.of(),
                List.of(new TaskOrganizeResponse.UnclassifiedResponse(
                        "known-item", "원문", "항목", null
                ))
        );
        TaskOrganizerRunEntity run = TaskOrganizerRunEntity.previewed(
                1L, 7L, objectMapper.createObjectNode(),
                objectMapper.valueToTree(preview), objectMapper.createArrayNode()
        );
        when(repository.findByIdAndUserIdAndNoteId(5L, 1L, 7L))
                .thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.confirm(
                1L,
                7L,
                new TaskOrganizeConfirmRequest(
                        5L,
                        7L,
                        List.of(new TaskOrganizeConfirmRequest.ApprovedTaskRequest(
                                "unknown-item", "원문", null, "항목"
                        ))
                ),
                List.of(new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                        41L, null, "항목", TaskStatus.TODO, false, false
                ))
        )).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TASK_ORGANIZER_RUN);

        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
