package com.swimming.backend.agentwork.usecase;
import com.swimming.backend.agentwork.exception.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.domain.StatusSource;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.dto.in.StartAgentWorkRequest;
import com.swimming.backend.agentwork.dto.in.AgentReportRequest;
import com.swimming.backend.agentwork.service.AgentWorkItemWriteService;
import com.swimming.backend.agentwork.dto.in.AddWorkItemRequest;
import com.swimming.backend.agentwork.dto.out.StartAgentWorkResult;
import com.swimming.backend.agentwork.repository.AgentSessionEventReadRepository;
import com.swimming.backend.agentwork.repository.AgentSessionReadRepository;
import com.swimming.backend.agentwork.repository.AgentWorkItemReadRepository;
import com.swimming.backend.agentwork.event.AgentWorkStatusChangedEvent;
import com.swimming.backend.agentwork.service.AgentSessionEventWriteService;
import com.swimming.backend.agentwork.service.AgentWorkSseService;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-session-start;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
class AgentSessionStartIntegrationTest {
    @Autowired private AgentSessionReportUseCase useCase;
    @Autowired private AgentWorkItemWriteService workItemService;
    @Autowired private AgentWorkItemReadRepository workItems;
    @Autowired private AgentSessionReadRepository sessions;
    @Autowired private AgentSessionEventReadRepository events;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private AgentSessionEventWriteService eventService;
    @MockitoSpyBean private AgentWorkSseService sseService;

    private Long userId;
    private Task task;

    @BeforeEach
    void setUp() {
        // 각 테스트는 독립된 Task를 사용한다. 실제 UseCase의 트랜잭션 커밋·롤백을 검증한다.
        userId = newUser();
        task = taskService.create(userId, null, "Agent 시작 검증");
    }

    @Test
    @DisplayName("최초 시작은 보드 항목·단일 세션·STARTED 이벤트를 함께 저장한다")
    void startsAndRecordsEvent() {
        StartAgentWorkResult result = useCase.start(userId, request(task.getId(), AgentType.CLAUDE_CODE, null));
        assertThat(result.created()).isTrue();
        assertThat(result.session().status()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(result.session().statusSource()).isEqualTo(StatusSource.MCP_REPORT);
        assertThat(result.session().instruction()).isNull();
        assertThat(result.session().summary()).isNull();
        assertThat(result.session().completedAt()).isNull();
        assertThat(result.session().lastSeenAt()).isEqualTo(result.session().startedAt());
        var item = workItems.findById(result.session().workItemIds().getFirst()).orElseThrow();
        assertThat(item.getUserId()).isEqualTo(userId);
        assertThat(item.getResourceId()).isEqualTo(task.getId().toString());
        assertThat(item.getCreatedAt()).isNotNull();
        var event = events.findAll().stream()
                .filter(e -> e.getSession().getId().equals(result.session().id())).findFirst().orElseThrow();
        assertThat(event.getAgentType()).isEqualTo(AgentType.CLAUDE_CODE);
        assertThat(event.getEventType().name()).isEqualTo("STARTED");
        assertThat(event.getSource()).isEqualTo(StatusSource.MCP_REPORT);
        assertThat(event.getPayload()).containsKey("instruction");
        assertThat(event.getPayload().get("instruction")).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"COMPLETED", "FAILED"})
    @DisplayName("종료 세션은 같은 카드·세션으로 재시작하고 이전 Agent 이벤트를 유지한다")
    void restartsWithSameIdentity(AgentWorkStatus status) {
        var first = useCase.start(userId, request(task.getId(), AgentType.CLAUDE_CODE, "첫 실행"));
        setStatus(first, status);
        var result = useCase.start(userId, request(task.getId(), AgentType.CODEX, "재실행"));
        assertThat(result.created()).isFalse();
        assertThat(result.session().id()).isEqualTo(first.session().id());
        assertThat(result.session().workItemIds().getFirst()).isEqualTo(first.session().workItemIds().getFirst());
        assertThat(result.session().agentType()).isEqualTo(AgentType.CODEX);
        assertThat(result.session().instruction()).isEqualTo("재실행");
        assertThat(result.session().summary()).isNull();
        assertThat(result.session().completedAt()).isNull();
        var stored = sessions.findById(result.session().id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(stored.getProgressSnapshot()).isNull();
        assertThat(stored.getResultSnapshot()).isNull();
        assertThat(stored.getErrorSnapshot()).isNull();
        var history = events.findAll().stream()
                .filter(e -> e.getSession().getId().equals(result.session().id()))
                .sorted(java.util.Comparator.comparing(e -> e.getId())).toList();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getAgentType()).isEqualTo(AgentType.CLAUDE_CODE);
        assertThat(history.get(0).getPayload()).containsEntry("instruction", "첫 실행");
        assertThat(history.get(1).getAgentType()).isEqualTo(AgentType.CODEX);
        assertThat(history.get(1).getPayload()).containsEntry("instruction", "재실행");
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"WORKING", "WAITING", "UNKNOWN"})
    @DisplayName("진행 중인 세션의 중복 시작은 거부하고 Agent·상태·이벤트를 유지한다")
    void rejectsStartWhileActive(AgentWorkStatus status) {
        var first = useCase.start(userId, request(task.getId(), AgentType.CLAUDE_CODE, "첫 실행"));
        setStatus(first, status);
        long beforeEvents = events.count();
        assertBusinessError(() -> useCase.start(userId, request(task.getId(), AgentType.CODEX, "중복")),
                AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS);
        var stored = sessions.findById(first.session().id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(status);
        assertThat(stored.getAgentType()).isEqualTo(AgentType.CLAUDE_CODE);
        assertThat(stored.getInstruction()).isEqualTo("첫 실행");
        assertThat(events.count()).isEqualTo(beforeEvents);
    }

    @Test
    @DisplayName("다른 사용자의 Task는 Agent Work 데이터를 만들지 않고 거부한다")
    void rejectsOtherUsersTask() {
        long beforeItems = workItems.count();
        long beforeSessions = sessions.count();
        long beforeEvents = events.count();
        assertBusinessError(() -> useCase.start(newUser(), request(task.getId(), AgentType.CODEX, null)),
                ErrorCode.TASK_NOT_FOUND);
        assertThat(workItems.count()).isEqualTo(beforeItems);
        assertThat(sessions.count()).isEqualTo(beforeSessions);
        assertThat(events.count()).isEqualTo(beforeEvents);
        // 롤백된 변경은 SSE로 알리지 않는다.
        verify(sseService, never()).publish(any());
    }

    @Test
    @DisplayName("이미 보드에 등록된 Task가 삭제되면 재시작을 거부한다")
    void rejectsDeletedTask() {
        var first = useCase.start(userId, request(task.getId(), AgentType.CLAUDE_CODE, null));
        setStatus(first, AgentWorkStatus.COMPLETED);
        taskService.deleteAll(userId, java.util.List.of(task.getId()));
        assertBusinessError(() -> useCase.start(userId, request(task.getId(), AgentType.CODEX, null)),
                ErrorCode.TASK_NOT_FOUND);
        assertThat(sessions.findById(first.session().id()).orElseThrow().getStatus())
                .isEqualTo(AgentWorkStatus.COMPLETED);
    }

    @Test
    @DisplayName("표기가 다른 동일 Task 식별자로 시작해도 새 카드·세션을 만들지 않는다")
    void normalizesTaskIdentity() {
        useCase.start(userId, request(task.getId(), AgentType.CLAUDE_CODE, null));
        long beforeItems = workItems.count();
        assertBusinessError(() -> useCase.start(userId, new StartAgentWorkRequest(java.util.List.of(new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "00" + task.getId())), AgentType.CODEX, null)),
                AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS);
        assertThat(workItems.count()).isEqualTo(beforeItems);
    }

    @Test
    @DisplayName("커밋된 시작은 소유 사용자에게 SSE 변경 알림을 한 번 보낸다")
    void publishesAfterCommit() {
        var result = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        verify(sseService).publish(new AgentWorkStatusChangedEvent(
                userId, result.session().id(), AgentWorkStatus.WORKING, result.session().startedAt()));
    }

    @Test
    @DisplayName("STARTED 이벤트 저장 실패 시 최초 카드·세션 등록도 롤백한다")
    void rollsBackNewSessionOnEventFailure() {
        long beforeItems = workItems.count();
        long beforeSessions = sessions.count();
        long beforeEvents = events.count();
        doThrow(new IllegalStateException("이벤트 저장 실패")).when(eventService).recordStarted(any());
        assertThatThrownBy(() -> useCase.start(userId, request(task.getId(), AgentType.CODEX, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(workItems.count()).isEqualTo(beforeItems);
        assertThat(sessions.count()).isEqualTo(beforeSessions);
        assertThat(events.count()).isEqualTo(beforeEvents);
    }

    @Test
    @DisplayName("재시작 이벤트 저장 실패 시 기존 세션의 상태·snapshot·Agent를 복원한다")
    void rollsBackRestartOnEventFailure() {
        var first = useCase.start(userId, request(task.getId(), AgentType.CLAUDE_CODE, "첫 실행"));
        setStatus(first, AgentWorkStatus.FAILED);
        long beforeEvents = events.count();
        doThrow(new IllegalStateException("이벤트 저장 실패")).when(eventService).recordStarted(any());
        assertThatThrownBy(() -> useCase.start(userId, request(task.getId(), AgentType.CODEX, "재실행")))
                .isInstanceOf(IllegalStateException.class);
        var stored = sessions.findById(first.session().id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AgentWorkStatus.FAILED);
        assertThat(stored.getAgentType()).isEqualTo(AgentType.CLAUDE_CODE);
        assertThat(stored.getErrorSnapshot()).containsEntry("summary", "이전 보고");
        assertThat(stored.getCompletedAt()).isNotNull();
        assertThat(events.count()).isEqualTo(beforeEvents);
    }

    @Test
    @DisplayName("여러 Task는 하나의 세션을 공유하며 정규 식별자가 같은 요청은 중복 연결하지 않는다")
    void startsMultipleTasksInOneSession() {
        var second = taskService.create(userId, null, "두 번째 Task");
        var result = useCase.start(userId, new StartAgentWorkRequest(java.util.List.of(
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, task.getId().toString()),
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, second.getId().toString()),
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "00" + task.getId())), AgentType.CODEX, null));
        assertThat(result.session().workItemIds()).hasSize(2);
        for (Long id : result.session().workItemIds()) {
            assertThat(workItems.findById(id).orElseThrow().getSession().getId()).isEqualTo(result.session().id());
        }
        assertThat(events.findAll().stream().filter(event -> event.getSession().getId().equals(result.session().id())))
                .hasSize(1);
    }

    @Test
    @DisplayName("공유 세션을 한 Task로 재시작하면 모든 연결을 유지하고 새 Task도 같은 세션에 연결한다")
    void restartsSharedSessionAndAttachesNewTask() {
        var second = taskService.create(userId, null, "두 번째 Task");
        var third = taskService.create(userId, null, "세 번째 Task");
        var first = useCase.start(userId, groupedRequest(task, second));
        setStatus(first, AgentWorkStatus.COMPLETED);
        var result = useCase.start(userId, groupedRequest(second, third));
        assertThat(result.created()).isFalse();
        assertThat(result.session().id()).isEqualTo(first.session().id());
        assertThat(result.session().workItemIds()).hasSize(3).containsAll(first.session().workItemIds());
        for (Long id : result.session().workItemIds()) {
            assertThat(workItems.findById(id).orElseThrow().getSession().getId()).isEqualTo(first.session().id());
        }
        assertThat(sessions.findById(first.session().id()).orElseThrow().getStatus()).isEqualTo(AgentWorkStatus.WORKING);
    }

    @Test
    @DisplayName("서로 다른 종료 세션에 연결된 Task를 한 번에 시작하면 병합 없이 거부한다")
    void rejectsDifferentSessions() {
        var second = taskService.create(userId, null, "다른 세션 Task");
        var firstSession = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        var secondSession = useCase.start(userId, request(second.getId(), AgentType.CODEX, null));
        setStatus(firstSession, AgentWorkStatus.COMPLETED);
        setStatus(secondSession, AgentWorkStatus.FAILED);
        long beforeEvents = events.count();
        assertBusinessError(() -> useCase.start(userId, groupedRequest(task, second)), AgentWorkErrorCode.AGENT_SESSION_CONFLICT);
        assertThat(sessions.findById(firstSession.session().id()).orElseThrow().getStatus()).isEqualTo(AgentWorkStatus.COMPLETED);
        assertThat(sessions.findById(secondSession.session().id()).orElseThrow().getStatus()).isEqualTo(AgentWorkStatus.FAILED);
        assertThat(events.count()).isEqualTo(beforeEvents);
    }

    @Test
    @DisplayName("묶음 요청에 타인 Task가 포함되면 소유한 Task도 등록하지 않는다")
    void rejectsMixedOwnership() {
        var other = taskService.create(newUser(), null, "다른 사용자 Task");
        long beforeItems = workItems.count();
        long beforeSessions = sessions.count();
        assertBusinessError(() -> useCase.start(userId, groupedRequest(task, other)), ErrorCode.TASK_NOT_FOUND);
        assertThat(workItems.count()).isEqualTo(beforeItems);
        assertThat(sessions.count()).isEqualTo(beforeSessions);
    }

    @Test
    @DisplayName("공유 세션 재시작 이벤트 실패 시 새 Task 연결과 기존 세션 변경을 함께 롤백한다")
    void rollsBackNewAttachmentOnRestartFailure() {
        var second = taskService.create(userId, null, "새 연결 Task");
        var first = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        setStatus(first, AgentWorkStatus.FAILED);
        long beforeItems = workItems.count();
        doThrow(new IllegalStateException("이벤트 실패")).when(eventService).recordStarted(any());
        assertThatThrownBy(() -> useCase.start(userId, groupedRequest(task, second))).isInstanceOf(IllegalStateException.class);
        assertThat(workItems.count()).isEqualTo(beforeItems);
        assertThat(sessions.findById(first.session().id()).orElseThrow().getStatus()).isEqualTo(AgentWorkStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"WORKING", "WAITING", "UNKNOWN"})
    @DisplayName("공유 세션 완료는 결과와 이벤트를 저장하고 연결 Task 자체의 상태를 변경하지 않는다")
    void completesSharedSession(AgentWorkStatus status) {
        var second = taskService.create(userId, null, "공유 Task");
        var first = useCase.start(userId, groupedRequest(task, second));
        setStatus(first, status);
        useCase.complete(userId, first.session().id(), new AgentReportRequest("작업 완료", Map.of("files", 3)));
        var stored = sessions.findById(first.session().id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AgentWorkStatus.COMPLETED);
        assertThat(stored.getStatusSource()).isEqualTo(StatusSource.MCP_REPORT);
        assertThat(stored.getCompletedAt()).isEqualTo(stored.getLastSeenAt());
        assertThat(stored.getStartedAt()).isEqualTo(first.session().startedAt());
        assertThat(stored.getResultSnapshot()).containsEntry("summary", "작업 완료").containsEntry("details", Map.of("files", 3));
        assertThat(workItems.findIdsBySession(userId, first.session().id())).containsExactlyElementsOf(first.session().workItemIds());
        assertThat(taskService.getOne(userId, task.getId()).getStatus()).isEqualTo(task.getStatus());
        assertThat(taskService.getOne(userId, second.getId()).getStatus()).isEqualTo(second.getStatus());
        var history = events.findAll().stream().filter(event -> event.getSession().getId().equals(first.session().id())).toList();
        assertThat(history).hasSize(2);
        var completed = history.stream().filter(event -> event.getEventType().name().equals("COMPLETED")).findFirst().orElseThrow();
        assertThat(completed.getPayload()).isEqualTo(stored.getResultSnapshot());
        assertThat(completed.getAgentType()).isEqualTo(AgentType.CODEX);
        assertThat(completed.getSource()).isEqualTo(StatusSource.MCP_REPORT);
    }

    @ParameterizedTest
    @EnumSource(value = AgentWorkStatus.class, names = {"COMPLETED", "FAILED"})
    @DisplayName("종료 세션에 완료를 보고하면 기존 상태와 이벤트를 변경하지 않는다")
    void rejectsCompleteAfterEnd(AgentWorkStatus status) {
        var first = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        setStatus(first, status);
        long beforeEvents = events.count();
        assertBusinessError(() -> useCase.complete(userId, first.session().id(), new AgentReportRequest("완료", null)),
                AgentWorkErrorCode.AGENT_SESSION_ALREADY_ENDED);
        assertThat(sessions.findById(first.session().id()).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(events.count()).isEqualTo(beforeEvents);
    }

    @Test
    @DisplayName("타인 또는 존재하지 않는 세션에 대한 완료 보고는 404로 거부한다")
    void rejectsUnownedCompletion() {
        var first = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        long beforeEvents = events.count();
        assertBusinessError(() -> useCase.complete(newUser(), first.session().id(), new AgentReportRequest("완료", null)),
                AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND);
        assertBusinessError(() -> useCase.complete(userId, -1L, new AgentReportRequest("완료", null)),
                AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND);
        assertThat(sessions.findById(first.session().id()).orElseThrow().getStatus()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(events.count()).isEqualTo(beforeEvents);
    }

    @Test
    @DisplayName("완료 이벤트 저장에 실패하면 상태·결과·마지막 보고·완료 시각을 함께 롤백한다")
    void rollsBackCompletionOnEventFailure() {
        var first = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        long beforeEvents = events.count();
        doThrow(new IllegalStateException("완료 이벤트 실패")).when(eventService).recordCompleted(any());
        assertThatThrownBy(() -> useCase.complete(userId, first.session().id(), new AgentReportRequest("완료", null)))
                .isInstanceOf(IllegalStateException.class);
        var stored = sessions.findById(first.session().id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(stored.getResultSnapshot()).isNull();
        assertThat(stored.getCompletedAt()).isNull();
        assertThat(stored.getLastSeenAt()).isEqualTo(first.session().lastSeenAt());
        assertThat(events.count()).isEqualTo(beforeEvents);
    }

    @Test
    @DisplayName("벌크 연결의 영향 행 수가 부족하면 허용된 행 변경도 롤백하고 다른 세션을 교체하지 않는다")
    void rollsBackPartialBulkAttachment() {
        var second = taskService.create(userId, null, "다른 세션 Task");
        var first = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        var other = useCase.start(userId, request(second.getId(), AgentType.CODEX, null));
        Long firstItem = first.session().workItemIds().getFirst();
        Instant previous = workItems.findById(firstItem).orElseThrow().getUpdatedAt();
        assertBusinessError(() -> workItemService.attachSession(userId,
                java.util.List.of(firstItem, other.session().workItemIds().getFirst()), first.session().id(), previous.plusSeconds(60)),
                AgentWorkErrorCode.AGENT_SESSION_CONFLICT);
        assertThat(workItems.findById(firstItem).orElseThrow().getUpdatedAt()).isEqualTo(previous);
        assertThat(workItems.findById(other.session().workItemIds().getFirst()).orElseThrow().getSession().getId())
                .isEqualTo(other.session().id());
    }

    @Test
    @DisplayName("벌크 연결은 다른 사용자의 Work Item이나 세션에 연결할 수 없다")
    void rejectsForeignBulkAttachment() {
        var first = useCase.start(userId, request(task.getId(), AgentType.CODEX, null));
        Long otherUser = newUser();
        var otherTask = taskService.create(otherUser, null, "타인 Task");
        var other = useCase.start(otherUser, request(otherTask.getId(), AgentType.CODEX, null));
        assertBusinessError(() -> workItemService.attachSession(userId, other.session().workItemIds(), first.session().id(), Instant.now()),
                AgentWorkErrorCode.AGENT_SESSION_CONFLICT);
        assertBusinessError(() -> workItemService.attachSession(userId, first.session().workItemIds(), other.session().id(), Instant.now()),
                AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND);
        assertThat(workItems.findById(other.session().workItemIds().getFirst()).orElseThrow().getSession().getId()).isEqualTo(other.session().id());
    }

    private StartAgentWorkRequest groupedRequest(Task... tasks) {
        return new StartAgentWorkRequest(java.util.Arrays.stream(tasks)
                .map(value -> new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, value.getId().toString()))
                .toList(), AgentType.CODEX, null);
    }

    private void setStatus(StartAgentWorkResult first, AgentWorkStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var entity = sessions.findById(first.session().id()).orElseThrow();
            Map<String, Object> snapshot = Map.of("summary", "이전 보고");
            entity.apply(AgentSession.restore(entity.getId(), userId,
                    entity.getAgentType(), status, StatusSource.MCP_REPORT, entity.getInstruction(),
                    snapshot, snapshot, snapshot, entity.getStartedAt(), entity.getLastSeenAt(), Instant.now()));
        });
    }

    private Long newUser() {
        String unique = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().email(unique + "@example.com").googleSubject(unique)
                .nickname("Agent 검증 사용자").timezone("Asia/Seoul").build()).getId();
    }

    private StartAgentWorkRequest request(Long taskId, AgentType agentType, String instruction) {
        return new StartAgentWorkRequest(java.util.List.of(new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, taskId.toString())), agentType, instruction);
    }

    private void assertBusinessError(Runnable action, Enum<?> errorCode) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
