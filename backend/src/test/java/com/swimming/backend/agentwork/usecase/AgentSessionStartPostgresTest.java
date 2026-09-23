package com.swimming.backend.agentwork.usecase;
import com.swimming.backend.agentwork.exception.AgentWorkErrorCode;

import com.swimming.backend.agentwork.domain.AgentType;
import com.swimming.backend.agentwork.domain.AgentWorkStatus;
import com.swimming.backend.agentwork.domain.WorkResourceType;
import com.swimming.backend.agentwork.dto.in.StartAgentWorkRequest;
import com.swimming.backend.agentwork.dto.in.AgentReportRequest;
import com.swimming.backend.agentwork.dto.in.AddWorkItemRequest;
import com.swimming.backend.agentwork.dto.out.StartAgentWorkResult;
import com.swimming.backend.agentwork.repository.AgentSessionEventReadRepository;
import com.swimming.backend.agentwork.repository.AgentSessionReadRepository;
import com.swimming.backend.agentwork.repository.AgentWorkItemReadRepository;
import com.swimming.backend.agentwork.repository.AgentWorkItemWriteRepository;
import com.swimming.backend.agentwork.repository.AgentSessionWriteRepository;
import com.swimming.backend.agentwork.repository.AgentSessionEventWriteRepository;
import com.swimming.backend.agentwork.repository.entity.AgentWorkItemEntity;
import com.swimming.backend.agentwork.service.AgentSessionEventWriteService;
import com.swimming.backend.agentwork.service.AgentSessionWriteService;
import com.swimming.backend.agentwork.service.AgentWorkItemWriteService;
import com.swimming.backend.agentwork.service.AgentWorkItemReadService;
import com.swimming.backend.agentwork.workitem.WorkItem;
import com.swimming.backend.agentwork.workitem.WorkItemId;
import com.swimming.backend.agentwork.workitem.WorkItemReader;
import com.swimming.backend.common.config.TimeConfig;
import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 로컬 PostgreSQL의 일회용 스키마에서 V14·N:1 제약·동시 시작을 검증한다. */
@Tag("postgres")
@SpringBootTest(classes = AgentSessionStartPostgresTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.type.json_format_mapper=org.hibernate.type.format.jackson.Jackson3JsonFormatMapper",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.image=none",
        "spring.ai.model.moderation=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none"
})
class AgentSessionStartPostgresTest {
    private static final String SCHEMA = "agent_start_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/swimming";
    private static final String DB_USER = "swimming";
    private static final String DB_PASSWORD = "swimming1234";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = AgentWorkItemEntity.class)
    @EnableJpaRepositories(basePackageClasses = AgentWorkItemReadRepository.class)
    @EnableJpaAuditing
    @Import({AgentSessionReportUseCase.class, AgentWorkItemReadService.class, AgentWorkItemWriteService.class, AgentSessionWriteService.class,
            AgentSessionEventWriteService.class, TimeConfig.class})
    static class TestApplication {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("SET search_path TO " + SCHEMA);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V15__agent_work.sql"));
        }
        registry.add("spring.datasource.url", () -> DB_URL + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
    }

    @AfterAll
    static void cleanSchema() throws SQLException {
        try (var connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Autowired private AgentSessionReportUseCase useCase;
    @Autowired private AgentWorkItemReadRepository workItems;
    @Autowired private AgentWorkItemWriteRepository workItemWrites;
    @Autowired private AgentSessionWriteRepository sessionWrites;
    @Autowired private AgentSessionEventWriteRepository eventWrites;
    @Autowired private AgentSessionReadRepository sessions;
    @Autowired private AgentSessionEventReadRepository events;
    @MockitoBean private WorkItemReader reader;

    @BeforeEach
    void cleanRows() {
        eventWrites.deleteAllInBatch();
        workItemWrites.deleteAllInBatch();
        sessionWrites.deleteAllInBatch();
        when(reader.readAll(eq(1L), anyCollection())).thenReturn(resources("7"));
    }

    @Test
    @DisplayName("V14로 만든 PostgreSQL 테이블에 세션과 null instruction 이벤트를 저장한다")
    void startsOnMigratedSchema() {
        var result = useCase.start(1L, request());
        assertThat(result.created()).isTrue();
        assertThat(result.session().status()).isEqualTo(AgentWorkStatus.WORKING);
        assertThat(workItems.count()).isEqualTo(1);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
        assertThat(events.findAll().getFirst().getPayload()).containsKey("instruction");
    }

    @Test
    @DisplayName("동시 최초 시작은 카드·세션·이벤트를 하나만 만들고 다른 요청은 409 오류를 낸다")
    void serializesConcurrentFirstStarts() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(reader.readAll(eq(1L), anyCollection())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return resources("7");
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> startOrError(request()));
            var second = executor.submit(() -> startOrError(request()));
            var results = java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(StartAgentWorkResult.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(AgentWorkErrorCode.class::isInstance))
                    .containsExactly(AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS);
        }
        assertThat(workItems.count()).isEqualTo(1);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL은 시작 전 NULL 연결과 공유 세션을 허용하고 없는 세션 FK를 거부한다")
    void enforcesManyToOneAndForeignKeys() throws SQLException {
        var result = useCase.start(1L, request());
        try (var connection = DriverManager.getConnection(DB_URL + "?currentSchema=" + SCHEMA, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO agent_work_items (user_id, resource_type, resource_id) VALUES (1, 'SWIMMING_TASK', '8')");
            statement.executeUpdate("INSERT INTO agent_work_items (user_id, resource_type, resource_id, agent_session_id) VALUES (1, 'SWIMMING_TASK', '9', " + result.session().id() + ")");
            assertThat(workItems.count()).isEqualTo(3);
            assertThat(workItems.findIdsBySession(1L, result.session().id())).hasSize(2);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO agent_work_items (user_id, resource_type, resource_id, agent_session_id)
                    VALUES (1, 'SWIMMING_TASK', '10', -1)
                    """))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23503"));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO agent_session_events (agent_session_id, agent_type, event_type, payload, source)
                    VALUES (-1, 'CODEX', 'STARTED', '{}', 'MCP_REPORT')
                    """))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23503"));
        }
    }

    @Test
    @DisplayName("순서가 반대인 겹친 Task 묶음의 동시 시작도 하나의 세션만 생성한다")
    void serializesOverlappingGroups() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(reader.readAll(eq(1L), anyCollection())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            var resourceMap = new java.util.HashMap<>(resources("7"));
            resourceMap.putAll(resources("8"));
            return resourceMap;
        });
        var forward = new StartAgentWorkRequest(java.util.List.of(
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7"),
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "8")), AgentType.CODEX, null);
        var reverse = new StartAgentWorkRequest(forward.workItems().reversed(), AgentType.CODEX, null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> startOrError(forward));
            var second = executor.submit(() -> startOrError(reverse));
            var results = java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(StartAgentWorkResult.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(AgentWorkErrorCode.class::isInstance)).containsExactly(AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS);
        }
        assertThat(workItems.count()).isEqualTo(2);
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 공유 카드의 동시 재시작은 세션 잠금으로 직렬화하여 한 번만 재시작한다")
    void serializesRestartsThroughDifferentCards() throws Exception {
        var map = new java.util.HashMap<>(resources("7"));
        map.putAll(resources("8"));
        when(reader.readAll(eq(1L), anyCollection())).thenReturn(map);
        var group = new StartAgentWorkRequest(java.util.List.of(
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7"),
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "8")), AgentType.CODEX, null);
        var initial = useCase.start(1L, group);
        try (var connection = DriverManager.getConnection(DB_URL + "?currentSchema=" + SCHEMA, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE agent_sessions SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP");
        }
        var barrier = new CyclicBarrier(2);
        when(reader.readAll(eq(1L), anyCollection())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return map;
        });
        var secondCard = new StartAgentWorkRequest(java.util.List.of(
                new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "8")), AgentType.CODEX, null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> startOrError(request()));
            var second = executor.submit(() -> startOrError(secondCard));
            var results = java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(StartAgentWorkResult.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(AgentWorkErrorCode.class::isInstance)).containsExactly(AgentWorkErrorCode.AGENT_WORK_ALREADY_IN_PROGRESS);
        }
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(2);
        assertThat(workItems.findIdsBySession(1L, initial.session().id())).hasSize(2);
    }

    @Test
    @DisplayName("동시 완료 보고는 한 번만 완료·JSON 결과·이벤트를 저장하고 두 번째 보고는 거부한다")
    void serializesConcurrentCompletions() throws Exception {
        var initial = useCase.start(1L, request());
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> complete = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    useCase.complete(1L, initial.session().id(), new AgentReportRequest("완료", Map.of("files", 3)));
                    return "completed";
                } catch (BusinessException error) {
                    return error.getErrorCode();
                }
            };
            var first = executor.submit(complete);
            var second = executor.submit(complete);
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("completed", AgentWorkErrorCode.AGENT_SESSION_ALREADY_ENDED);
        }
        var stored = sessions.findById(initial.session().id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AgentWorkStatus.COMPLETED);
        assertThat(stored.getResultSnapshot()).containsEntry("summary", "완료").containsEntry("details", Map.of("files", 3));
        assertThat(stored.getCompletedAt()).isEqualTo(stored.getLastSeenAt());
        assertThat(events.count()).isEqualTo(2);
        var completed = events.findAll().stream().filter(event -> event.getEventType().name().equals("COMPLETED")).findFirst().orElseThrow();
        assertThat(completed.getPayload()).isEqualTo(stored.getResultSnapshot());
    }

    private Object startOrError(StartAgentWorkRequest request) {
        try {
            return useCase.start(1L, request);
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private Map<WorkItemId, WorkItem> resources(String id) {
        return Map.of(WorkItemId.builder().type(WorkResourceType.SWIMMING_TASK).id(id).build(),
                WorkItem.builder().type(WorkResourceType.SWIMMING_TASK).id(id).title("PostgreSQL 검증")
                        .status(0).important(false).urgent(false)
                        .createdAt(java.time.Instant.parse("2026-09-18T00:00:00Z")).build());
    }

    private StartAgentWorkRequest request() {
        return new StartAgentWorkRequest(java.util.List.of(new AddWorkItemRequest(WorkResourceType.SWIMMING_TASK, "7")), AgentType.CODEX, null);
    }
}
