package com.swimming.backend.knowledge.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swimming.backend.knowledge.domain.NodeTitleNormalizer;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.CREATE;
import static com.swimming.backend.knowledge.benchmark.CategoryAssignmentEvalScenario.Action.REUSE;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm-eval")
@Tag("category-assignment-comparison")
class CategoryAssignmentEmbeddingEvalTest {
    static final String MODEL = "text-embedding-3-small";
    static final int DIMENSIONS = 768;
    static final double RATE_PER_MILLION = 0.02;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Test
    void 로컬_Category_제안의_임베딩_거리와_임계값별_판정_품질을_측정한다() throws Exception {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            key = Dotenv.configure().directory(".").filename(".env.local").ignoreIfMissing()
                    .load().get("OPENAI_API_KEY");
        }
        assertThat(key).as("OPENAI_API_KEY 설정 여부").isNotNull();
        // 실패 시 키를 assertion 출력에 포함하지 않는다.
        if (key.isBlank()) throw new IllegalStateException("OPENAI_API_KEY가 비어 있다");

        var scenarios = CategoryAssignmentEvalTestData.scenarios();
        var calls = new ArrayList<Call>();
        var cases = new ArrayList<CaseResult>();
        var vectors = new LinkedHashMap<String, double[]>();
        var thresholds = new TreeSet<Double>();
        for (int i = 0; i <= 40; i++) thresholds.add(i / 20.0);
        Path directory = Path.of("build/reports/category-assignment-eval");
        Files.createDirectories(directory);
        String stamp = Instant.now().toString().replace(':', '-');
        Path raw = directory.resolve("category-embedding-" + stamp + ".json");

        // 후보 제목 벡터를 준비한다. 단독 Source의 Category도 여기서 준비하지만
        // 해당 leave-one-out 케이스의 검색 후보에서는 반드시 제외한다.
        for (var category : CategoryAssignmentEvalTestData.snapshot().categories()) {
            embed(category.title(), "candidate-preparation", null, key, vectors, calls);
        }
        for (var scenario : scenarios) {
            var exact = scenario.existingCategories().stream().filter(c ->
                    NodeTitleNormalizer.normalize(c.title()).equals(
                            NodeTitleNormalizer.normalize(scenario.source().proposedCategoryTitle())))
                    .findFirst();
            if (exact.isPresent()) {
                cases.add(new CaseResult(scenario.id(), scenario.source().title(),
                        scenario.source().proposedCategoryTitle(), scenario.expected(),
                        true, false, 0, List.of(new Candidate(exact.get().id(), exact.get().title(), 0))));
                continue;
            }
            String text = embeddingText(scenario.source().proposedCategoryTitle());
            boolean reused = vectors.containsKey(text);
            long start = System.nanoTime();
            embed(text, "query", scenario.id(), key, vectors, calls);
            var ranked = scenario.existingCategories().stream().map(c -> new Candidate(c.id(), c.title(),
                    distance(vectors.get(text), vectors.get(embeddingText(c.title())))))
                    // 거리 동률이면 fixture 후보 순서를 유지한다.
                    .sorted(Comparator.comparingDouble(Candidate::distance)).toList();
            long latency = (System.nanoTime() - start) / 1_000_000;
            cases.add(new CaseResult(scenario.id(), scenario.source().title(),
                    scenario.source().proposedCategoryTitle(), scenario.expected(), false, reused, latency, ranked));
            thresholds.add(ranked.getFirst().distance());
            var report = report(stamp, calls, cases, thresholds);
            mapper.writerWithDefaultPrettyPrinter().writeValue(raw.toFile(), report);
        }
        var report = report(stamp, calls, cases, thresholds);
        mapper.writerWithDefaultPrettyPrinter().writeValue(raw.toFile(), report);
        Files.writeString(directory.resolve("category-embedding-" + stamp + ".md"), markdown(report));
        System.out.println("Category embedding report: " + raw.toAbsolutePath());
        assertThat(cases).hasSize(scenarios.size());
        assertThat(calls).allMatch(c -> c.success());
    }

    private void embed(String title, String stage, String scenarioId, String key,
                       Map<String, double[]> vectors, List<Call> calls) throws Exception {
        String text = embeddingText(title);
        if (vectors.containsKey(text)) return;
        var request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/embeddings"))
                .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                        "model", MODEL, "dimensions", DIMENSIONS, "input", text, "encoding_format", "float"))))
                .build();
        long start = System.nanoTime();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = (System.nanoTime() - start) / 1_000_000;
            if (response.statusCode() != 200) {
                calls.add(new Call(stage, scenarioId, text, latency, false, response.statusCode(), null, null, null));
                throw new IllegalStateException("임베딩 API HTTP " + response.statusCode());
            }
            JsonNode body = mapper.readTree(response.body());
            JsonNode vector = body.path("data").get(0).path("embedding");
            if (vector.size() != DIMENSIONS) throw new IllegalStateException("임베딩 차원 불일치");
            double[] values = new double[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) values[i] = vector.get(i).asDouble();
            JsonNode usage = body.get("usage");
            Long tokens = usage != null && usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : null;
            calls.add(new Call(stage, scenarioId, text, latency, true, 200, tokens,
                    tokens == null ? null : cost(tokens), usage));
            vectors.put(text, values);
        } catch (Exception e) {
            if (calls.isEmpty() || !calls.getLast().input().equals(text)) {
                calls.add(new Call(stage, scenarioId, text, (System.nanoTime() - start) / 1_000_000,
                        false, null, null, null, null));
            }
            Path failure = Path.of("build/reports/category-assignment-eval/embedding-failure-"
                    + Instant.now().toString().replace(':', '-') + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(failure.toFile(), calls);
            throw new IllegalStateException("임베딩 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    static String embeddingText(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    static double cost(long tokens) { return tokens * RATE_PER_MILLION / 1_000_000; }

    static double distance(double[] left, double[] right) {
        if (left.length != right.length) throw new IllegalArgumentException("벡터 차원 불일치");
        double dot = 0, l = 0, r = 0;
        for (int i = 0; i < left.length; i++) { dot += left[i] * right[i]; l += left[i] * left[i]; r += right[i] * right[i]; }
        if (l == 0 || r == 0) throw new IllegalArgumentException("영벡터");
        return Math.max(0, Math.min(2, 1 - dot / Math.sqrt(l * r)));
    }

    static Score score(List<CaseResult> cases, double threshold) {
        int correct = 0, wrongReuse = 0, overCreate = 0;
        for (var c : cases) {
            var nearest = c.candidates().getFirst();
            boolean reuse = c.normalizedMatch() || nearest.distance() <= threshold;
            if (reuse) {
                if (c.expected().action() == REUSE && nearest.id().equals(c.expected().categoryId())) correct++;
                else wrongReuse++;
            } else if (c.expected().action() == CREATE) correct++;
            else overCreate++;
        }
        return new Score(threshold, cases.size(), correct, wrongReuse, overCreate);
    }

    private Report report(String stamp, List<Call> calls, List<CaseResult> cases, TreeSet<Double> thresholds) {
        return new Report(stamp, CategoryAssignmentEvalTestData.VERSION, MODEL, DIMENSIONS,
                "strip + lowercase(Locale.ROOT)", "OpenAI direct, sequential, timeout 45s, retries 0",
                RATE_PER_MILLION, "2026-09-18", "https://developers.openai.com/api/docs/models/text-embedding-3-small",
                "수작업 제안 이름, 단일 폴더 탐색. 운영 임계값을 선정하지 않는다. digest 비용 미포함.",
                List.copyOf(calls), List.copyOf(cases), thresholds.stream().map(t -> score(cases, t)).toList());
    }

    private String markdown(Report report) {
        long preparationTokens = report.calls().stream().filter(c -> c.stage().equals("candidate-preparation"))
                .filter(c -> c.inputTokens() != null).mapToLong(Call::inputTokens).sum();
        long queryTokens = report.calls().stream().filter(c -> c.stage().equals("query"))
                .filter(c -> c.inputTokens() != null).mapToLong(Call::inputTokens).sum();
        StringBuilder out = new StringBuilder("# Category 임베딩 탐색 평가\n\n");
        out.append("- Fixture: ").append(report.fixtureVersion()).append("\n- 모델: ").append(MODEL)
                .append(" / ").append(DIMENSIONS).append("차원\n- ").append(report.limitations()).append("\n")
                .append("- 단가: 입력 100만 토큰당 $0.02. [공식 출처](").append(report.priceSource()).append(")\n")
                .append("- 후보 준비: ").append(preparationTokens).append("토큰 / $").append(cost(preparationTokens)).append("\n")
                .append("- 추가 질의: ").append(queryTokens).append("토큰 / $").append(cost(queryTokens)).append("\n")
                .append("- 실제 API 호출: ").append(report.calls().size()).append("회, 재시도 없음\n")
                .append("- CREATE 2건의 제목 벡터는 준비 단계에서 생성한 동일 제목 벡터를 재사용한다. 제거된 Category는 검색 후보에 포함하지 않는다.\n\n")
                .append("| 제안 이름 | 정답 | 최근접 후보 | 거리 | 정규화 일치 |\n| --- | --- | --- | --- | --- |\n");
        for (var c : report.cases()) {
            var nearest = c.candidates().getFirst();
            String expected = c.expected().action() == CREATE ? "CREATE" : CategoryAssignmentEvalTestData.snapshot()
                    .categories().stream().filter(x -> x.id().equals(c.expected().categoryId())).findFirst().orElseThrow().title();
            out.append("| ").append(c.proposedTitle()).append(" | ").append(expected).append(" | ").append(nearest.title())
                    .append(" | ").append(String.format(Locale.ROOT, "%.6f", nearest.distance())).append(" | ")
                    .append(c.normalizedMatch() ? "예 (거리 계산 생략)" : "아니오").append(" |\n");
        }
        out.append("\n정규화 일치 행의 0은 처리상 표기이며 실측 임베딩 거리가 아니다.\n\n")
                .append("| 임계값 | 전체 정답 / 건수 | 임베딩 판정 정답 / 건수 | Wrong reuse | Over-create |\n| --- | --- | --- | --- | --- |\n");
        var embeddingCases = report.cases().stream().filter(c -> !c.normalizedMatch()).toList();
        for (var s : report.scores()) {
            var embeddingScore = score(embeddingCases, s.threshold());
            out.append("| ").append(String.format(Locale.ROOT, "%.6f", s.threshold())).append(" | ")
                    .append(s.correct()).append(" / ").append(s.total()).append(" | ")
                    .append(embeddingScore.correct()).append(" / ").append(embeddingScore.total()).append(" | ")
                    .append(s.wrongReuse()).append(" | ").append(s.overCreate()).append(" |\n");
        }
        return out.toString();
    }

    record Call(String stage, String scenarioId, String input, long latencyMillis, boolean success,
                Integer httpStatus, Long inputTokens, Double costUsd, JsonNode usage) {}
    record Candidate(String id, String title, double distance) {}
    record CaseResult(String id, String sourceTitle, String proposedTitle, CategoryAssignmentEvalScenario.Expected expected,
                      boolean normalizedMatch, boolean vectorReused, long latencyMillis, List<Candidate> candidates) {}
    record Score(double threshold, int total, int correct, int wrongReuse, int overCreate) {}
    record Report(String executedAt, String fixtureVersion, String model, int dimensions, String preprocessing,
                  String executionPolicy, double inputRatePerMillion, String priceCheckedAt, String priceSource,
                  String limitations, List<Call> calls, List<CaseResult> cases, List<Score> scores) {}
}
