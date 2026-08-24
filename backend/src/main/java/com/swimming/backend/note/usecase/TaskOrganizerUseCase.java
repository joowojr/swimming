package com.swimming.backend.note.usecase;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.domain.Note;
import com.swimming.backend.note.domain.NoteStatus;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeConfirmResponse;
import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.dto.out.ProjectContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.service.TaskOrganizerService;
import com.swimming.backend.note.service.NoteService;
import com.swimming.backend.project.service.ProjectService;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.service.TaskService;
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

    private final TaskService taskService;
    private final TaskOrganizerService taskOrganizerService;
    private final NoteService noteService;
    private final ProjectService projectService;

    public TaskOrganizeResponse preview(
            Long userId,
            TaskOrganizeRequest request
    ) {
        List<TaskOrganizerContextRow> contextRows =
                taskService.getTaskOrganizerContext(userId);

        Map<Long, ProjectContext> projectsById = contextRows.stream()
                .collect(Collectors.toMap(
                        TaskOrganizerContextRow::projectId,
                        row -> new ProjectContext(
                                row.projectId(),
                                row.projectName(),
                                row.projectDescription()
                        ),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));

        TaskOrganizerInput input = new TaskOrganizerInput(
                request.memo(),
                List.copyOf(projectsById.values()),
                contextRows.stream()
                        .filter(row -> row.taskId() != null)
                        .map(row ->
                                new TaskContext(
                                        row.taskId(),
                                        row.projectId(),
                                        row.taskTitle(),
                                        row.taskStatus()
                                )
                        )
                        .toList()
        );

        TaskOrganizeResult result =
                taskOrganizerService.organize(input);

        return toResponse(projectsById, result);
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
                .map(TaskOrganizeConfirmRequest.ApprovedTaskRequest::projectId)
                .distinct()
                .forEach(projectId ->
                        projectService.validateOwnership(userId, projectId)
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

    private TaskOrganizeResponse toResponse(
            Map<Long, ProjectContext> projectsById,
            TaskOrganizeResult result
    ) {
        var suggestions = result.suggestions()
                .stream()
                .filter(suggestion ->
                        projectsById.containsKey(suggestion.projectId())
                )
                .map(suggestion -> {
                    var project =
                            projectsById.get(suggestion.projectId());

                    return new TaskOrganizeResponse.TaskSuggestionResponse(
                            suggestion.sourceText(),
                            project.id(),
                            project.name(),
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

    private TaskOrganizeConfirmResponse.CreatedTaskResponse createTask(
            Long userId,
            Long sourceNoteId,
            TaskOrganizeConfirmRequest.ApprovedTaskRequest approvedTask
    ) {
        Task task = taskService.createFromNote(
                userId,
                approvedTask.projectId(),
                sourceNoteId,
                approvedTask.title()
        );
        return new TaskOrganizeConfirmResponse.CreatedTaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle()
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
