package com.swimming.backend.knowledge.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swimming.backend.common.config.llm.ChatOptionsFactory;
import com.swimming.backend.common.config.llm.LlmProperties;
import com.swimming.backend.common.config.llm.LlmProvider;
import com.swimming.backend.common.config.llm.OpenAiChatOptionsFactory;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import com.swimming.backend.knowledge.dto.out.NodeResolutionInput;
import com.swimming.backend.knowledge.dto.out.NodeResolutionResult;
import com.swimming.backend.knowledge.prompt.NodeResolutionInputSerializer;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm-eval")
@Tag("node-resolution-comparison")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 60, unit = TimeUnit.MINUTES)
class NodeResolutionPipelineComparisonTest {

    private static final String DEFAULT_MODEL = "gpt-5.6-luna";
    private static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int EMBEDDING_DIMENSIONS = 768;
    private static final int SOURCE_LIMIT = 5;
    private static final Path REPORT_DIRECTORY = Path.of("build/reports/node-resolution-eval");

    private final String model = System.getProperty("eval.model", DEFAULT_MODEL);
    private final int runs = Integer.getInteger("eval.runs", 3);
    private final int topK = Integer.getInteger("eval.topK", 3);
    private final Set<String> variants = parseVariants();
    private final String previousReport = System.getProperty("eval.previousReport");
    private final CostRates costRates = CostRates.fromSystemProperties();

    private ChatClient chatClient;
    private ChatOptionsFactory chatOptionsFactory;
    private OpenAiEmbeddingModel embeddingModel;
    private String prompt;

    @BeforeAll
    void setUp() throws Exception {
        String apiKey = loadApiKey();
        chatClient = ChatClient.builder(OpenAiChatModel.builder()
                        .options(OpenAiChatOptions.builder().apiKey(apiKey).build())
                        .build())
                .build();
        chatOptionsFactory = new OpenAiChatOptionsFactory(openAiProperties());

        embeddingModel = OpenAiEmbeddingModel.builder()
                .options(OpenAiEmbeddingOptions.builder()
                        .apiKey(apiKey)
                        .model(EMBEDDING_MODEL)
                        .dimensions(EMBEDDING_DIMENSIONS)
                        .build())
                .build();
        prompt = new ClassPathResource("prompts/knowledge/node-resolution.md")
                .getContentAsString(StandardCharsets.UTF_8).strip();
    }

    @Test
    @DisplayName("Baseline과 Subject-only 및 Hybrid embedding 파이프라인을 Golden dataset으로 비교한다")
    void comparesBaselineSubjectOnlyAndHybridEmbeddingPipelines() throws Exception {
        List<NodeResolutionEvalScenario> scenarios = NodeResolutionEvalTestData.scenarios();
        ComparisonReport previous = loadPreviousReport();
        int runOffset = previous == null ? 0 : previous.runs();
        List<RetrievalRun> retrievalRuns = previous == null
                ? new ArrayList<>() : new ArrayList<>(previous.retrievalRuns());
        List<SubjectEmbeddingRun> subjectEmbeddingRuns = previous == null
                ? new ArrayList<>() : new ArrayList<>(previous.subjectEmbeddingRuns());
        List<HybridRetrievalRun> hybridRetrievalRuns = previous == null
                ? new ArrayList<>() : new ArrayList<>(previous.hybridRetrievalRuns());
        List<VariantRun> variantRuns = previous == null
                ? new ArrayList<>() : new ArrayList<>(previous.variantRuns());
        List<String> errors = previous == null
                ? new ArrayList<>() : new ArrayList<>(previous.errors());

        for (NodeResolutionEvalScenario scenario : scenarios) {
            RetrievalRun retrieval = findSourceRetrieval(retrievalRuns, scenario.id());
            SubjectEmbeddingRun subjectEmbedding = findSubjectEmbedding(
                    subjectEmbeddingRuns, scenario.id());
            try {
                if (retrieval == null || subjectEmbedding == null) {
                    CompletableFuture<RetrievalRun> sourcePath = CompletableFuture.supplyAsync(
                            () -> retrieve(scenario));
                    CompletableFuture<SubjectEmbeddingRun> subjectPath = CompletableFuture.supplyAsync(
                            () -> retrieveBySubjectEmbedding(scenario));
                    CompletableFuture.allOf(sourcePath, subjectPath).join();
                    retrieval = sourcePath.join();
                    subjectEmbedding = subjectPath.join();
                    retrievalRuns.add(retrieval);
                    subjectEmbeddingRuns.add(subjectEmbedding);
                }
            } catch (CompletionException failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                errors.add("stage=retrieval scenario=%s %s: %s".formatted(
                        scenario.id(), cause.getClass().getSimpleName(), cause.getMessage()));
                continue;
            }

            List<String> baseline = retrieval.subjects().stream().map(SubjectEvidence::title).toList();
            HybridRetrievalRun hybrid = hybridRetrievalRuns.stream()
                    .filter(run -> run.scenarioId().equals(scenario.id()))
                    .findFirst().orElse(null);
            if (hybrid == null) {
                hybrid = combine(retrieval, subjectEmbedding);
                hybridRetrievalRuns.add(hybrid);
            }

            for (int run = runOffset + 1; run <= runOffset + runs; run++) {
                if (variants.contains("source-baseline")) {
                    evaluate("source-baseline", scenario, run, baseline, variantRuns, errors);
                }
                if (variants.contains("subject-embedding-only")) {
                    evaluate(
                            "subject-embedding-only", scenario, run,
                            subjectEmbedding.subjects(), variantRuns, errors);
                }
                if (variants.contains("hybrid-embedding")) {
                    evaluate("hybrid-embedding", scenario, run, hybrid.subjects(), variantRuns, errors);
                }
            }
        }

        RetrievalAggregate retrieval = aggregateRetrieval(retrievalRuns);
        HybridRetrievalAggregate hybridRetrieval = aggregateHybridRetrieval(
                subjectEmbeddingRuns, hybridRetrievalRuns);
        Map<String, Aggregate> aggregates = aggregate(variantRuns);
        Map<String, Map<NodeResolutionEvalScenario.Domain, Aggregate>> domainAggregates =
                aggregateByDomain(variantRuns);
        List<FailureSummary> baselineFailures = failureSummaries(
                "source-baseline", scenarios, retrievalRuns, variantRuns);
        List<FailureSummary> hybridFailures = failureSummaries(
                "hybrid-embedding", scenarios, retrievalRuns, variantRuns);
        List<FailureSummary> subjectEmbeddingOnlyFailures = failureSummaries(
                "subject-embedding-only", scenarios, retrievalRuns, variantRuns);
        GateResult gates = gates(
                retrieval, hybridRetrieval, aggregates, domainAggregates, hybridFailures, errors);
        ComparisonReport report = new ComparisonReport(
                Instant.now().toString(), NodeResolutionEvalTestData.VERSION, model,
                EMBEDDING_MODEL, runOffset + runs, SOURCE_LIMIT, topK, variants, costRates,
                retrieval, hybridRetrieval,
                aggregates, domainAggregates, hybridFailures, subjectEmbeddingOnlyFailures,
                baselineFailures,
                retrievalRuns, subjectEmbeddingRuns, hybridRetrievalRuns, variantRuns, errors, gates
        );
        Path reportPath = writeReport(report);
        writeMarkdown(reportPath, report);
        printSummary(report, reportPath);

        assertThat(errors).as("평가 실행 오류가 있습니다. 리포트: %s", reportPath).isEmpty();
        assertThat(gates.failures())
                .as("Node resolution 회귀 게이트 실패. 리포트: %s", reportPath)
                .isEmpty();
    }

    private RetrievalRun retrieve(NodeResolutionEvalScenario scenario) {
        List<String> documents = new ArrayList<>();
        documents.add(scenario.currentSourceSummary());
        documents.addAll(scenario.sourceCorpus().stream()
                .map(NodeResolutionEvalScenario.SimilarSourceFixture::summary).toList());
        EmbeddingCall embedding = embed(documents);
        float[] query = embedding.vectors().getFirst();

        List<RankedSource> ranked = IntStream.range(0, scenario.sourceCorpus().size())
                .mapToObj(index -> new RankedSource(
                        scenario.sourceCorpus().get(index),
                        cosine(query, embedding.vectors().get(index + 1))))
                .sorted(Comparator.comparingDouble(RankedSource::similarity).reversed())
                .limit(SOURCE_LIMIT)
                .toList();

        List<SubjectEvidence> subjects = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int rank = 0; rank < ranked.size(); rank++) {
            RankedSource source = ranked.get(rank);
            for (String title : source.source().subjects()) {
                if (seen.add(NodeTitleNormalizer.normalize(title))) {
                    subjects.add(new SubjectEvidence(title, rank + 1, source.similarity()));
                }
            }
        }

        List<String> expectedReuse = scenario.expected().stream()
                .filter(item -> item.action() == NodeResolutionResult.Action.REUSE)
                .map(NodeResolutionEvalScenario.ExpectedResolution::canonicalSubject)
                .toList();
        int sourceHits = (int) expectedReuse.stream().filter(expected -> ranked.stream()
                .anyMatch(source -> source.source().subjects().contains(expected))).count();
        return new RetrievalRun(
                scenario.id(), scenario.domain(), ranked, subjects, expectedReuse.size(), sourceHits,
                embedding.usage(), embedding.latencyMillis()
        );
    }

    private SubjectEmbeddingRun retrieveBySubjectEmbedding(NodeResolutionEvalScenario scenario) {
        List<String> existingSubjects = scenario.sourceCorpus().stream()
                .flatMap(source -> source.subjects().stream())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                NodeTitleNormalizer::normalize,
                                Function.identity(),
                                (first, duplicate) -> first,
                                LinkedHashMap::new
                        ),
                        values -> List.copyOf(values.values())
                ));
        List<String> inputs = new ArrayList<>();
        inputs.addAll(scenario.extractedSubjects().stream().map(this::embeddingText).toList());
        inputs.addAll(existingSubjects.stream().map(this::embeddingText).toList());

        EmbeddingCall embedding = embed(inputs);
        int candidateCount = scenario.extractedSubjects().size();
        List<float[]> candidateVectors = embedding.vectors().subList(0, candidateCount);
        List<float[]> subjectVectors = embedding.vectors().subList(
                candidateCount, embedding.vectors().size());
        Map<String, List<RankedSubject>> rankedByCandidate = new LinkedHashMap<>();
        LinkedHashSet<String> selected = new LinkedHashSet<>();

        for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
            float[] candidateVector = candidateVectors.get(candidateIndex);
            List<RankedSubject> ranked = IntStream.range(0, existingSubjects.size())
                    .mapToObj(subjectIndex -> new RankedSubject(
                            existingSubjects.get(subjectIndex),
                            cosine(candidateVector, subjectVectors.get(subjectIndex))))
                    .sorted(Comparator.comparingDouble(RankedSubject::similarity).reversed())
                    .limit(topK)
                    .toList();
            rankedByCandidate.put(scenario.extractedSubjects().get(candidateIndex), ranked);
            ranked.stream().map(RankedSubject::title).forEach(selected::add);
        }

        int expectedReuse = 0;
        int hits = 0;
        double reciprocalRank = 0;
        for (NodeResolutionEvalScenario.ExpectedResolution expected : scenario.expected()) {
            if (expected.action() != NodeResolutionResult.Action.REUSE) {
                continue;
            }
            expectedReuse++;
            List<RankedSubject> ranked = rankedByCandidate.getOrDefault(
                    expected.candidate(), List.of());
            int rank = IntStream.range(0, ranked.size())
                    .filter(index -> ranked.get(index).title().equals(expected.canonicalSubject()))
                    .findFirst().orElse(-1);
            if (rank >= 0) {
                hits++;
                reciprocalRank += 1.0 / (rank + 1);
            }
        }
        return new SubjectEmbeddingRun(
                scenario.id(), scenario.domain(), rankedByCandidate, List.copyOf(selected),
                expectedReuse, hits, reciprocalRank, embedding.usage(), embedding.latencyMillis());
    }

    private HybridRetrievalRun combine(
            RetrievalRun sourceRetrieval,
            SubjectEmbeddingRun subjectRetrieval
    ) {
        List<String> sourceSubjects = sourceRetrieval.subjects().stream()
                .map(SubjectEvidence::title)
                .toList();
        List<String> candidates = unionCandidates(sourceSubjects, subjectRetrieval.subjects());
        return new HybridRetrievalRun(
                sourceRetrieval.scenarioId(), sourceRetrieval.domain(),
                candidates,
                Math.max(sourceRetrieval.latencyMillis(), subjectRetrieval.latencyMillis())
        );
    }

    static List<String> unionCandidates(
            List<String> sourceSubjects,
            List<String> directSubjectCandidates
    ) {
        LinkedHashMap<String, String> candidates = new LinkedHashMap<>();
        directSubjectCandidates.forEach(title -> candidates.put(
                NodeTitleNormalizer.normalize(title), title));
        sourceSubjects.forEach(title -> candidates.putIfAbsent(
                NodeTitleNormalizer.normalize(title), title));
        return List.copyOf(candidates.values());
    }

    private String embeddingText(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private List<String> rerank(
            NodeResolutionEvalScenario scenario,
            List<SubjectEvidence> subjects
    ) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String candidate : scenario.extractedSubjects()) {
            subjects.stream()
                    .sorted(Comparator.comparingDouble(
                            (SubjectEvidence subject) -> lexicalScore(candidate, scenario.currentSourceSummary(), subject)
                    ).reversed().thenComparingInt(SubjectEvidence::sourceRank))
                    .limit(topK)
                    .map(SubjectEvidence::title)
                    .forEach(selected::add);
        }
        return List.copyOf(selected);
    }

    private double lexicalScore(String candidate, String summary, SubjectEvidence subject) {
        String left = searchable(candidate);
        String right = searchable(subject.title());
        Set<String> leftTokens = tokens(candidate);
        Set<String> rightTokens = tokens(subject.title());

        // 정규화 후 완전히 같은 표기는 가장 강한 동일 개념 신호로 취급한다.
        double exact = left.equals(right) ? 10.0 : 0.0;
        // 한쪽 이름이 다른 쪽을 포함하면 수식어가 붙은 표기 차이일 가능성을 반영한다.
        double containment = left.contains(right) || right.contains(left) ? 3.0 : 0.0;
        // 공백과 구두점으로 분리한 단어 집합의 중첩으로 키워드 유사성을 반영한다.
        double tokenOverlap = jaccard(leftTokens, rightTokens) * 4.0;
        // 띄어쓰기·활용·음역 차이에도 일부 점수를 주도록 문자 bigram 중첩을 반영한다.
        double ngramOverlap = jaccard(ngrams(left, 2), ngrams(right, 2)) * 2.0;
        // OIDC처럼 후보가 기존 Subject 이름의 두문자어인 경우를 강하게 보상한다.
        double acronym = acronym(subject.title()).equals(left) || acronym(candidate).equals(right)
                ? 4.0 : 0.0;
        // 현재 Source 요약에도 기존 Subject의 단어가 등장하면 문맥상 근거로 약하게 보상한다.
        double summaryMatch = tokens(summary).stream().filter(rightTokens::contains).count() * 0.15;
        // Subject가 연결된 Source의 임베딩 유사도와 검색 순위를 보조 신호로 더한다.
        double sourceEvidence = subject.sourceSimilarity() + 1.0 / subject.sourceRank();
        return exact + containment + tokenOverlap + ngramOverlap + acronym
                + summaryMatch + sourceEvidence;
    }

    private void evaluate(
            String variant,
            NodeResolutionEvalScenario scenario,
            int run,
            List<String> candidateSubjects,
            List<VariantRun> results,
            List<String> errors
    ) {
        try {
            NodeResolutionInput input = new NodeResolutionInput(
                    scenario.currentSourceSummary(),
                    indexedCandidates(scenario.extractedSubjects()),
                    indexed(candidateSubjects));
            CallResult call = call(input);
            results.add(new VariantRun(
                    scenario.id(), scenario.domain(), variant, run, candidateSubjects,
                    call.latencyMillis(), call.usage(), costRates.chatCost(call.usage()),
                    score(scenario, candidateSubjects, call.output()), call.output()
            ));
        } catch (RuntimeException failure) {
            errors.add("stage=resolution variant=%s run=%d scenario=%s %s: %s".formatted(
                    variant, run, scenario.id(), failure.getClass().getSimpleName(), failure.getMessage()));
        }
    }

    private CallResult call(NodeResolutionInput input) {
        long startedAt = System.nanoTime();
        var response = chatClient.prompt()
                .system(prompt)
                .user(NodeResolutionInputSerializer.serialize(input))
                .options(chatOptionsFactory.create())
                .call()
                .responseEntity(
                        NodeResolutionResult.class,
                        spec -> spec.useProviderStructuredOutput().validateSchema()
                );
        long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return new CallResult(
                response.getEntity(),
                TokenUsage.from(response.getResponse() == null
                        ? null : response.getResponse().getMetadata().getUsage()),
                latency
        );
    }

    private Score score(
            NodeResolutionEvalScenario scenario,
            List<String> candidateSubjects,
            NodeResolutionResult actual
    ) {
        Map<String, NodeResolutionEvalScenario.ExpectedResolution> expected = scenario.expected().stream()
                .collect(Collectors.toMap(
                        NodeResolutionEvalScenario.ExpectedResolution::candidate,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        // 결정은 후보 index로 돌아오므로 채점 전에 후보 값으로 되돌린다.
        Map<String, NodeResolutionResult.Decision> predicted = new LinkedHashMap<>();
        if (actual != null && actual.decisions() != null) {
            for (NodeResolutionResult.Decision decision : actual.decisions()) {
                String candidate = candidateOf(decision, scenario.extractedSubjects());
                if (candidate != null) {
                    predicted.putIfAbsent(candidate, decision);
                }
            }
        }

        int correct = 0;
        int trueReuse = 0;
        int predictedReuse = 0;
        int expectedReuse = 0;
        int falseMerge = 0;
        int missedReuse = 0;
        int wrongReuse = 0;
        List<DecisionScore> decisions = new ArrayList<>();
        for (NodeResolutionEvalScenario.ExpectedResolution golden : scenario.expected()) {
            NodeResolutionResult.Decision decision = predicted.get(golden.candidate());
            String selected = selectedTitle(decision, candidateSubjects);
            boolean actionMatches = decision != null && decision.action() == golden.action();
            boolean titleMatches = golden.action() == NodeResolutionResult.Action.CREATE
                    ? actionMatches && NodeTitleNormalizer.normalize(decision.value())
                    .equals(NodeTitleNormalizer.normalize(golden.canonicalSubject()))
                    : actionMatches && golden.canonicalSubject().equals(selected);
            boolean passed = actionMatches && titleMatches;
            if (passed) {
                correct++;
            }
            if (golden.action() == NodeResolutionResult.Action.REUSE) {
                expectedReuse++;
                if (decision != null && decision.action() == NodeResolutionResult.Action.REUSE) {
                    predictedReuse++;
                    if (golden.canonicalSubject().equals(selected)) {
                        trueReuse++;
                    } else {
                        wrongReuse++;
                    }
                } else {
                    missedReuse++;
                }
            } else if (decision != null && decision.action() == NodeResolutionResult.Action.REUSE) {
                predictedReuse++;
                falseMerge++;
            }
            decisions.add(new DecisionScore(golden, decision, selected, passed));
        }
        double precision = predictedReuse == 0 ? (expectedReuse == 0 ? 1.0 : 0.0)
                : trueReuse / (double) predictedReuse;
        double recall = expectedReuse == 0 ? 1.0 : trueReuse / (double) expectedReuse;
        double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        return new Score(correct, expected.size(), trueReuse, predictedReuse, expectedReuse,
                falseMerge, missedReuse, wrongReuse, precision, recall, f1,
                correct == expected.size() && predicted.size() == expected.size(), decisions);
    }

    private RetrievalAggregate aggregateRetrieval(List<RetrievalRun> results) {
        int expected = results.stream().mapToInt(RetrievalRun::expectedReuse).sum();
        int sourceHits = results.stream().mapToInt(RetrievalRun::sourceHits).sum();
        int rerankHits = 0;
        int retrievedExpected = 0;
        double reciprocalRank = 0.0;
        int rankedExpected = 0;
        long tokens = 0;
        long latency = 0;
        for (RetrievalRun result : results) {
            NodeResolutionEvalScenario scenario = NodeResolutionEvalTestData.scenarios().stream()
                    .filter(item -> item.id().equals(result.scenarioId())).findFirst().orElseThrow();
            List<String> selected = rerank(scenario, result.subjects());
            for (NodeResolutionEvalScenario.ExpectedResolution golden : scenario.expected()) {
                if (golden.action() != NodeResolutionResult.Action.REUSE) {
                    continue;
                }
                rankedExpected++;
                boolean retrieved = result.subjects().stream()
                        .anyMatch(subject -> subject.title().equals(golden.canonicalSubject()));
                if (!retrieved) {
                    continue;
                }
                retrievedExpected++;
                int rank = selected.indexOf(golden.canonicalSubject());
                if (rank >= 0) {
                    rerankHits++;
                    reciprocalRank += 1.0 / (rank + 1);
                }
            }
            tokens += result.embeddingUsage().totalTokens();
            latency += result.latencyMillis();
        }
        return new RetrievalAggregate(
                expected == 0 ? 1.0 : sourceHits / (double) expected,
                retrievedExpected == 0 ? 1.0 : rerankHits / (double) retrievedExpected,
                rankedExpected == 0 ? 1.0 : rerankHits / (double) rankedExpected,
                retrievedExpected == 0 ? 1.0 : reciprocalRank / retrievedExpected,
                results.isEmpty() ? 0 : latency / results.size(),
                results.isEmpty() ? 0 : tokens / (double) results.size(),
                costRates.embeddingCost(new TokenUsage((int) tokens, 0, (int) tokens, 0))
        );
    }

    private HybridRetrievalAggregate aggregateHybridRetrieval(
            List<SubjectEmbeddingRun> subjectRuns,
            List<HybridRetrievalRun> hybridRuns
    ) {
        int expected = subjectRuns.stream().mapToInt(SubjectEmbeddingRun::expectedReuse).sum();
        int directHits = subjectRuns.stream().mapToInt(SubjectEmbeddingRun::hits).sum();
        double reciprocalRank = subjectRuns.stream()
                .mapToDouble(SubjectEmbeddingRun::reciprocalRankSum).sum();
        Map<String, HybridRetrievalRun> hybridByScenario = hybridRuns.stream()
                .collect(Collectors.toMap(HybridRetrievalRun::scenarioId, Function.identity()));
        int unionHits = 0;
        for (NodeResolutionEvalScenario scenario : NodeResolutionEvalTestData.scenarios()) {
            HybridRetrievalRun hybrid = hybridByScenario.get(scenario.id());
            if (hybrid == null) {
                continue;
            }
            for (NodeResolutionEvalScenario.ExpectedResolution golden : scenario.expected()) {
                if (golden.action() == NodeResolutionResult.Action.REUSE
                    && hybrid.subjects().contains(golden.canonicalSubject())) {
                    unionHits++;
                }
            }
        }
        long totalTokens = subjectRuns.stream()
                .mapToLong(run -> run.embeddingUsage().totalTokens()).sum();
        List<Long> latencies = hybridRuns.stream()
                .map(HybridRetrievalRun::parallelLatencyMillis).sorted().toList();
        return new HybridRetrievalAggregate(
                expected == 0 ? 1.0 : directHits / (double) expected,
                expected == 0 ? 1.0 : unionHits / (double) expected,
                expected == 0 ? 1.0 : reciprocalRank / expected,
                hybridRuns.stream().mapToInt(run -> run.subjects().size()).average().orElse(0),
                latencies.isEmpty() ? 0 : latencies.get(latencies.size() / 2),
                subjectRuns.isEmpty() ? 0 : totalTokens / (double) subjectRuns.size(),
                costRates.embeddingCost(new TokenUsage(
                        (int) totalTokens, 0, (int) totalTokens, 0))
        );
    }

    private Map<String, Aggregate> aggregate(List<VariantRun> runs) {
        return runs.stream().collect(Collectors.groupingBy(
                VariantRun::variant, LinkedHashMap::new, Collectors.collectingAndThen(
                        Collectors.toList(), this::aggregateValues)));
    }

    private Map<String, Map<NodeResolutionEvalScenario.Domain, Aggregate>> aggregateByDomain(
            List<VariantRun> runs
    ) {
        return runs.stream().collect(Collectors.groupingBy(
                VariantRun::variant, LinkedHashMap::new,
                Collectors.groupingBy(
                        VariantRun::domain, LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::aggregateValues)
                )));
    }

    private Aggregate aggregateValues(List<VariantRun> values) {
        int trueReuse = values.stream().mapToInt(value -> value.score().trueReuse()).sum();
        int predicted = values.stream().mapToInt(value -> value.score().predictedReuse()).sum();
        int expected = values.stream().mapToInt(value -> value.score().expectedReuse()).sum();
        double precision = predicted == 0 ? 0.0 : trueReuse / (double) predicted;
        double recall = expected == 0 ? 1.0 : trueReuse / (double) expected;
        double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        List<Long> latencies = values.stream().map(VariantRun::latencyMillis).sorted().toList();
        return new Aggregate(
                precision, recall, f1,
                values.stream().filter(value -> value.score().exact()).count() / (double) values.size(),
                values.stream().mapToInt(value -> value.score().falseMerge()).sum(),
                values.stream().mapToInt(value -> value.score().missedReuse()).sum(),
                values.stream().mapToInt(value -> value.score().wrongReuse()).sum(),
                latencies.get(latencies.size() / 2),
                values.stream().mapToInt(value -> value.usage().promptTokens()).average().orElse(0),
                values.stream().mapToDouble(VariantRun::costIndex).average().orElse(0),
                values.stream().mapToInt(value -> value.candidateSubjects().size()).average().orElse(0)
        );
    }

    private GateResult gates(
            RetrievalAggregate retrieval,
            HybridRetrievalAggregate hybridRetrieval,
            Map<String, Aggregate> aggregates,
            Map<String, Map<NodeResolutionEvalScenario.Domain, Aggregate>> domainAggregates,
            List<FailureSummary> hybridFailures,
            List<String> errors
    ) {
        List<String> failures = new ArrayList<>();
        if (!errors.isEmpty()) {
            failures.add("evaluation errors=" + errors.size());
        }
        if (retrieval.sourceRecallAtK() < 0.95) {
            failures.add("source Recall@%d %.3f < 0.950".formatted(
                    SOURCE_LIMIT, retrieval.sourceRecallAtK()));
        }
        if (hybridRetrieval.unionRecall() < 0.99) {
            failures.add("hybrid union Recall %.3f < 0.990".formatted(
                    hybridRetrieval.unionRecall()));
        }
        Aggregate baseline = aggregates.get("source-baseline");
        Aggregate hybrid = aggregates.get("hybrid-embedding");
        if (hybrid == null) {
            failures.add("hybrid-embedding 결과가 없습니다");
            return new GateResult(false, failures);
        }
        if (baseline != null && hybrid.falseMerge() > baseline.falseMerge()) {
            failures.add("hybrid false merge %d > baseline %d".formatted(
                    hybrid.falseMerge(), baseline.falseMerge()));
        }
        if (baseline != null && hybrid.f1() + 0.000_001 < baseline.f1()) {
            failures.add("hybrid F1 %.3f < baseline %.3f".formatted(hybrid.f1(), baseline.f1()));
        }
        if (baseline != null && hybrid.exactRate() + 0.000_001 < baseline.exactRate()) {
            failures.add("hybrid exact %.3f < baseline %.3f".formatted(
                    hybrid.exactRate(), baseline.exactRate()));
        }
        hybridFailures.stream()
                .filter(failure -> failure.priority()
                        == NodeResolutionEvalScenario.Priority.REGRESSION_CRITICAL)
                .forEach(failure -> failures.add(
                        "critical case failed: %s %d/%d".formatted(
                                failure.candidate(), failure.passedRuns(), failure.totalRuns())));
        Map<NodeResolutionEvalScenario.Domain, Aggregate> baselineDomains =
                domainAggregates.getOrDefault("source-baseline", Map.of());
        Map<NodeResolutionEvalScenario.Domain, Aggregate> hybridDomains =
                domainAggregates.getOrDefault("hybrid-embedding", Map.of());
        for (NodeResolutionEvalScenario.Domain domain : NodeResolutionEvalScenario.Domain.values()) {
            Aggregate baselineDomain = baselineDomains.get(domain);
            Aggregate hybridDomain = hybridDomains.get(domain);
            if (baseline != null && baselineDomain != null && hybridDomain != null
                    && hybridDomain.f1() + 0.000_001 < baselineDomain.f1()) {
                failures.add("%s hybrid F1 %.3f < baseline %.3f".formatted(
                        domain, hybridDomain.f1(), baselineDomain.f1()));
            }
        }
        return new GateResult(failures.isEmpty(), failures);
    }

    private List<FailureSummary> failureSummaries(
            String variant,
            List<NodeResolutionEvalScenario> scenarios,
            List<RetrievalRun> retrievalRuns,
            List<VariantRun> variantRuns
    ) {
        Map<String, RetrievalRun> retrievalByScenario = retrievalRuns.stream()
                .collect(Collectors.toMap(RetrievalRun::scenarioId, Function.identity()));
        Map<String, List<VariantRun>> runsByScenario = variantRuns.stream()
                .filter(run -> run.variant().equals(variant))
                .collect(Collectors.groupingBy(VariantRun::scenarioId));
        List<FailureSummary> failures = new ArrayList<>();

        for (NodeResolutionEvalScenario scenario : scenarios) {
            RetrievalRun retrieval = retrievalByScenario.get(scenario.id());
            List<VariantRun> scenarioRuns = runsByScenario.getOrDefault(scenario.id(), List.of());
            for (NodeResolutionEvalScenario.ExpectedResolution expected : scenario.expected()) {
                List<DecisionScore> decisions = scenarioRuns.stream()
                        .flatMap(run -> run.score().decisions().stream())
                        .filter(decision -> decision.expected().candidate().equals(expected.candidate()))
                        .toList();
                long passed = decisions.stream().filter(DecisionScore::passed).count();
                if (decisions.isEmpty() || passed == decisions.size()) {
                    continue;
                }

                FailureStage stage = failureStage(expected, retrieval, scenarioRuns);
                Map<String, Long> actualOutcomes = decisions.stream()
                        .filter(decision -> !decision.passed())
                        .map(this::describeActual)
                        .collect(Collectors.groupingBy(
                                Function.identity(), LinkedHashMap::new, Collectors.counting()));
                failures.add(new FailureSummary(
                        scenario.id(), scenario.domain(), expected.priority(), stage,
                        expected.candidate(), describeExpected(expected), actualOutcomes,
                        failureCause(expected, stage, decisions), (int) passed, decisions.size()
                ));
            }
        }
        return failures;
    }

    private FailureStage failureStage(
            NodeResolutionEvalScenario.ExpectedResolution expected,
            RetrievalRun retrieval,
            List<VariantRun> scenarioRuns
    ) {
        if (expected.action() == NodeResolutionResult.Action.REUSE) {
            boolean included = scenarioRuns.stream().findFirst()
                    .map(run -> run.candidateSubjects().contains(expected.canonicalSubject()))
                    .orElse(false);
            if (included) {
                return FailureStage.NODE_RESOLUTION;
            }
            boolean hybrid = scenarioRuns.stream().findFirst()
                    .map(run -> run.variant().equals("hybrid-embedding"))
                    .orElse(false);
            if (hybrid) {
                return FailureStage.HYBRID_RETRIEVAL;
            }
            boolean retrieved = retrieval != null && retrieval.subjects().stream()
                    .anyMatch(subject -> subject.title().equals(expected.canonicalSubject()));
            if (!retrieved) {
                return FailureStage.SOURCE_RETRIEVAL;
            }
            return FailureStage.SUBJECT_RERANK;
        }
        return FailureStage.NODE_RESOLUTION;
    }

    private String failureCause(
            NodeResolutionEvalScenario.ExpectedResolution expected,
            FailureStage stage,
            List<DecisionScore> decisions
    ) {
        if (stage == FailureStage.SOURCE_RETRIEVAL) {
            return "관련 Subject를 포함한 Source가 상위 %d개에서 탈락".formatted(SOURCE_LIMIT);
        }
        if (stage == FailureStage.SUBJECT_RERANK) {
            return "Source 후보에는 있었지만 상위 %d개 Subject 후보에서 탈락".formatted(topK);
        }
        if (stage == FailureStage.HYBRID_RETRIEVAL) {
            return "Source와 Subject 직접 임베딩 후보의 합집합에서도 정답 Subject가 누락";
        }
        boolean createdInstead = decisions.stream().filter(decision -> !decision.passed())
                .map(DecisionScore::actual).anyMatch(actual -> actual != null
                        && actual.action() == NodeResolutionResult.Action.CREATE);
        if (expected.action() == NodeResolutionResult.Action.REUSE && createdInstead) {
            return "정답 후보가 있었지만 동일 개념으로 판단하지 못함";
        }
        boolean reusedInstead = decisions.stream().filter(decision -> !decision.passed())
                .map(DecisionScore::actual).anyMatch(actual -> actual != null
                        && actual.action() == NodeResolutionResult.Action.REUSE);
        if (expected.action() == NodeResolutionResult.Action.CREATE && reusedInstead) {
            return "관련 있지만 범위가 다른 Subject를 동일 개념으로 병합";
        }
        return "Node Resolution의 action 또는 canonical Subject 판정 불일치";
    }

    private String describeActual(DecisionScore decision) {
        if (decision.actual() == null) {
            return "응답 누락";
        }
        if (decision.actual().action() == NodeResolutionResult.Action.REUSE) {
            return "REUSE: " + decision.selectedSubject();
        }
        return "CREATE: " + decision.actual().value();
    }

    private String describeExpected(NodeResolutionEvalScenario.ExpectedResolution expected) {
        return expected.action() + ": " + expected.canonicalSubject();
    }

    private EmbeddingCall embed(List<String> inputs) {
        long startedAt = System.nanoTime();
        var response = embeddingModel.embedForResponse(inputs);
        long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        List<float[]> vectors = response.getResults().stream()
                .sorted(Comparator.comparingInt(result -> result.getIndex()))
                .map(result -> result.getOutput()).toList();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return new EmbeddingCall(vectors, TokenUsage.from(usage), latency);
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String selectedTitle(
            NodeResolutionResult.Decision decision,
            List<String> candidateSubjects
    ) {
        if (decision == null || decision.action() != NodeResolutionResult.Action.REUSE
                || decision.reuseIndex() < 1 || decision.reuseIndex() > candidateSubjects.size()) {
            return null;
        }
        return candidateSubjects.get(decision.reuseIndex() - 1);
    }

    private static List<NodeResolutionInput.Candidate> indexedCandidates(List<String> candidates) {
        return IntStream.range(0, candidates.size())
                .mapToObj(index -> new NodeResolutionInput.Candidate(index + 1, candidates.get(index)))
                .toList();
    }

    /** 범위를 벗어난 index는 채점에서 답하지 않은 것으로 본다. */
    private static String candidateOf(NodeResolutionResult.Decision decision, List<String> candidates) {
        if (decision == null || decision.candidateIndex() < 1
                || decision.candidateIndex() > candidates.size()) {
            return null;
        }
        return candidates.get(decision.candidateIndex() - 1);
    }

    private static List<NodeResolutionInput.ExistingSubject> indexed(List<String> subjects) {
        return IntStream.range(0, subjects.size())
                .mapToObj(index -> new NodeResolutionInput.ExistingSubject(index + 1, subjects.get(index)))
                .toList();
    }

    private static String searchable(String value) {
        return NodeTitleNormalizer.normalize(value).toLowerCase(Locale.ROOT);
    }

    private static Set<String> tokens(String value) {
        String expanded = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(expanded.split("[^\\p{L}\\p{N}+#.]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> ngrams(String value, int length) {
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index <= value.length() - length; index++) {
            values.add(value.substring(index, index + length));
        }
        return values;
    }

    private static String acronym(String value) {
        return tokens(value).stream().filter(token -> !token.isBlank())
                .map(token -> token.substring(0, 1)).collect(Collectors.joining());
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return intersection.size() / (double) union.size();
    }

    private Path writeReport(ComparisonReport report) throws Exception {
        Files.createDirectories(REPORT_DIRECTORY);
        Path path = REPORT_DIRECTORY.resolve(
                "pipeline-comparison-" + Instant.now().toString().replace(':', '-') + ".json");
        new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), report);
        return path;
    }

    private ComparisonReport loadPreviousReport() throws Exception {
        if (previousReport == null || previousReport.isBlank()) {
            return null;
        }
        Path path = Path.of(previousReport).toAbsolutePath().normalize();
        ComparisonReport report = new ObjectMapper().findAndRegisterModules()
                .readValue(path.toFile(), ComparisonReport.class);
        if (!report.datasetVersion().equals(NodeResolutionEvalTestData.VERSION)
                || !report.model().equals(model)
                || !report.embeddingModel().equals(EMBEDDING_MODEL)
                || report.sourceLimit() != SOURCE_LIMIT
                || report.subjectTopK() != topK
                || !report.variants().equals(variants)) {
            throw new IllegalArgumentException(
                    "previous report configuration does not match the current evaluation");
        }
        System.out.printf("[node-resolution-eval] resume=%s previousRuns=%d additionalRuns=%d%n",
                path, report.runs(), runs);
        return report;
    }

    private Set<String> parseVariants() {
        Set<String> allowed = Set.of(
                "source-baseline", "subject-embedding-only", "hybrid-embedding");
        String configured = System.getProperty(
                "eval.variants",
                "source-baseline,subject-embedding-only,hybrid-embedding"
        );
        Set<String> selected = java.util.Arrays.stream(configured.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selected.isEmpty() || !allowed.containsAll(selected)) {
            throw new IllegalArgumentException("unsupported eval variants: " + configured);
        }
        return Set.copyOf(selected);
    }

    private RetrievalRun findSourceRetrieval(List<RetrievalRun> runs, String scenarioId) {
        return runs.stream().filter(run -> run.scenarioId().equals(scenarioId))
                .findFirst().orElse(null);
    }

    private SubjectEmbeddingRun findSubjectEmbedding(
            List<SubjectEmbeddingRun> runs,
            String scenarioId
    ) {
        return runs.stream().filter(run -> run.scenarioId().equals(scenarioId))
                .findFirst().orElse(null);
    }

    private void writeMarkdown(Path jsonPath, ComparisonReport report) throws Exception {
        StringBuilder markdown = new StringBuilder("# Node Resolution Pipeline Comparison\n\n");
        markdown.append("- model: ").append(report.model()).append('\n');
        markdown.append("- golden dataset: ").append(report.datasetVersion()).append('\n');
        markdown.append("- runs: ").append(report.runs()).append('\n');
        markdown.append("- source limit: ").append(report.sourceLimit()).append('\n');
        markdown.append("- subject topK: ").append(report.subjectTopK()).append('\n');
        markdown.append("- variants: ").append(String.join(", ", report.variants()))
                .append("\n\n");
        markdown.append("## Stage metrics\n\n");
        markdown.append("- source Recall@").append(report.sourceLimit()).append(": ")
                .append("%.3f".formatted(report.retrieval().sourceRecallAtK())).append('\n');
        markdown.append("- conditional subject Recall@").append(report.subjectTopK()).append(": ")
                .append("%.3f".formatted(report.retrieval().subjectRecallAtK())).append('\n');
        markdown.append("- end-to-end subject Recall@").append(report.subjectTopK()).append(": ")
                .append("%.3f".formatted(report.retrieval().endToEndSubjectRecallAtK())).append('\n');
        markdown.append("- subject MRR: ")
                .append("%.3f".formatted(report.retrieval().meanReciprocalRank())).append("\n\n");
        markdown.append("- direct Subject embedding Recall@").append(report.subjectTopK()).append(": ")
                .append("%.3f".formatted(report.hybridRetrieval().directSubjectRecallAtK())).append('\n');
        markdown.append("- direct Subject embedding MRR: ")
                .append("%.3f".formatted(report.hybridRetrieval().directSubjectMrr())).append('\n');
        markdown.append("- hybrid union Recall: ")
                .append("%.3f".formatted(report.hybridRetrieval().unionRecall())).append('\n');
        markdown.append("- hybrid average candidates: ")
                .append("%.1f".formatted(report.hybridRetrieval().averageCandidates())).append("\n\n");
        markdown.append("- parallel retrieval p50: ")
                .append(report.hybridRetrieval().medianParallelLatencyMillis()).append("ms\n");
        markdown.append("- additional Subject embedding tokens: ")
                .append("%.1f".formatted(
                        report.hybridRetrieval().averageAdditionalEmbeddingTokens()))
                .append("\n\n");
        markdown.append("| variant | precision | recall | F1 | exact | false merge | missed | wrong | p50(ms) | prompt tokens | candidates |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        report.aggregates().forEach((name, value) -> markdown.append("| ").append(name).append(" | ")
                .append("%.3f".formatted(value.precision())).append(" | ")
                .append("%.3f".formatted(value.recall())).append(" | ")
                .append("%.3f".formatted(value.f1())).append(" | ")
                .append("%.3f".formatted(value.exactRate())).append(" | ")
                .append(value.falseMerge()).append(" | ").append(value.missedReuse()).append(" | ")
                .append(value.wrongReuse()).append(" | ").append(value.medianLatencyMillis()).append(" | ")
                .append("%.1f".formatted(value.averagePromptTokens())).append(" | ")
                .append("%.1f".formatted(value.averageCandidates())).append(" |\n"));
        appendFailureSection(markdown, "Hybrid embedding 실패 사례", report.hybridFailures());
        if (report.variants().contains("subject-embedding-only")) {
            appendFailureSection(
                    markdown,
                    "Subject embedding only 실패 사례",
                    report.subjectEmbeddingOnlyFailures()
            );
        }
        if (report.variants().contains("source-baseline")) {
            appendFailureSection(markdown, "Baseline 실패 사례", report.baselineFailures());
        }

        markdown.append("\n## 우선 개선 대상\n\n");
        List<FailureSummary> criticalFailures = report.hybridFailures().stream()
                .filter(failure -> failure.priority()
                        == NodeResolutionEvalScenario.Priority.REGRESSION_CRITICAL)
                .toList();
        if (criticalFailures.isEmpty()) {
            markdown.append("- 이번 실행에서 핵심 회귀 사례는 모두 통과했습니다.\n");
        } else {
            criticalFailures.forEach(failure -> markdown.append("- **")
                    .append(failure.candidate()).append("** (`")
                    .append(failure.stage()).append("`): ")
                    .append(failure.cause()).append('\n'));
        }
        markdown.append("\n## Domain slices\n\n");
        markdown.append("| variant | domain | F1 | exact | false merge |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: |\n");
        report.domainAggregates().forEach((variant, domains) -> domains.forEach((domain, value) ->
                markdown.append("| ").append(variant).append(" | ").append(domain).append(" | ")
                        .append("%.3f".formatted(value.f1())).append(" | ")
                        .append("%.3f".formatted(value.exactRate())).append(" | ")
                        .append(value.falseMerge()).append(" |\n")));
        markdown.append("\n## Regression gate\n\n");
        if (report.gates().passed()) {
            markdown.append("PASS\n");
        } else {
            report.gates().failures().forEach(failure -> markdown.append("- ").append(failure).append('\n'));
        }
        Files.writeString(Path.of(jsonPath.toString().replace(".json", ".md")),
                markdown, StandardCharsets.UTF_8);
    }

    private void appendFailureSection(
            StringBuilder markdown,
            String title,
            List<FailureSummary> failures
    ) {
        markdown.append("\n## ").append(title).append("\n\n");
        if (failures.isEmpty()) {
            markdown.append("실패 없음\n");
            return;
        }
        markdown.append("| 우선순위 | 단계 | 후보 | 기대 | 실제 실패 결과 | 원인 | 성공 횟수 |\n");
        markdown.append("| --- | --- | --- | --- | --- | --- | ---: |\n");
        failures.forEach(failure -> markdown.append("| ")
                .append(failure.priority()).append(" | ")
                .append(failure.stage()).append(" | ")
                .append(failure.candidate()).append(" | ")
                .append(failure.expected()).append(" | ")
                .append(failure.actualOutcomes().entrySet().stream()
                        .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                        .collect(Collectors.joining("<br>")))
                .append(" | ").append(failure.cause()).append(" | ")
                .append(failure.passedRuns()).append('/').append(failure.totalRuns()).append(" |\n"));

        Map<FailureStage, Long> counts = failures.stream()
                .collect(Collectors.groupingBy(
                        FailureSummary::stage, LinkedHashMap::new, Collectors.counting()));
        markdown.append("\n유형별 집계: ")
                .append(counts.entrySet().stream()
                        .map(entry -> entry.getKey() + " " + entry.getValue() + "건")
                        .collect(Collectors.joining(", ")))
                .append("\n");
    }

    private void printSummary(ComparisonReport report, Path reportPath) {
        report.aggregates().forEach((variant, value) -> System.out.printf(
                "[node-resolution-eval] %s F1=%.3f exact=%.3f falseMerge=%d promptTokens=%.1f%n",
                variant, value.f1(), value.exactRate(), value.falseMerge(), value.averagePromptTokens()));
        Map<String, Map<String, Long>> successCounts = report.variantRuns().stream()
                .flatMap(result -> result.score().decisions().stream()
                        .map(decision -> Map.entry(
                                result.variant() + " / " + result.scenarioId() + " / "
                                        + decision.expected().candidate(),
                                decision.passed())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.groupingBy(entry -> entry.getValue() ? "pass" : "fail",
                                LinkedHashMap::new, Collectors.counting())));
        successCounts.forEach((key, counts) -> System.out.printf(
                "[node-resolution-eval] %s: %d/%d 성공%n",
                key, counts.getOrDefault("pass", 0L),
                counts.getOrDefault("pass", 0L) + counts.getOrDefault("fail", 0L)));
        System.out.printf("[node-resolution-eval] sourceRecall=%.3f lexicalRecall=%.3f directEmbeddingRecall=%.3f unionRecall=%.3f gate=%s report=%s%n",
                report.retrieval().sourceRecallAtK(), report.retrieval().subjectRecallAtK(),
                report.hybridRetrieval().directSubjectRecallAtK(), report.hybridRetrieval().unionRecall(),
                report.gates().passed() ? "PASS" : "FAIL",
                reportPath.toAbsolutePath().normalize());
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
        String key = Dotenv.configure().directory(envPath.getParent().toString())
                .filename(envPath.getFileName().toString()).ignoreIfMissing().load()
                .get("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required");
        }
        return key;
    }

    private LlmProperties openAiProperties() {
        return new LlmProperties(LlmProvider.OPENAI, model, 2_000, 0.0,
                new LlmProperties.OpenAi(model.startsWith("gpt-5") ? "low" : null), null);
    }

    private record RankedSource(NodeResolutionEvalScenario.SimilarSourceFixture source, double similarity) {
    }

    private record SubjectEvidence(String title, int sourceRank, double sourceSimilarity) {
    }

    private record RankedSubject(String title, double similarity) {
    }

    private record SubjectEmbeddingRun(
            String scenarioId,
            NodeResolutionEvalScenario.Domain domain,
            Map<String, List<RankedSubject>> rankedByCandidate,
            List<String> subjects,
            int expectedReuse,
            int hits,
            double reciprocalRankSum,
            TokenUsage embeddingUsage,
            long latencyMillis
    ) {
    }

    private record HybridRetrievalRun(
            String scenarioId,
            NodeResolutionEvalScenario.Domain domain,
            List<String> subjects,
            long parallelLatencyMillis
    ) {
    }

    private record CallResult(NodeResolutionResult output, TokenUsage usage, long latencyMillis) {
    }

    private record EmbeddingCall(List<float[]> vectors, TokenUsage usage, long latencyMillis) {
    }

    private record RetrievalRun(
            String scenarioId,
            NodeResolutionEvalScenario.Domain domain,
            List<RankedSource> sources,
            List<SubjectEvidence> subjects,
            int expectedReuse,
            int sourceHits,
            TokenUsage embeddingUsage,
            long latencyMillis
    ) {
    }

    private record RetrievalAggregate(
            double sourceRecallAtK,
            double subjectRecallAtK,
            double endToEndSubjectRecallAtK,
            double meanReciprocalRank,
            long averageLatencyMillis,
            double averageEmbeddingTokens,
            double totalEmbeddingCostIndex
    ) {
    }

    private record HybridRetrievalAggregate(
            double directSubjectRecallAtK,
            double unionRecall,
            double directSubjectMrr,
            double averageCandidates,
            long medianParallelLatencyMillis,
            double averageAdditionalEmbeddingTokens,
            double totalAdditionalEmbeddingCostIndex
    ) {
    }

    private record DecisionScore(
            NodeResolutionEvalScenario.ExpectedResolution expected,
            NodeResolutionResult.Decision actual,
            String selectedSubject,
            boolean passed
    ) {
    }

    private enum FailureStage {
        SOURCE_RETRIEVAL,
        SUBJECT_RERANK,
        HYBRID_RETRIEVAL,
        NODE_RESOLUTION
    }

    private record FailureSummary(
            String scenarioId,
            NodeResolutionEvalScenario.Domain domain,
            NodeResolutionEvalScenario.Priority priority,
            FailureStage stage,
            String candidate,
            String expected,
            Map<String, Long> actualOutcomes,
            String cause,
            int passedRuns,
            int totalRuns
    ) {
    }

    private record Score(
            int correct,
            int expected,
            int trueReuse,
            int predictedReuse,
            int expectedReuse,
            int falseMerge,
            int missedReuse,
            int wrongReuse,
            double precision,
            double recall,
            double f1,
            boolean exact,
            List<DecisionScore> decisions
    ) {
    }

    private record VariantRun(
            String scenarioId,
            NodeResolutionEvalScenario.Domain domain,
            String variant,
            int run,
            List<String> candidateSubjects,
            long latencyMillis,
            TokenUsage usage,
            double costIndex,
            Score score,
            NodeResolutionResult output
    ) {
    }

    private record Aggregate(
            double precision,
            double recall,
            double f1,
            double exactRate,
            int falseMerge,
            int missedReuse,
            int wrongReuse,
            long medianLatencyMillis,
            double averagePromptTokens,
            double averageCostIndex,
            double averageCandidates
    ) {
    }

    private record TokenUsage(int promptTokens, int completionTokens, int totalTokens, long cachedPromptTokens) {
        static TokenUsage from(Usage usage) {
            if (usage == null) {
                return new TokenUsage(0, 0, 0, 0);
            }
            return new TokenUsage(value(usage.getPromptTokens()), value(usage.getCompletionTokens()),
                    value(usage.getTotalTokens()),
                    usage.getCacheReadInputTokens() == null ? 0 : usage.getCacheReadInputTokens());
        }

        private static int value(Integer value) {
            return value == null ? 0 : value;
        }
    }

    private record CostRates(
            String unit,
            double chatInputPerMillion,
            double chatCachedInputPerMillion,
            double chatOutputPerMillion,
            double embeddingInputPerMillion
    ) {
        static CostRates fromSystemProperties() {
            return new CostRates(System.getProperty("eval.cost.unit", "relative-index"),
                    rate("eval.cost.chatInput", 1.0), rate("eval.cost.chatCachedInput", 0.1),
                    rate("eval.cost.chatOutput", 4.0), rate("eval.cost.embeddingInput", 0.02));
        }

        double chatCost(TokenUsage usage) {
            long cached = Math.min(usage.promptTokens(), usage.cachedPromptTokens());
            long uncached = Math.max(0, usage.promptTokens() - cached);
            return (uncached * chatInputPerMillion + cached * chatCachedInputPerMillion
                    + usage.completionTokens() * chatOutputPerMillion) / 1_000_000.0;
        }

        double embeddingCost(TokenUsage usage) {
            return usage.totalTokens() * embeddingInputPerMillion / 1_000_000.0;
        }

        private static double rate(String key, double defaultValue) {
            return Double.parseDouble(System.getProperty(key, String.valueOf(defaultValue)));
        }
    }

    private record GateResult(boolean passed, List<String> failures) {
    }

    private record ComparisonReport(
            String generatedAt,
            String datasetVersion,
            String model,
            String embeddingModel,
            int runs,
            int sourceLimit,
            int subjectTopK,
            Set<String> variants,
            CostRates costRates,
            RetrievalAggregate retrieval,
            HybridRetrievalAggregate hybridRetrieval,
            Map<String, Aggregate> aggregates,
            Map<String, Map<NodeResolutionEvalScenario.Domain, Aggregate>> domainAggregates,
            List<FailureSummary> hybridFailures,
            List<FailureSummary> subjectEmbeddingOnlyFailures,
            List<FailureSummary> baselineFailures,
            List<RetrievalRun> retrievalRuns,
            List<SubjectEmbeddingRun> subjectEmbeddingRuns,
            List<HybridRetrievalRun> hybridRetrievalRuns,
            List<VariantRun> variantRuns,
            List<String> errors,
            GateResult gates
    ) {
    }
}
