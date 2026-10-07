package com.swimming.backend.task.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.note.repository.entity.NoteEntity;
import com.swimming.backend.folder.domain.FolderStatus;
import com.swimming.backend.folder.repository.entity.FolderEntity;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.domain.TaskStatus;
import com.swimming.backend.task.dto.projection.PlannedTaskRow;
import com.swimming.backend.task.dto.projection.TaskOrganizerContextRow;
import com.swimming.backend.task.dto.projection.TaskReference;
import com.swimming.backend.task.dto.projection.TaskSummaryRow;
import com.swimming.backend.task.dto.in.NewTaskSpec;
import com.swimming.backend.task.dto.in.TaskSummaryResponse;
import com.swimming.backend.task.repository.TaskRepository;
import com.swimming.backend.task.repository.entity.TaskEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.swimming.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(Long userId, Long folderId, String title) {
        return create(userId, folderId, null, title, false, false, 0L);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(Long userId, Long folderId, String title, boolean priority, boolean urgent) {
        return create(userId, folderId, null, title, priority, urgent, 0L);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task create(
            Long userId,
            Long folderId,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        return create(userId, folderId, null, title, priority, urgent, matrixRank);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task createFromNote(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title
    ) {
        return create(userId, folderId, sourceNoteId, title, false, false, 0L);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task createFromNote(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        return create(userId, folderId, sourceNoteId, title, priority, urgent, matrixRank);
    }

    private Task create(
            Long userId,
            Long folderId,
            Long sourceNoteId,
            String title,
            boolean priority,
            boolean urgent,
            long matrixRank
    ) {
        int nextOrder = nextOrderIdx(userId, folderId);
        Task task = sourceNoteId == null
                ? Task.create(userId, folderId, title, nextOrder, priority, urgent, matrixRank)
                : Task.createFromNote(userId, folderId, sourceNoteId, title, nextOrder, priority, urgent, matrixRank);
        User user = entityManager.getReference(User.class, userId);
        FolderEntity folder = folderId == null
                ? null
                : entityManager.getReference(FolderEntity.class, folderId);
        NoteEntity sourceNote = sourceNoteId == null
                ? null
                : entityManager.getReference(NoteEntity.class, sourceNoteId);
        return taskRepository.saveAndFlush(
                TaskEntity.from(task, user, folder, sourceNote)
        ).toDomain();
    }

    /**
     * 여러 건을 한 번에 만든다.
     *
     * <p>폴더 안 순번은 폴더마다 한 번만 읽고 메모리에서 올린다. 건마다 읽으면 건수만큼
     * 쿼리가 나가고, 직전에 넣은 것이 이미 보이는지에 결과가 달라진다.
     * 돌려주는 순서는 넘겨받은 순서와 같다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Task> createAll(Long userId, List<NewTaskSpec> specs) {
        if (specs.isEmpty()) {
            return List.of();
        }

        User user = entityManager.getReference(User.class, userId);
        Map<Long, Integer> nextOrderByFolder = new HashMap<>();
        List<TaskEntity> entities = new ArrayList<>();
        for (NewTaskSpec spec : specs) {
            int nextOrder = nextOrderByFolder.computeIfAbsent(
                    spec.folderId(), folderId -> nextOrderIdx(userId, folderId));
            nextOrderByFolder.put(spec.folderId(), nextOrder + 1);

            Task task = Task.create(
                    userId, spec.folderId(), spec.title(), nextOrder,
                    spec.priority(), spec.urgent(), spec.matrixRank());
            FolderEntity folder = spec.folderId() == null
                    ? null
                    : entityManager.getReference(FolderEntity.class, spec.folderId());
            entities.add(TaskEntity.from(task, user, folder, null));
        }

        return taskRepository.saveAll(entities).stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    /** 폴더 안(미분류면 사용자 전체)의 다음 순번. */
    private int nextOrderIdx(Long userId, Long folderId) {
        return (folderId == null
                ? taskRepository.findTopByUser_IdAndFolderIsNullAndDeletedFalseOrderByOrderIdxDescIdDesc(userId)
                : taskRepository.findTopByFolder_IdAndDeletedFalseOrderByIdDesc(folderId))
                .map(TaskEntity::getOrderIdx)
                .map(orderIdx -> orderIdx + 1)
                .orElse(0);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getByFolder(Long folderId) {
        return taskRepository.findAllByFolderIdWithFolder(folderId)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getAll(Long userId, Sort sort) {
        return taskRepository.findAllByUser_IdAndDeletedFalse(userId, sort)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskReference> getReferences(Long userId, List<Long> taskIds) {
        return taskRepository.findAllOwnedByIdsIncludingDeleted(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskReference> getActiveReferences(Long userId, List<Long> taskIds) {
        return taskRepository.findAllOwnedActiveByIds(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskSummaryRow> getActiveSummaries(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return taskRepository.findAllOwnedActiveSummariesByIds(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskSummaryRow> getActiveSummaries(Long userId) {
        return taskRepository.findAllOwnedActiveSummaries(userId);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskOrganizerContextRow> getTaskOrganizerContext(Long userId) {
        return taskRepository.findTaskOrganizerContext(
                userId,
                FolderStatus.ARCHIVED
        );
    }

    /** 지정된 폴더만 대상으로 하는 컨텍스트. ARCHIVED 도 포함한다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskOrganizerContextRow> getTaskOrganizerContext(Long userId, List<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return List.of();
        }

        return taskRepository.findTaskOrganizerContextByFolderIds(userId, folderIds);
    }

    /** task 들이 속한 폴더 id. 폴더가 없는 task 는 빠진다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Long> getFolderIds(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return List.of();
        }

        return taskRepository.findFolderIdsByTaskIds(userId, taskIds);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<TaskSummaryResponse> getSummaries(Long folderId) {
        return getByFolder(folderId)
                .stream()
                .map(TaskSummaryResponse::from)
                .toList();
    }

    /**
     * 폴더의 할 일을 최근 순으로 한 페이지 읽는다.
     *
     * @return 요청한 만큼. 다음 장이 있는지는 부르는 쪽이 한 건 더 요청해 판단한다
     */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getPageByFolder(Long folderId, TaskCursorCodec.Decoded cursor, int size) {
        List<TaskEntity> entities = cursor == null
                ? taskRepository.findFolderTaskFirstPage(folderId, PageRequest.of(0, size))
                : taskRepository.findFolderTaskNextPage(
                        folderId, cursor.createdAt(), cursor.taskId(), PageRequest.of(0, size)
                );

        return entities.stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    /** 지워지지 않은 내 할 일들. 순서는 보장하지 않는다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Task> getAllByIds(Long userId, List<Long> taskIds) {
        return taskRepository.findAllByUser_IdAndDeletedFalseAndIdIn(userId, taskIds)
                .stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Task getOne(Long userId, Long taskId) {
        return getOwnedEntity(userId, taskId).toDomain();
    }

    /**
     * 제목·폴더·중요·즉시·캘린더 날짜를 한 번에 바꾼다. 엔티티를 한 번만 읽고 변경 감지로 반영한다.
     * matrixRank는 중요·즉시가 바뀔 때만 필요하고 그 판단과 계산은 유스케이스가 한다.
     * null이면 순서를 건드리지 않는다. changesFolder가 false면 폴더를 그대로 둔다(folderId는 무시).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Task updateInfo(
            Long userId,
            Long taskId,
            String title,
            boolean changesFolder,
            Long folderId,
            boolean priority,
            boolean urgent,
            Long matrixRank,
            LocalDate planDate
    ) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        entity.updatePlanDate(planDate);
        entity.updateTitle(title.trim());
        if (changesFolder) {
            entity.updateFolder(folderId == null
                    ? null
                    : entityManager.getReference(FolderEntity.class, folderId));
        }
        entity.updatePriority(priority);
        entity.updateUrgent(urgent);
        if (matrixRank != null) {
            entity.updateMatrixRank(matrixRank);
        }
        taskRepository.flush();
        return entity.toDomain();
    }

    /** 기간 안의 캘린더에 담긴 할 일. 날짜순이고, 하루 안에서는 최근에 만든 할 일이 먼저다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PlannedTaskRow> getPlannedRows(Long userId, LocalDate fromDate, LocalDate toDate) {
        return taskRepository.findPlannedRows(userId, fromDate, toDate);
    }

    /** 그 날짜에 담긴 할 일의 수. 같은 id는 한 번만 센다. */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public long countPlannedOn(Long userId, LocalDate planDate, List<Long> taskIds) {
        Set<Long> uniqueTaskIds = Set.copyOf(taskIds);
        if (uniqueTaskIds.isEmpty()) {
            return 0;
        }
        return taskRepository.countPlannedOn(userId, planDate, uniqueTaskIds);
    }

    /**
     * 할 일들을 그 날짜의 캘린더에 담는다. 다른 날짜에 담겨 있던 할 일은 이 날짜로 옮겨진다.
     * 한 task는 최대 하나의 날짜를 갖는다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void plan(Long userId, List<Long> taskIds, LocalDate planDate) {
        if (taskIds.isEmpty()) {
            return;
        }
        List<TaskEntity> entities = taskRepository.findAllByUser_IdAndDeletedFalseAndIdIn(userId, taskIds);
        if (entities.size() != Set.copyOf(taskIds).size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        entities.forEach(entity -> entity.updatePlanDate(planDate));
        taskRepository.flush();
    }

    /** 그 날짜의 캘린더에서 뺀다. 다른 날짜에 담긴 할 일이면 캘린더 항목이 없는 것으로 본다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void unplan(Long userId, Long taskId, LocalDate planDate) {
        TaskEntity entity = taskRepository.findByIdAndUser_IdAndDeletedFalse(taskId, userId)
                .filter(candidate -> planDate.equals(candidate.getPlanDate()))
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_PLAN_ITEM_NOT_FOUND));
        entity.updatePlanDate(null);
        taskRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task updateTitle(Long userId, Long taskId, String title) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        entity.updateTitle(title.trim());
        taskRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Task updateStatus(Long userId, Long taskId, TaskStatus status) {
        TaskEntity entity = getOwnedEntity(userId, taskId);
        entity.updateStatus(status);
        taskRepository.flush();
        return entity.toDomain();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAll(Long userId, List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        if (taskRepository.softDeleteAllOwnedByIds(userId, taskIds) != taskIds.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void updateStatuses(Long userId, Map<Long, TaskStatus> statusByTaskId) {
        if (statusByTaskId.isEmpty()) {
            return;
        }
        int updatedCount = statusByTaskId.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ))
                .entrySet()
                .stream()
                .mapToInt(entry -> taskRepository.updateOwnedStatuses(
                        userId,
                        entry.getValue(),
                        entry.getKey()
                ))
                .sum();
        if (updatedCount != statusByTaskId.size()) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
    }

    private TaskEntity getOwnedEntity(Long userId, Long taskId) {
        return taskRepository.findByIdAndUser_IdAndDeletedFalse(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }

}
