package com.swimming.backend.agentwork.application.usecase;

import com.swimming.backend.agentwork.application.dto.StartAgentWorkResult;
import com.swimming.backend.agentwork.domain.AgentSession;
import com.swimming.backend.agentwork.domain.AgentSessionEventType;
import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.AgentWorkErrorCode;
import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.domain.StatusSource;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.infra.persistence.AgentSessionEventReadRepository;
import com.swimming.backend.agentwork.infra.persistence.AgentSessionReadRepository;
import com.swimming.backend.agentwork.infra.persistence.AgentWorkItemReadRepository;
import com.swimming.backend.agentwork.interfaces.router.dto.AddWorkItemRequest;
import com.swimming.backend.agentwork.interfaces.router.dto.LinkWorkItemsRequest;
import com.swimming.backend.agentwork.interfaces.router.dto.StartAgentWorkRequest;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.task.domain.Task;
import com.swimming.backend.task.service.TaskService;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 진행 중인 세션에 할 일을 붙이고 떼는 경로. 보드의 사용자와 에이전트가 같은 유스케이스를 쓴다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-session-link;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
class AgentSessionLinkIntegrationTest {
    @Autowired private AgentSessionReportUseCase useCase;
    @Autowired private AgentWorkItemReadRepository workItems;
    @Autowired private AgentSessionReadRepository sessions;
    @Autowired private AgentSessionEventReadRepository events;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long userId;
    private Task started;
    private Task waiting;

    @BeforeEach
    void setUp() {
        userId = newUser();
        started = taskService.create(userId, null, "세션을 가진 할 일");
        waiting = taskService.create(userId, null, "붙일 할 일");
    }

    @Test
    @DisplayName("진행 중인 세션에 할 일을 붙이면 연결 목록과 LINKED 이벤트가 늘어난다")
    void linksWorkItemToRunningSession() {
        StartAgentWorkResult session = startWith(started);

        var response = useCase.link(userId, session.session().id(), linkRequest(waiting));

        assertThat(response.id()).isEqualTo(session.session().id());
        assertThat(response.status()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(response.workItemIds()).hasSize(2);
        assertThat(workItems.findIdsBySession(userId, session.session().id())).hasSize(2);
        assertThat(linkedResourceIds(session.session().id()))
                .containsExactly(List.of(waiting.getId().toString()));
    }

    @Test
    @DisplayName("이미 그 세션에 붙어 있는 할 일을 다시 붙여도 연결은 하나로 유지된다")
    void keepsSingleLinkWhenAlreadyLinked() {
        StartAgentWorkResult session = startWith(started);

        var response = useCase.link(userId, session.session().id(), linkRequest(started));

        assertThat(response.workItemIds()).hasSize(1);
    }

    @Test
    @DisplayName("다른 세션에 붙어 있던 할 일은 새 세션으로 옮기고 이전 세션에는 UNLINKED를 남긴다")
    void movesWorkItemBetweenSessions() {
        StartAgentWorkResult from = startWith(started);
        StartAgentWorkResult to = startWith(waiting);

        var response = useCase.link(userId, to.session().id(), linkRequest(started));

        assertThat(response.workItemIds()).hasSize(2);
        assertThat(workItems.findIdsBySession(userId, from.session().id())).isEmpty();
        assertThat(unlinkedResourceIds(from.session().id()))
                .containsExactly(List.of(started.getId().toString()));
        assertThat(linkedResourceIds(to.session().id()))
                .containsExactly(List.of(started.getId().toString()));
    }

    @Test
    @DisplayName("끝난 세션에는 할 일을 붙이지 않는다")
    void rejectsLinkOnEndedSession() {
        StartAgentWorkResult session = startWith(started);
        endSession(session.session().id());

        assertBusinessError(() -> useCase.link(userId, session.session().id(), linkRequest(waiting)),
                AgentWorkErrorCode.AGENT_SESSION_ALREADY_ENDED);
        assertThat(workItems.findIdsBySession(userId, session.session().id())).hasSize(1);
    }

    @Test
    @DisplayName("남의 세션이나 없는 세션에는 붙이지 않는다")
    void rejectsLinkOnOtherUsersSession() {
        Long otherUserId = newUser();
        Task otherTask = taskService.create(otherUserId, null, "남의 할 일");
        StartAgentWorkResult otherSession = useCase.start(otherUserId, startRequest(otherTask));

        assertBusinessError(() -> useCase.link(userId, otherSession.session().id(), linkRequest(waiting)),
                AgentWorkErrorCode.AGENT_SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 할 일은 붙이지 않는다")
    void rejectsLinkOfOtherUsersTask() {
        StartAgentWorkResult session = startWith(started);
        Task otherTask = taskService.create(newUser(), null, "남의 할 일");

        assertBusinessError(() -> useCase.link(userId, session.session().id(), linkRequest(otherTask)),
                ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    @DisplayName("할 일을 떼면 시작 전으로 돌아가고 세션의 다른 할 일은 그대로 남는다")
    void unlinksWorkItem() {
        StartAgentWorkResult session = startWith(started);
        useCase.link(userId, session.session().id(), linkRequest(waiting));
        Long unlinkTargetId = workItems.findIdsBySession(userId, session.session().id()).getLast();

        useCase.unlink(userId, session.session().id(), unlinkTargetId);

        assertThat(workItems.findIdsBySession(userId, session.session().id())).hasSize(1);
        assertThat(workItems.findSessionId(userId, unlinkTargetId)).isEmpty();
        assertThat(sessions.findById(session.session().id())).isPresent();
        assertThat(events.findAllOwnedBySession(userId, session.session().id()))
                .extracting(event -> event.getEventType())
                .contains(AgentSessionEventType.WORK_ITEM_UNLINKED);
    }

    @Test
    @DisplayName("그 세션에 붙어 있지 않은 할 일은 떼지 않는다")
    void rejectsUnlinkOfUnrelatedWorkItem() {
        StartAgentWorkResult session = startWith(started);
        StartAgentWorkResult other = startWith(waiting);

        assertBusinessError(() -> useCase.unlink(userId, session.session().id(), other.session().workItemIds().getFirst()),
                AgentWorkErrorCode.AGENT_WORK_ITEM_NOT_FOUND);
        assertThat(workItems.findIdsBySession(userId, other.session().id())).hasSize(1);
    }

    private StartAgentWorkResult startWith(Task task) {
        return useCase.start(userId, startRequest(task));
    }

    private StartAgentWorkRequest startRequest(Task task) {
        return new StartAgentWorkRequest(
                List.of(new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, task.getId().toString())),
                AgentType.CLAUDE_CODE, null);
    }

    private LinkWorkItemsRequest linkRequest(Task task) {
        return new LinkWorkItemsRequest(
                List.of(new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, task.getId().toString())));
    }

    /** 끝난 세션을 만든다. complete 보고와 같은 상태를 직접 심는다. */
    private void endSession(Long sessionId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var entity = sessions.findById(sessionId).orElseThrow();
            Map<String, Object> snapshot = Map.of("summary", "완료 보고");
            entity.apply(AgentSession.restore(entity.getId(), userId, entity.getAgentType(),
                    AgentWorkStatus.COMPLETED, StatusSource.MCP_REPORT, entity.getInstruction(),
                    null, snapshot, null, entity.getStartedAt(), entity.getLastSeenAt(), Instant.now()));
        });
    }

    private List<Object> linkedResourceIds(Long sessionId) {
        return resourceIds(sessionId, AgentSessionEventType.WORK_ITEM_LINKED);
    }

    private List<Object> unlinkedResourceIds(Long sessionId) {
        return resourceIds(sessionId, AgentSessionEventType.WORK_ITEM_UNLINKED);
    }

    private List<Object> resourceIds(Long sessionId, AgentSessionEventType eventType) {
        return events.findAllOwnedBySession(userId, sessionId).stream()
                .filter(event -> event.getEventType() == eventType)
                .map(event -> event.getPayload().get("resourceIds"))
                .toList();
    }

    private Long newUser() {
        String unique = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().email(unique + "@example.com").googleSubject(unique)
                .nickname("Agent 연결 검증 사용자").timezone("Asia/Seoul").build()).getId();
    }

    private void assertBusinessError(Runnable action, Enum<?> errorCode) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
