package com.swimming.backend.note.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.domain.NoteContextType;
import com.swimming.backend.note.domain.TaskOrganizerRunStatus;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmResponse;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.out.LlmCallSnapshot;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.repository.TaskOrganizerRunRepository;
import com.swimming.backend.note.repository.entity.TaskOrganizerRunEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskOrganizerRunService {

    private final TaskOrganizerRunRepository repository;
    private final ObjectMapper objectMapper;

    public TaskOrganizerRunService(
            TaskOrganizerRunRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Long savePreview(
            Long userId,
            Long noteId,
            NoteContextType contextType,
            Long contextId,
            TaskOrganizerInput input,
            TaskOrganizeResponse preview,
            LlmCallSnapshot call
    ) {
        var inputSnapshot = new InputSnapshot(
                input.memo(),
                input.currentDate(),
                contextType,
                contextId,
                input.folders(),
                input.tasks()
        );
        TaskOrganizerRunEntity entity = TaskOrganizerRunEntity.previewed(
                userId,
                noteId,
                objectMapper.valueToTree(inputSnapshot),
                objectMapper.valueToTree(preview),
                objectMapper.valueToTree(List.of(call))
        );
        return repository.saveAndFlush(entity).getId();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void confirm(
            Long userId,
            Long noteId,
            TaskOrganizeConfirmRequest request,
            List<TaskOrganizeConfirmResponse.CreatedTaskResponse> createdTasks
    ) {
        TaskOrganizerRunEntity run = repository
                .findByIdAndUserIdAndNoteId(request.runId(), userId, noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TASK_ORGANIZER_RUN));
        if (run.getStatus() != TaskOrganizerRunStatus.PREVIEWED) {
            throw new BusinessException(ErrorCode.INVALID_TASK_ORGANIZER_RUN);
        }

        Map<String, String> previewSourceByItemId = new LinkedHashMap<>();
        run.getPreviewSnapshot().path("suggestions")
                .forEach(item -> previewSourceByItemId.put(
                        item.path("itemId").asString(), item.path("sourceText").asString()
                ));
        run.getPreviewSnapshot().path("unclassified")
                .forEach(item -> previewSourceByItemId.put(
                        item.path("itemId").asString(), item.path("sourceText").asString()
                ));

        Map<String, TaskOrganizeConfirmRequest.ApprovedTaskRequest> approvedByItemId =
                request.tasks().stream().collect(Collectors.toMap(
                        TaskOrganizeConfirmRequest.ApprovedTaskRequest::itemId,
                        Function.identity(),
                        (left, right) -> {
                            throw new BusinessException(ErrorCode.INVALID_TASK_ORGANIZER_RUN);
                        }
                ));
        boolean sourceMismatch = approvedByItemId.entrySet().stream().anyMatch(entry ->
                !entry.getValue().sourceText().equals(previewSourceByItemId.get(entry.getKey()))
        );
        if (!previewSourceByItemId.keySet().containsAll(approvedByItemId.keySet())
                || sourceMismatch
                || createdTasks.size() != request.tasks().size()) {
            throw new BusinessException(ErrorCode.INVALID_TASK_ORGANIZER_RUN);
        }

        Map<String, TaskOrganizeConfirmResponse.CreatedTaskResponse> createdByItemId =
                java.util.stream.IntStream.range(0, request.tasks().size())
                        .boxed()
                        .collect(Collectors.toMap(
                                index -> request.tasks().get(index).itemId(),
                                createdTasks::get
                        ));
        List<FeedbackItem> feedbackItems = previewSourceByItemId.keySet().stream()
                .map(itemId -> {
                    var approved = approvedByItemId.get(itemId);
                    var created = createdByItemId.get(itemId);
                    return approved == null
                            ? FeedbackItem.excluded(itemId)
                            : FeedbackItem.approved(itemId, approved, created.id());
                })
                .toList();
        run.confirm(objectMapper.valueToTree(Map.of("items", feedbackItems)));
    }

    private record FeedbackItem(
            String itemId,
            String decision,
            String finalTitle,
            Long finalFolderId,
            Long createdTaskId
    ) {
        static FeedbackItem excluded(String itemId) {
            return new FeedbackItem(itemId, "EXCLUDED", null, null, null);
        }

        static FeedbackItem approved(
                String itemId,
                TaskOrganizeConfirmRequest.ApprovedTaskRequest request,
                Long createdTaskId
        ) {
            return new FeedbackItem(
                    itemId, "APPROVED", request.title(), request.folderId(), createdTaskId
            );
        }
    }

    private record InputSnapshot(
            String memo,
            java.time.LocalDate currentDate,
            NoteContextType contextType,
            Long contextId,
            List<com.swimming.backend.note.dto.out.FolderContext> folders,
            List<com.swimming.backend.note.dto.out.TaskContext> tasks
    ) {
    }
}
