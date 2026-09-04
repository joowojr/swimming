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
import com.swimming.backend.note.dto.out.TaskExtractResult;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.dto.out.FolderContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.service.TaskOrganizerService;
import com.swimming.backend.note.service.NoteService;
import com.swimming.backend.folder.service.FolderService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.task.service.TaskOrderingService;
import com.swimming.backend.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskOrganizerUseCase {

    private static final int MAX_TASKS_PER_FOLDER = 10;

    private final TaskService taskService;
    private final TaskOrderingService taskOrderingService;
    private final TaskOrganizerService taskOrganizerService;
    private final NoteService noteService;
    private final FolderService folderService;
    private final SessionService sessionService;

    public TaskOrganizeResponse preview(
            Long userId,
            TaskOrganizeRequest request
    ) {
        NoteContextType contextType = request.contextTypeOrDefault();

        List<TaskOrganizerContextRow> contextRows =
                contextRowsFor(userId, request, contextType);

        Map<Long, FolderContext> foldersById = contextRows.stream()
                .collect(Collectors.toMap(
                        TaskOrganizerContextRow::folderId,
                        row -> new FolderContext(
                                row.folderId(),
                                row.folderName(),
                                row.folderDescription()
                        ),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));

        TaskOrganizerInput input = new TaskOrganizerInput(
                request.memo(),
                List.copyOf(foldersById.values()),
                recentTasksByFolder(contextRows)
        );

        // 폴더가 하나로 정해져 있으면 분류를 시키지 않는다. 추출만 하고 그 폴더로 확정한다.
        if (contextType == NoteContextType.FOLDER) {
            return toResponse(
                    foldersById.get(request.contextId()),
                    taskOrganizerService.extract(input)
            );
        }

        TaskOrganizeResult result =
                taskOrganizerService.organize(input);

        return toResponse(foldersById, result);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public TaskOrganizeConfirmResponse confirm(
            Long userId,
            TaskOrganizeConfirmRequest request
    ) {
        Note note = noteService.getOne(
                userId,
                request.noteId(),
                NoteStatus.ACTIVE
        );

        request.tasks().stream()
                .map(TaskOrganizeConfirmRequest.ApprovedTaskRequest::folderId)
                .filter(folderId -> folderId != null)
                .distinct()
                .forEach(folderId ->
                        folderService.validateOwnership(userId, folderId)
                );

        validateApprovedSources(
                note.getContent(),
                request.tasks()
        );

        List<TaskOrganizeConfirmResponse.CreatedTaskResponse> createdTasks =
                request.tasks().stream()
                        .map(approvedTask -> createTask(userId, request.noteId(), approvedTask))
                        .toList();

        return new TaskOrganizeConfirmResponse(createdTasks);
    }
    
    /**
     * 참조 범위를 컨텍스트에 맞춰 좁힌다.
     *
     */
    private List<TaskOrganizerContextRow> contextRowsFor(
            Long userId,
            TaskOrganizeRequest request,
            NoteContextType contextType
    ) {
        if (contextType == NoteContextType.DEFAULT) {
            if (request.contextId() != null) {
                throw new BusinessException(ErrorCode.INVALID_TASK_ORGANIZER_CONTEXT);
            }
            return taskService.getTaskOrganizerContext(userId);
        }

        Long contextId = request.contextId();
        if (contextId == null) {
            throw new BusinessException(ErrorCode.INVALID_TASK_ORGANIZER_CONTEXT);
        }

        List<Long> folderIds = switch (contextType) {
            case FOLDER -> {
                folderService.validateOwnership(userId, contextId);
                yield List.of(contextId);
            }
            case SESSION -> taskService.getFolderIds(
                    userId,
                    sessionService.getTaskIds(userId, contextId)
            );
            case DEFAULT -> throw new IllegalStateException("unreachable");
        };

        if (folderIds.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_TASK_ORGANIZER_CONTEXT);
        }

        return taskService.getTaskOrganizerContext(userId, folderIds);
    }

    private List<TaskContext> recentTasksByFolder(List<TaskOrganizerContextRow> contextRows) {
        return contextRows.stream()
                .filter(row -> row.taskId() != null)
                .collect(Collectors.groupingBy(
                        TaskOrganizerContextRow::folderId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .flatMap(rows -> rows.stream().limit(MAX_TASKS_PER_FOLDER))
                .map(row -> new TaskContext(
                        row.taskId(),
                        row.folderId(),
                        row.taskTitle(),
                        row.taskStatus()
                ))
                .toList();
    }

    private TaskOrganizeResponse toResponse(
            Map<Long, FolderContext> foldersById,
            TaskOrganizeResult result
    ) {
        var suggestions = result.suggestions()
                .stream()
                .filter(suggestion ->
                        foldersById.containsKey(suggestion.folderId())
                )
                .map(suggestion -> {
                    var folder =
                            foldersById.get(suggestion.folderId());

                    return new TaskOrganizeResponse.TaskSuggestionResponse(
                            suggestion.sourceText(),
                            folder.id(),
                            folder.name(),
                            suggestion.title()
                    );
                })
                .toList();

        return new TaskOrganizeResponse(
                suggestions,
                result.unclassified()
                        .stream()
                        .map(item -> new TaskOrganizeResponse.UnclassifiedResponse(
                                item.sourceText(),
                                item.title()
                        ))
                        .toList()
        );
    }

    /** 추출 결과는 폴더가 이미 정해져 있으므로 모든 항목이 그 폴더로 간다. */
    private TaskOrganizeResponse toResponse(
            FolderContext folder,
            TaskExtractResult result
    ) {
        var suggestions = result.tasks()
                .stream()
                .map(task -> new TaskOrganizeResponse.TaskSuggestionResponse(
                        task.sourceText(),
                        folder.id(),
                        folder.name(),
                        task.title()
                ))
                .toList();

        return new TaskOrganizeResponse(
                suggestions,
                result.unclassified()
                        .stream()
                        .map(item -> new TaskOrganizeResponse.UnclassifiedResponse(
                                item.sourceText(),
                                item.title()
                        ))
                        .toList()
        );
    }

    private TaskOrganizeConfirmResponse.CreatedTaskResponse createTask(
            Long userId,
            Long sourceNoteId,
            TaskOrganizeConfirmRequest.ApprovedTaskRequest approvedTask
    ) {
        long matrixRank = taskOrderingService.nextRank(userId, false, false);
        Task task = taskService.createFromNote(
                userId,
                approvedTask.folderId(),
                sourceNoteId,
                approvedTask.title(),
                false,
                false,
                matrixRank
        );
        return new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                task.getId(),
                task.getFolderId(),
                task.getTitle(),
                task.getStatus(),
                task.isPriority(),
                task.isUrgent()
        );
    }

    private void validateApprovedSources(
            String content,
            List<TaskOrganizeConfirmRequest.ApprovedTaskRequest> approvedTasks
    ) {
        String unmatchedContent = content;

        for (var approvedTask : approvedTasks) {
            List<String> sourceParts = approvedTask.sourceText()
                    .lines()
                    .filter(sourcePart -> !sourcePart.isBlank())
                    .toList();

            if (sourceParts.isEmpty()) {
                throw invalidSelection();
            }

            for (String sourcePart : sourceParts) {
                int sourceIndex = unmatchedContent.indexOf(sourcePart);
                if (sourceIndex < 0) {
                    throw invalidSelection();
                }
                unmatchedContent = unmatchedContent.substring(0, sourceIndex)
                        + unmatchedContent.substring(sourceIndex + sourcePart.length());
            }
        }
    }

    private BusinessException invalidSelection() {
        return new BusinessException(ErrorCode.INVALID_TASK_ORGANIZER_SELECTION);
    }
}
