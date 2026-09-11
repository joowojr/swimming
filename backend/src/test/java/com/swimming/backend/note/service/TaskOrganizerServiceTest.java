package com.swimming.backend.note.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swimming.backend.common.config.llm.LlmProperties;
import com.swimming.backend.common.config.llm.LlmProvider;
import com.swimming.backend.common.logging.LlmUsageLogger;
import com.swimming.backend.common.config.llm.OllamaChatOptionsFactory;
import com.swimming.backend.common.config.llm.OpenAiChatOptionsFactory;
import com.swimming.backend.note.dto.out.FolderContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.dto.out.TaskExtractResult;
import com.swimming.backend.note.dto.out.TaskOrganizeResult;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.common.prompt.PromptKey;
import com.swimming.backend.common.prompt.PromptProperties;
import com.swimming.backend.common.prompt.PromptRepository;
import com.swimming.backend.common.prompt.ResourcePromptRepository;
import org.springframework.core.io.DefaultResourceLoader;
import com.swimming.backend.task.domain.TaskStatus;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm-eval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 45, unit = TimeUnit.MINUTES)
class TaskOrganizerServiceTest {

    private record EvalModel(String id, LlmProvider provider) {
    }

    private static final List<EvalModel> EVAL_MODELS = List.of(
//            new EvalModel("gpt-5.4", LlmProvider.OPENAI),
            new EvalModel("gpt-5.6-luna", LlmProvider.OPENAI)
//            new EvalModel("qwen3:8b", LlmProvider.OLLAMA)
    );
    private static final List<String> MODELS = EVAL_MODELS.stream()
            .map(EvalModel::id)
            .toList();
    private static final String OLLAMA_BASE_URL = System.getenv()
            .getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private static final Path REPORT_DIRECTORY = Path.of(
            "build/reports/task-organizer-eval"
    );
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault());

    private static final FolderContext SWIMMING = folder(
            1L, "Swimming", "Folder, Task, 집중 세션을 관리하는 생산성 서비스 개발"
    );
    private static final FolderContext PORTFOLIO = folder(
            2L, "포트폴리오", "취업용 개발 포트폴리오와 AWS 배포 구조 정리"
    );
    private static final FolderContext MOVING = folder(
            3L, "이사 준비", "새집 계약, 행정 처리, 짐 정리와 각종 이전 신청"
    );
    private static final FolderContext ENGLISH = folder(
            4L, "영공", "영어 공부, 단어 복습과 영어 발표 준비"
    );
    private static final FolderContext HEALTH = folder(
            5L, "건강 루틴", "운동 기록, 러닝과 PT 일정 관리"
    );

    /** 비교할 프롬프트 버전. key 는 리포트에 남는 라벨이다. */
    /** 프롬프트 버전 비교의 반복 횟수. reasoning 모델은 temperature 0 에서도 결정적이지 않다. */
    private static final int PROMPT_VERSION_RUNS = 3;
    /**
     * 추출 평가 반복 횟수와 통과 기준.
     *
     * <p>reasoning 모델은 temperature 를 지원하지 않아 {@code temperature: 0.0} 이 전달되지
     * 않는다. 같은 프롬프트로도 실행마다 결과가 달라지므로 1 회 실행은 게이트가 될 수 없다.
     * 반복해서 통과율로 판정한다.
     */
    private static final int EXTRACT_RUNS = 5;
    private static final double EXTRACT_MIN_PASS_RATE = 0.8;

    private static final Map<String, String> PROMPT_VERSIONS = Map.of(
            "v2", "classpath:prompts/task-organizer-v2.md",
            "v3", "classpath:prompts/task-organizer-v3.md",
            "v4", "classpath:prompts/task-organizer-v4.md"
    );

    private Map<String, TaskOrganizerService> services;
    /** 프롬프트 버전별 서비스. 모델은 EVAL_MODELS 의 첫 번째 하나만 쓴다. */
    private Map<String, TaskOrganizerService> promptVariants;
    private final UsageRecorder usageRecorder = new UsageRecorder();

    /** 한 프롬프트만 담은 저장소. 버전별로 나란히 비교할 때 쓴다. */
    private static PromptRepository promptRepository(String location) {
        return new ResourcePromptRepository(
                new PromptProperties(
                        Map.of(
                                PromptKey.TASK_ORGANIZER.configName(), location,
                                PromptKey.TASK_EXTRACTOR.configName(), location
                        ),
                        Map.of(
                                "splitting", "classpath:prompts/task-organizer/_splitting.md",
                                "titles", "classpath:prompts/task-organizer/_titles.md"
                        )
                ),
                new DefaultResourceLoader()
        );
    }

    @BeforeAll
    void setUp() {
        PromptRepository promptRepository = promptRepository(PROMPT_VERSIONS.get("v4"));
        LlmUsageLogger usageLogger = new LlmUsageLogger();
        Map<String, TaskOrganizerService> built = new LinkedHashMap<>();

        // provider 별 ChatClient 는 필요할 때 한 번만 만든다.
        ChatClient openAiClient = null;
        ChatClient ollamaClient = null;

        for (EvalModel evalModel : EVAL_MODELS) {
            TaskOrganizerService service;

            switch (evalModel.provider()) {
                case OPENAI -> {
                    if (openAiClient == null) {
                        openAiClient = chatClient(OpenAiChatModel.builder()
                                .options(OpenAiChatOptions.builder()
                                        .apiKey(loadApiKey())
                                        .build())
                                .build());
                    }
                    service = new TaskOrganizerService(
                            openAiClient,
                            promptRepository,
                            new OpenAiChatOptionsFactory(openAiProperties(evalModel.id())),
                            usageLogger,
                            openAiProperties(evalModel.id())
                    );
                }
                case OLLAMA -> {
                    if (ollamaClient == null) {
                        ollamaClient = chatClient(OllamaChatModel.builder()
                                .ollamaApi(OllamaApi.builder()
                                        .baseUrl(OLLAMA_BASE_URL)
                                        .build())
                                .build());
                    }
                    service = new TaskOrganizerService(
                            ollamaClient,
                            promptRepository,
                            new OllamaChatOptionsFactory(ollamaProperties(evalModel.id())),
                            usageLogger,
                            ollamaProperties(evalModel.id())
                    );
                }
                default -> throw new IllegalStateException(
                        "Unsupported eval provider: " + evalModel.provider()
                );
            }

            built.put(evalModel.id(), service);
        }

        services = Collections.unmodifiableMap(built);

        EvalModel primary = EVAL_MODELS.get(0);
        ChatClient primaryClient = primary.provider() == LlmProvider.OPENAI
                ? openAiClient
                : ollamaClient;
        var optionsFactory = primary.provider() == LlmProvider.OPENAI
                ? new OpenAiChatOptionsFactory(openAiProperties(primary.id()))
                : new OllamaChatOptionsFactory(ollamaProperties(primary.id()));

        Map<String, TaskOrganizerService> variants = new LinkedHashMap<>();
        PROMPT_VERSIONS.keySet().stream().sorted().forEach(version -> variants.put(
                version,
                new TaskOrganizerService(
                        primaryClient,
                        promptRepository(PROMPT_VERSIONS.get(version)),
                        optionsFactory,
                        usageLogger,
                        primary.provider() == LlmProvider.OPENAI
                                ? openAiProperties(primary.id())
                                : ollamaProperties(primary.id())
                )
        ));
        promptVariants = Collections.unmodifiableMap(variants);
    }

    /** 호출 한 번의 토큰 사용량을 리포트에 담으려고 응답 메타데이터를 가로챈다. */
    private static final class UsageRecorder implements CallAdvisor {

        private Usage last;

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            ChatClientResponse response = chain.nextCall(request);
            var chatResponse = response.chatResponse();
            last = chatResponse == null || chatResponse.getMetadata() == null
                    ? null
                    : chatResponse.getMetadata().getUsage();
            return response;
        }

        @Override
        public String getName() {
            return "usage-recorder";
        }

        @Override
        public int getOrder() {
            return 0;
        }

        private TokenUsage drain() {
            Usage usage = last;
            last = null;
            if (usage == null) {
                return new TokenUsage(-1, -1, -1, -1);
            }
            return new TokenUsage(
                    usage.getPromptTokens() == null ? -1 : usage.getPromptTokens(),
                    usage.getCompletionTokens() == null ? -1 : usage.getCompletionTokens(),
                    usage.getTotalTokens() == null ? -1 : usage.getTotalTokens(),
                    usage.getCacheReadInputTokens() == null ? -1 : usage.getCacheReadInputTokens()
            );
        }
    }

    private record TokenUsage(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long cachedPromptTokens
    ) {
    }

    private ChatClient chatClient(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build(), usageRecorder)
                .build();
    }

    /**
     * reasoning 모델 계열에만 reasoning effort를 준다.
     * reasoning effort를 주면 maxCompletionTokens를, 주지 않으면 maxTokens를 쓰는데 둘은 상호 배타적이다.
     */
    private static LlmProperties openAiProperties(String model) {
        String reasoningEffort = model.startsWith("gpt-5") ? "low" : null;

        return new LlmProperties(
                LlmProvider.OPENAI,
                model,
                2_000,
                0.0,
                new LlmProperties.OpenAi(reasoningEffort),
                null
        );
    }

    /** 운영 llm/ollama.yml 과 같은 값을 쓴다. thinking 은 끈다. */
    private static LlmProperties ollamaProperties(String model) {
        return new LlmProperties(
                LlmProvider.OLLAMA,
                model,
                2_000,
                0.0,
                null,
                new LlmProperties.Ollama(4_096, "10m", false)
        );
    }

    @Test
    @DisplayName("활성 모델의 실제 사용자 시나리오 분류 결과를 JSON 리포트로 생성한다")
    void generatesTaskOrganizerEvaluationReport() throws Exception {
        generateReport(
                "task-organizer-results",
                "복수의 개발·학습·생활 Folder를 관리하는 사용자",
                scenarios()
        );
    }

    @Test
    @DisplayName("30대 여성 디지털 마케팅 프리랜서 시나리오 분류 결과를 JSON 리포트로 생성한다")
    void generatesDigitalMarketingFreelancerEvaluationReport() throws Exception {
        generateReport(
                "task-organizer-digital-marketer-results",
                "원격으로 세 클라이언트의 일을 병행하며 집중력은 좋지만 Folder 전환 비용 때문에 하루가 파편화되는 30대 여성 디지털 마케팅 프리랜서",
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

    @Test
    @DisplayName("폴더가 정해진 추출 경로가 비행동을 Task 로 만들지 않는지 검증한다")
    void extractKeepsNonActionableOutOfTasks() throws Exception {
        List<ExtractScenarioResult> results = new ArrayList<>();

        Map<String, Integer> passCount = new LinkedHashMap<>();

        for (TaskExtractTestScenario scenario : extractScenarios()) {
            for (String model : MODELS) {
                TaskOrganizerService service = services.get(model);
                String key = scenario.id() + " [" + model + "]";
                passCount.putIfAbsent(key, 0);

                for (int run = 1; run <= EXTRACT_RUNS; run++) {
                    long startedAt = System.nanoTime();
                    TaskExtractResult output = null;
                    String error = null;

                    try {
                        output = service.extract(scenario.input()).output();
                        assertStructurallyValid(scenario.input(), output);
                        assertBucketLabels(scenario, output);
                        passCount.merge(key, 1, Integer::sum);
                    } catch (Exception | AssertionError failure) {
                        error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                    }

                    results.add(new ExtractScenarioResult(
                            scenario.id(),
                            scenario.name(),
                            scenario.evaluationCriteria(),
                            model,
                            run,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                            usageRecorder.drain(),
                            scenario.input(),
                            output,
                            error
                    ));
                }
            }
        }

        // 리포트는 실패 실행까지 남긴다. 게이트는 통과율로 판정한다.
        writeExtractReport(results);

        assertThat(passCount).allSatisfy((key, passes) ->
                assertThat(passes / (double) EXTRACT_RUNS)
                        .as("%s — %d회 중 %d회 통과", key, EXTRACT_RUNS, passes)
                        .isGreaterThanOrEqualTo(EXTRACT_MIN_PASS_RATE));
    }

    @Test
    @DisplayName("프롬프트 버전별 분류 결과와 토큰 사용량을 나란히 리포트로 생성한다")
    void generatesPromptVersionComparisonReport() throws Exception {
        List<ScenarioResult> results = new ArrayList<>();

        for (TaskOrganizerTestScenario scenario : DigitalMarketerTestData.scenarios()) {
            for (var variant : promptVariants.entrySet()) {
                for (int run = 1; run <= PROMPT_VERSION_RUNS; run++) {
                    results.add(evaluate(scenario, variant.getKey(), variant.getValue(), run));
                }
            }
        }

        writeReport(
                "task-organizer-prompt-version-results",
                "프리랜서 마케터 시나리오에 프롬프트 버전만 바꿔 %d회씩 반복 측정한다".formatted(PROMPT_VERSION_RUNS),
                List.copyOf(promptVariants.keySet()),
                results
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

        writeReport(filePrefix, persona, MODELS, results);
    }

    private void writeReport(
            String filePrefix,
            String persona,
            List<String> models,
            List<? extends EvalResult> results
    ) throws Exception {
        Instant generatedAt = Instant.now();
        Path reportPath = reportPath(filePrefix, generatedAt);
        EvaluationReport report = new EvaluationReport(
                generatedAt.toString(),
                persona,
                models,
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

    /** 실행별 실패를 리포트에 남기고 통과율로 판정하므로, error 가 있어도 여기서 막지 않는다. */
    private void writeExtractReport(List<ExtractScenarioResult> results) throws Exception {
        Instant generatedAt = Instant.now();
        Path reportPath = reportPath("task-extractor-results", generatedAt);
        Files.createDirectories(REPORT_DIRECTORY);
        new ObjectMapper()
                .findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValue(reportPath.toFile(), new EvaluationReport(
                        generatedAt.toString(),
                        "폴더를 직접 고른 사용자",
                        MODELS,
                        results
                ));

        assertThat(reportPath).isRegularFile();
    }

    private Path reportPath(String filePrefix, Instant generatedAt) {
        String timestamp = REPORT_TIME_FORMAT.format(generatedAt);
        return REPORT_DIRECTORY.resolve(
                filePrefix + "-" + timestamp + ".json"
        );
    }

    private ScenarioResult evaluate(TaskOrganizerTestScenario scenario, String model) {
        return evaluate(scenario, model, services.get(model), 1);
    }

    private ScenarioResult evaluate(
            TaskOrganizerTestScenario scenario,
            String label,
            TaskOrganizerService service,
            int run
    ) {
        long startedAt = System.nanoTime();

        try {
            TaskOrganizeResult output = service.organize(scenario.input()).output();
            assertStructurallyValid(scenario.input(), output);
            return result(scenario, label, run, startedAt, output, null);
        } catch (Exception | AssertionError failure) {
            String error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            return result(scenario, label, run, startedAt, null, error);
        }
    }

    private ScenarioResult result(
            TaskOrganizerTestScenario scenario,
            String model,
            int run,
            long startedAt,
            TaskOrganizeResult output,
            String error
    ) {
        return new ScenarioResult(
                scenario.id(),
                scenario.name(),
                scenario.evaluationCriteria(),
                model,
                run,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                usageRecorder.drain(),
                scenario.input(),
                output,
                error
        );
    }

    /**
     * 추출 결과의 구조 검증. 분류가 없으므로 folderId 검사가 빠지고, 대신 한 원문이 두
     * 바구니에 동시에 들어가지 않는지를 본다. 프롬프트의 "exactly one of" 규칙이다.
     */
    private void assertStructurallyValid(TaskOrganizerInput input, TaskExtractResult output) {
        assertThat(output).isNotNull();
        assertThat(output.tasks()).isNotNull().allSatisfy(task -> {
            assertThat(task.title()).isNotBlank();
            assertThat(task.sourceText()).isNotBlank();
            task.sourceText().lines().forEach(sourcePart ->
                    assertThat(input.memo()).contains(sourcePart)
            );
        });
        assertThat(output.unclassified()).isNotNull().allSatisfy(item -> {
            assertThat(item.title()).isNotBlank();
            assertThat(item.sourceText()).isNotBlank();
            item.sourceText().lines().forEach(sourcePart ->
                    assertThat(input.memo()).contains(sourcePart)
            );
        });

        List<String> taskSources = output.tasks().stream()
                .map(TaskExtractResult.ExtractedTask::sourceText)
                .toList();
        List<String> unclassifiedSources = output.unclassified().stream()
                .map(TaskExtractResult.UnclassifiedItem::sourceText)
                .toList();

        assertThat(taskSources)
                .as("같은 원문이 tasks 와 unclassified 에 동시에 들어가면 안 된다")
                .noneMatch(unclassifiedSources::contains);
    }

    private void assertStructurallyValid(TaskOrganizerInput input, TaskOrganizeResult output) {
        Set<Long> folderIds = input.folders().stream()
                .map(FolderContext::id)
                .collect(Collectors.toSet());

        assertThat(input.folders()).hasSizeLessThanOrEqualTo(5);
        assertThat(output).isNotNull();
        assertThat(output.suggestions()).isNotNull().allSatisfy(suggestion -> {
            assertThat(suggestion.type()).isEqualTo("CREATE_TASK");
            assertThat(suggestion.folderId()).isIn(folderIds);
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

    /**
     * 폴더가 하나로 정해진 추출 시나리오.
     *
     * <p>분류 시나리오에서 폴더 하나만 남긴 변형이다. 같은 메모로 돌리면 분류 프롬프트가
     * 미분류로 밀어냈던 항목이 추출에서는 Task 가 되는지를 직접 대조할 수 있다.
     */
    private List<TaskExtractTestScenario> extractScenarios() {
        return List.of(
                extractScenario(
                        "extract-unrelated-item",
                        "지정된 Folder 주제와 무관한 개인 용무",
                        "폴더 밖의 행동은 unclassified 로 가는지 평가. 사용자가 직접 옮길 수 있도록"
                                + " 버리지 않고 보존하는 것이 핵심이다",
                        "엄마 생신 케이크 예약해야 하는데 날짜 카톡에서 먼저 찾아봐야겠다",
                        List.of(PORTFOLIO),
                        List.of(task(21L, PORTFOLIO, "프로젝트 소개 작성")),
                        List.of(),
                        List.of("엄마 생신 케이크 예약해야 하는데 날짜 카톡에서 먼저 찾아봐야겠다")
                ),
                extractScenario(
                        "extract-on-off-topic-mix",
                        "주제 유관·무관 행동만 섞인 메모",
                        "전부 명확한 행동이라 '행동인가' 축이 제거된다. 폴더 안의 행동만 Task 가 되고"
                                + " 폴더 밖의 행동은 unclassified 로 가는지 본다",
                        """
                                이번 달 러닝 거리 기록 정리
                                내일 PT 예약 시간 옮기기
                                전세 계약서 특약 조항 다시 확인
                                수영앱 배포 롤백 절차 문서화
                                """,
                        List.of(HEALTH),
                        List.of(task(51L, HEALTH, "주간 운동 기록")),
                        List.of("이번 달 러닝 거리 기록 정리", "내일 PT 예약 시간 옮기기"),
                        List.of("전세 계약서 특약 조항 다시 확인", "수영앱 배포 롤백 절차 문서화")
                ),
                extractScenario(
                        "extract-actions-vs-thoughts",
                        "행동과 감상·막연한 생각 혼합",
                        "감상과 막연한 생각을 Task 로 만들지 않으면서, 폴더 안의 행동은 놓치지 않는지 평가",
                        """
                                요즘 뛰고 나면 무릎이 좀 뻐근한 느낌
                                이번주 운동 기록 밀린거 정리
                                영어 발표 주제 아직 고민중 그냥 여행 얘기?
                                아 수영앱 에러 응답 문서 업데이트해야지
                                다음주쯤 뭔가 하나 해야될듯 기억이 안남
                                """,
                        List.of(HEALTH),
                        List.of(task(51L, HEALTH, "주간 운동 기록")),
                        List.of("이번주 운동 기록 밀린거 정리"),
                        List.of(
                                "요즘 뛰고 나면 무릎이 좀 뻐근한 느낌",
                                "다음주쯤 뭔가 하나 해야될듯 기억이 안남",
                                "아 수영앱 에러 응답 문서 업데이트해야지"
                        )
                )
        );
    }

    private static TaskExtractTestScenario extractScenario(
            String id,
            String name,
            String evaluationCriteria,
            String memo,
            List<FolderContext> folders,
            List<TaskContext> tasks,
            List<String> neverActionable,
            List<String> mustBeTask
    ) {
        return new TaskExtractTestScenario(
                id, name, evaluationCriteria, input(memo, folders, tasks), neverActionable, mustBeTask
        );
    }

    /**
     * 두 바구니 배정을 양방향으로 검증한다.
     *
     * <p>{@code mustBeTask} 만 보면 "전부 tasks 로 보내기"로 통과하고,
     * {@code mustBeUnclassified} 만 보면 그 반대가 통과한다. 둘이 서로를 견제한다.
     */
    private void assertBucketLabels(TaskExtractTestScenario scenario, TaskExtractResult output) {
        assertBucket(scenario, "tasks", output.tasks().stream()
                .map(TaskExtractResult.ExtractedTask::sourceText).toList(), scenario.mustBeTask());
        assertBucket(scenario, "unclassified", output.unclassified().stream()
                .map(TaskExtractResult.UnclassifiedItem::sourceText).toList(), scenario.mustBeUnclassified());
    }

    private void assertBucket(
            TaskExtractTestScenario scenario,
            String bucket,
            List<String> actualSources,
            List<String> expected
    ) {
        expected.forEach(source ->
                assertThat(actualSources)
                        .as("scenario=%s — '%s' 가 %s 에 없다", scenario.id(), source, bucket)
                        .anySatisfy(actual -> assertThat(actual).contains(source))
        );
    }

    private List<TaskOrganizerTestScenario> scenarios() {
        FolderContext milestone = folder(
                1L, "M2", "Task Organizer 노트 정리와 Preview 기능 개발 마일스톤"
        );
        FolderContext operations = folder(
                2L, "운영 개선", "배포 서버와 운영 로그 관리"
        );

        return List.of(
                scenario(
                        "mixed-work-personal",
                        "개발·포트폴리오 작업과 개인 구매 메모 혼합",
                        "개발 메모는 해당 Folder로, 운동화 구매 메모는 미분류로 남기는지 평가",
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
                        "한 문장에 섞인 세 Folder 작업 분리",
                        "서로 다른 실행 결과 세 개를 각각 올바른 Folder Task로 나누는지 평가",
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
                        "ambiguous-folder",
                        "두 Folder 사이에서 모호한 로그인 UI 작업",
                        "구별 근거가 없는 작업을 목록 첫 Folder에 억지 배정하지 않고 미분류하는지 평가",
                        "둘 다 로그인 있어서 어느 프로젝트인지 모르겠는데 모바일에서 페이지가 자꾸 옆으로 튀어나옴 그거 고쳐야댐",
                        List.of(
                                folder(1L, "업무 서비스", "로그인 페이지가 있는 웹 서비스"),
                                folder(2L, "개인 서비스", "로그인 페이지가 있는 웹 서비스")
                        ),
                        List.of()
                ),
                scenario(
                        "abbreviated-folder-names",
                        "축약 Folder 이름으로 분류",
                        "스위밍과 영공이라는 러프한 명칭을 각각 올바른 Folder로 연결하는지 평가",
                        "스위밍 태스크 순서바꾸기 api 이상한거 다시 봐야함 / 영공은 오늘 외운 단어 복습 알림 붙이기",
                        List.of(SWIMMING, ENGLISH, PORTFOLIO),
                        List.of(
                                task(13L, SWIMMING, "Task 순서 변경"),
                                task(41L, ENGLISH, "단어장 만들기")
                        )
                ),
                scenario(
                        "unrelated-only-folder",
                        "유일한 Folder와 무관한 개인 용무",
                        "Folder가 하나뿐이어도 생신 케이크 용무를 포트폴리오에 배정하지 않는지 평가",
                        "엄마 생신 케이크 예약해야 하는데 날짜 카톡에서 먼저 찾아봐야겠다",
                        List.of(PORTFOLIO),
                        List.of(task(21L, PORTFOLIO, "프로젝트 소개 작성"))
                ),
                scenario(
                        "no-folders",
                        "Folder가 없는 러프 메모",
                        "Folder가 없을 때 모든 의미 있는 원문을 미분류로 보존하는지 평가",
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
                        "Folder 이름을 직접 말하지 않아도 Organizer Preview 맥락을 M2로 연결하고 운영 개선에는 배정하지 않는지 평가",
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

        Path envPath = Path.of("src/test/.env.test").toAbsolutePath().normalize();
        if (!Files.isRegularFile(envPath)) {
            envPath = Path.of("backend/src/test/.env.test").toAbsolutePath().normalize();
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
            List<FolderContext> folders,
            List<TaskContext> tasks
    ) {
        return new TaskOrganizerTestScenario(id, name, evaluationCriteria, input(memo, folders, tasks));
    }

    private static TaskOrganizerInput input(
            String memo,
            List<FolderContext> folders,
            List<TaskContext> tasks
    ) {
        return new TaskOrganizerInput(memo.strip(), folders, tasks);
    }

    private static FolderContext folder(Long id, String name, String description) {
        return new FolderContext(id, name, description);
    }

    private static TaskContext task(Long id, FolderContext folder, String title) {
        return new TaskContext(id, folder.id(), title, TaskStatus.TODO);
    }

    /** 리포트가 분류·추출 결과를 함께 담기 위한 최소 공통면. */
    private interface EvalResult {
        String scenarioId();

        String model();

        String error();
    }

    private record ScenarioResult(
            String scenarioId,
            String scenarioName,
            String evaluationCriteria,
            String model,
            int run,
            long latencyMillis,
            TokenUsage tokens,
            TaskOrganizerInput input,
            TaskOrganizeResult output,
            String error
    ) implements EvalResult {
    }

    private record ExtractScenarioResult(
            String scenarioId,
            String scenarioName,
            String evaluationCriteria,
            String model,
            int run,
            long latencyMillis,
            TokenUsage tokens,
            TaskOrganizerInput input,
            TaskExtractResult output,
            String error
    ) implements EvalResult {
    }

    private record EvaluationReport(
            String generatedAt,
            String persona,
            List<String> models,
            List<? extends EvalResult> results
    ) {
    }
}
