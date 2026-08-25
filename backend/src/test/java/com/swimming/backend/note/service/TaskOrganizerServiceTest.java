package com.swimming.backend.note.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swimming.backend.note.dto.out.ProjectContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.note.prompt.TaskOrganizerPromptProvider;
import com.swimming.backend.task.domain.TaskStatus;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm-eval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class TaskOrganizerServiceTest {

    private static final List<String> MODELS = List.of(
//            "gpt-4o-mini",
//            "gpt-4.1",
            "gpt-5.4",
            "gpt-5.6-luna"
    );
    private static final Path REPORT_DIRECTORY = Path.of(
            "build/reports/task-organizer-eval"
    );
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());

    private static final ProjectContext SWIMMING = project(
            1L, "Swimming", "Project, Task, 집중 세션을 관리하는 생산성 서비스 개발"
    );
    private static final ProjectContext PORTFOLIO = project(
            2L, "포트폴리오", "취업용 개발 포트폴리오와 AWS 배포 구조 정리"
    );
    private static final ProjectContext MOVING = project(
            3L, "이사 준비", "새집 계약, 행정 처리, 짐 정리와 각종 이전 신청"
    );
    private static final ProjectContext ENGLISH = project(
            4L, "영공", "영어 공부, 단어 복습과 영어 발표 준비"
    );
    private static final ProjectContext HEALTH = project(
            5L, "건강 루틴", "운동 기록, 러닝과 PT 일정 관리"
    );

    private Map<String, TaskOrganizerService> services;

    @BeforeAll
    void setUp() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(loadApiKey())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                .build();
        TaskOrganizerPromptProvider promptProvider = new TaskOrganizerPromptProvider();

        services = MODELS.stream().collect(Collectors.toUnmodifiableMap(
                Function.identity(),
                model -> new TaskOrganizerService(
                        chatClient,
                        promptProvider,
                        model
                )
        ));
    }

    @Test
    @DisplayName("활성 모델의 실제 사용자 시나리오 분류 결과를 JSON 리포트로 생성한다")
    void generatesTaskOrganizerEvaluationReport() throws Exception {
        generateReport(
                "task-organizer-results",
                "복수의 개발·학습·생활 Project를 관리하는 사용자",
                scenarios()
        );
    }

    @Test
    @DisplayName("30대 여성 디지털 마케팅 프리랜서 시나리오 분류 결과를 JSON 리포트로 생성한다")
    void generatesDigitalMarketingFreelancerEvaluationReport() throws Exception {
        generateReport(
                "task-organizer-digital-marketer-results",
                "원격으로 세 클라이언트의 일을 병행하며 집중력은 좋지만 Project 전환 비용 때문에 하루가 파편화되는 30대 여성 디지털 마케팅 프리랜서",
                DigitalMarketerTestData.scenarios()
        );
    }

    @Test
    @DisplayName("29세 재택근무 직장인 박소연 시나리오 분류 결과를 JSON 리포트로 생성한다")
    void generatesRemoteWorkerCertificationEvaluationReport() throws Exception {
        generateReport(
                "task-organizer-remote-worker-results",
                "박소연 · 29세 · 주 4일 재택근무를 하며 저녁에 정보처리기사를 준비하지만 집안일과 알림 때문에 45분 집중 시간 확보가 어려운 직장인",
                RemoteWorkerCertificationTestData.scenarios()
        );
    }

    private void generateReport(
            String filePrefix,
            String persona,
            List<TaskOrganizerTestScenario> scenarios
    ) throws Exception {
        List<ScenarioResult> results = new ArrayList<>();

        for (TaskOrganizerTestScenario scenario : scenarios) {
            for (String model : MODELS) {
                results.add(evaluate(scenario, model));
            }
        }

        Instant generatedAt = Instant.now();
        Path reportPath = reportPath(filePrefix, generatedAt);
        EvaluationReport report = new EvaluationReport(
                generatedAt.toString(),
                persona,
                MODELS,
                results
        );
        Files.createDirectories(REPORT_DIRECTORY);
        new ObjectMapper()
                .findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValue(reportPath.toFile(), report);

        assertThat(reportPath).isRegularFile();
        assertThat(results).allSatisfy(result ->
                assertThat(result.error())
                        .as("scenario=%s, model=%s", result.scenarioId(), result.model())
                        .isNull()
        );
    }

    private Path reportPath(String filePrefix, Instant generatedAt) {
        String timestamp = REPORT_TIME_FORMAT.format(generatedAt);
        return REPORT_DIRECTORY.resolve(
                filePrefix + "-" + timestamp + ".json"
        );
    }

    private ScenarioResult evaluate(TaskOrganizerTestScenario scenario, String model) {
        long startedAt = System.nanoTime();

        try {
            TaskOrganizeResult output = services.get(model).organize(scenario.input());
            assertStructurallyValid(scenario.input(), output);
            return result(scenario, model, startedAt, output, null);
        } catch (Exception exception) {
            String error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return result(scenario, model, startedAt, null, error);
        }
    }

    private ScenarioResult result(
            TaskOrganizerTestScenario scenario,
            String model,
            long startedAt,
            TaskOrganizeResult output,
            String error
    ) {
        return new ScenarioResult(
                scenario.id(),
                scenario.name(),
                scenario.evaluationCriteria(),
                model,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                scenario.input(),
                output,
                error
        );
    }

    private void assertStructurallyValid(TaskOrganizerInput input, TaskOrganizeResult output) {
        Set<Long> projectIds = input.projects().stream()
                .map(ProjectContext::id)
                .collect(Collectors.toSet());

        assertThat(input.projects()).hasSizeLessThanOrEqualTo(5);
        assertThat(output).isNotNull();
        assertThat(output.suggestions()).isNotNull().allSatisfy(suggestion -> {
            assertThat(suggestion.type()).isEqualTo("CREATE_TASK");
            assertThat(suggestion.projectId()).isIn(projectIds);
            assertThat(suggestion.title()).isNotBlank();
            assertThat(suggestion.sourceText()).isNotBlank();
            suggestion.sourceText().lines().forEach(sourcePart ->
                    assertThat(input.memo()).contains(sourcePart)
            );
        });
        assertThat(output.unclassified()).isNotNull().allSatisfy(item -> {
            assertThat(item.sourceText()).isNotBlank();
            assertThat(item.title()).isNotBlank();
            item.sourceText().lines().forEach(sourcePart ->
                        assertThat(input.memo()).contains(sourcePart)
            );
        });
    }

    private List<TaskOrganizerTestScenario> scenarios() {
        ProjectContext milestone = project(
                1L, "M2", "Task Organizer 노트 정리와 Preview 기능 개발 마일스톤"
        );
        ProjectContext operations = project(
                2L, "운영 개선", "배포 서버와 운영 로그 관리"
        );

        return List.of(
                scenario(
                        "mixed-work-personal",
                        "개발·포트폴리오 작업과 개인 구매 메모 혼합",
                        "개발 메모는 해당 Project로, 운동화 구매 메모는 미분류로 남기는지 평가",
                        """
                                아 맞다 로그인 그거 refresh 만료됐을때 다시 발급되는지 봐야함
                                장소 검색 캐시 붙인거 테스트 아직 안했고 redis 껐다 켰을때도 확인?
                                포폴 aws 그림 예전 구조로 되어있음 수정
                                그리고 운동화 주문해야 하는데 사이즈 뭐였지
                                """,
                        List.of(SWIMMING, PORTFOLIO, MOVING, ENGLISH),
                        List.of(
                                task(11L, SWIMMING, "로그인 API 구현"),
                                task(12L, SWIMMING, "장소 검색 기능 구현"),
                                task(21L, PORTFOLIO, "프로젝트 소개 작성")
                        )
                ),
                scenario(
                        "split-one-sentence",
                        "한 문장에 섞인 세 Project 작업 분리",
                        "서로 다른 실행 결과 세 개를 각각 올바른 Project Task로 나누는지 평가",
                        "수영앱 상세 api 응답에 task도 넣고 포폴 README엔 배포주소랑 화면 캡처 추가, 아 맞다 이사 전입신고 서류도 찾아놔야됨",
                        List.of(SWIMMING, PORTFOLIO, MOVING),
                        List.of(
                                task(11L, SWIMMING, "프로젝트 상세 API 구현"),
                                task(21L, PORTFOLIO, "README 초안 작성")
                        )
                ),
                scenario(
                        "merge-scattered-notes",
                        "흩어진 같은 캐시 작업 병합",
                        "캐시 적용·TTL·재시작 테스트를 한 Task로 병합하고 구매 메모는 미분류하는지 평가",
                        """
                                장소 조회 캐시 붙이기
                                redis로 할거고 TTL은 10분 제대로 먹는지도 보기
                                아 그리고 고양이 모래 주문
                                아까 그 장소조회 캐시 재시작 뒤에도 테스트
                                """,
                        List.of(SWIMMING, PORTFOLIO),
                        List.of(task(12L, SWIMMING, "장소 검색 기능 구현"))
                ),
                scenario(
                        "ambiguous-project",
                        "두 Project 사이에서 모호한 로그인 UI 작업",
                        "구별 근거가 없는 작업을 목록 첫 Project에 억지 배정하지 않고 미분류하는지 평가",
                        "둘 다 로그인 있어서 어느 프로젝트인지 모르겠는데 모바일에서 페이지가 자꾸 옆으로 튀어나옴 그거 고쳐야댐",
                        List.of(
                                project(1L, "업무 서비스", "로그인 페이지가 있는 웹 서비스"),
                                project(2L, "개인 서비스", "로그인 페이지가 있는 웹 서비스")
                        ),
                        List.of()
                ),
                scenario(
                        "abbreviated-project-names",
                        "축약 Project 이름으로 분류",
                        "스위밍과 영공이라는 러프한 명칭을 각각 올바른 Project로 연결하는지 평가",
                        "스위밍 태스크 순서바꾸기 api 이상한거 다시 봐야함 / 영공은 오늘 외운 단어 복습 알림 붙이기",
                        List.of(SWIMMING, ENGLISH, PORTFOLIO),
                        List.of(
                                task(13L, SWIMMING, "Task 순서 변경"),
                                task(41L, ENGLISH, "단어장 만들기")
                        )
                ),
                scenario(
                        "unrelated-only-project",
                        "유일한 Project와 무관한 개인 용무",
                        "Project가 하나뿐이어도 생신 케이크 용무를 포트폴리오에 배정하지 않는지 평가",
                        "엄마 생신 케이크 예약해야 하는데 날짜 카톡에서 먼저 찾아봐야겠다",
                        List.of(PORTFOLIO),
                        List.of(task(21L, PORTFOLIO, "프로젝트 소개 작성"))
                ),
                scenario(
                        "no-projects",
                        "Project가 없는 러프 메모",
                        "Project가 없을 때 모든 의미 있는 원문을 미분류로 보존하는지 평가",
                        "로그인 refresh 쪽 다시 확인\n이사 인터넷 이전 신청 전화",
                        List.of(),
                        List.of()
                ),
                scenario(
                        "rough-typo-source",
                        "오타가 있는 러프한 이사 메모",
                        "제목의 표현은 정리하되 sourceText에는 오타를 포함한 원문을 그대로 보존하는지 평가",
                        "이사 전입신고 서류 첵크해야댐 뭐뭐 필요한지도 같이 보기",
                        List.of(MOVING, HEALTH, SWIMMING),
                        List.of()
                ),
                new TaskOrganizerTestScenario(
                        "existing-task-context",
                        "기존 Task의 고유 용어를 분류 맥락으로 사용",
                        "Project 이름을 직접 말하지 않아도 Organizer Preview 맥락을 M2로 연결하고 운영 개선에는 배정하지 않는지 평가",
                        input(
                                "Task Organizer Preview API 결과에서 미분류 체크 풀면 노트에 그대로 남는지도 확인해야함",
                                List.of(milestone, operations),
                                List.of(
                                        task(11L, milestone, "노트 작성 화면 구현"),
                                        task(12L, milestone, "Task Organizer Preview API 구현"),
                                        task(21L, operations, "서버 로그 보관 기간 설정")
                                )
                        )
                ),
                scenario(
                        "actions-vs-thoughts",
                        "행동과 감상·막연한 생각 혼합",
                        "명시된 행동만 Task로 만들고 신체 상태와 막연한 생각은 미분류하며 일정·우선순위를 만들지 않는지 평가",
                        """
                                요즘 뛰고 나면 무릎이 좀 뻐근한 느낌
                                이번주 운동 기록 밀린거 정리
                                영어 발표 주제 아직 고민중 그냥 여행 얘기?
                                아 수영앱 에러 응답 문서 업데이트해야지
                                다음주쯤 뭔가 하나 해야될듯 기억이 안남
                                """,
                        List.of(HEALTH, ENGLISH, SWIMMING, PORTFOLIO),
                        List.of(
                                task(51L, HEALTH, "주간 운동 기록"),
                                task(41L, ENGLISH, "영어 발표 준비"),
                                task(14L, SWIMMING, "ProblemDetail 에러 응답 적용")
                        )
                )
        );
    }

    private String loadApiKey() {
        String processApiKey = System.getenv("OPENAI_API_KEY");
        if (processApiKey != null && !processApiKey.isBlank()) {
            return processApiKey;
        }

        Path envPath = Path.of("src/test/.env").toAbsolutePath().normalize();
        if (!Files.isRegularFile(envPath)) {
            envPath = Path.of("backend/src/test/.env").toAbsolutePath().normalize();
        }

        String testApiKey = Dotenv.configure()
                .directory(envPath.getParent().toString())
                .filename(envPath.getFileName().toString())
                .load()
                .get("OPENAI_API_KEY");

        if (testApiKey == null || testApiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is required in the process environment or src/test/.env."
            );
        }
        return testApiKey;
    }

    private static TaskOrganizerTestScenario scenario(
            String id,
            String name,
            String evaluationCriteria,
            String memo,
            List<ProjectContext> projects,
            List<TaskContext> tasks
    ) {
        return new TaskOrganizerTestScenario(id, name, evaluationCriteria, input(memo, projects, tasks));
    }

    private static TaskOrganizerInput input(
            String memo,
            List<ProjectContext> projects,
            List<TaskContext> tasks
    ) {
        return new TaskOrganizerInput(memo.strip(), projects, tasks);
    }

    private static ProjectContext project(Long id, String name, String description) {
        return new ProjectContext(id, name, description);
    }

    private static TaskContext task(Long id, ProjectContext project, String title) {
        return new TaskContext(id, project.id(), title, TaskStatus.TODO);
    }

    private record ScenarioResult(
            String scenarioId,
            String scenarioName,
            String evaluationCriteria,
            String model,
            long latencyMillis,
            TaskOrganizerInput input,
            TaskOrganizeResult output,
            String error
    ) {
    }

    private record EvaluationReport(
            String generatedAt,
            String persona,
            List<String> models,
            List<ScenarioResult> results
    ) {
    }
}
