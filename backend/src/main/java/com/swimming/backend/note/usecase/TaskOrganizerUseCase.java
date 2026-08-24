package com.swimming.backend.note.usecase;

import com.swimming.backend.note.dto.in.TaskOrganizeRequest;
import com.swimming.backend.note.dto.in.TaskOrganizeResponse;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.dto.out.ProjectContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.service.TaskOrganizerService;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskOrganizerUseCase {

    private final TaskService taskService;
    private final TaskOrganizerService taskOrganizerService;

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

    private TaskOrganizeResponse toResponse(
            Map<Long, ProjectContext> projectsById,
            TaskOrganizeResult result
    ) {
        var suggestions = result.suggestions()
                .stream()
                .map(suggestion -> {
                    var project =
                            projectsById.get(suggestion.projectId());

                    if (project == null) {
                        throw new IllegalStateException(
                                "AI returned invalid projectId: "
                                        + suggestion.projectId()
                        );
                    }

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
}
