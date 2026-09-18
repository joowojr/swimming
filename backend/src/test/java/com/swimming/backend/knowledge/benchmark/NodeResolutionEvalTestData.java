package com.swimming.backend.knowledge.benchmark;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Domain.AI_PRODUCTIVITY;
import static com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Domain.JOB_POSTING;
import static com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Domain.KYOTO_TRAVEL;
import static com.swimming.backend.knowledge.benchmark.NodeResolutionEvalScenario.Domain.TECHNOLOGY;

final class NodeResolutionEvalTestData {

    static final String VERSION = "2026-09-10-v6";

    private NodeResolutionEvalTestData() {
    }

    /**
     * 판정만 재는 merge-only 변형이 쓰는 고정 재사용 후보.
     *
     * <p>검색을 타지 않고 코퍼스의 Subject를 코퍼스 순서 그대로 준다. 정답 대상이 항상 들어
     * 있으므로 실패는 판정 탓뿐이다.
     */
    static List<String> reusableSubjects(NodeResolutionEvalScenario scenario) {
        return List.copyOf(scenario.sourceCorpus().stream()
                .flatMap(source -> source.subjects().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    static List<NodeResolutionEvalScenario> scenarios() {
        return List.of(
                scenario("tech-auth-aliases", "인증 표준의 약어와 공식명", TECHNOLOGY,
                        "OIDC 로그인에서 PKCE로 Authorization Code 탈취를 막고 JWT 형식의 ID Token을 검증한다.",
                        List.of("OIDC", "PKCE", "JWT"), technologyCorpus(),
                        reuse("OIDC", "OpenID Connect"),
                        reuse("PKCE", "Proof Key for Code Exchange"),
                        reuse("JWT", "JSON Web Token")),
                scenario("tech-auth-boundaries", "인증 프로토콜과 하위 흐름의 경계", TECHNOLOGY,
                        "OAuth 2.0 권한 위임과 Refresh Token, Access Token 갱신 과정을 비교한다.",
                        List.of("OAuth 2.0", "Refresh Token", "액세스 토큰"), technologyCorpus(),
                        create("OAuth 2.0", "OAuth 2.0"),
                        reuse("Refresh Token", "OAuth Refresh Token"),
                        reuse("액세스 토큰", "Access Token")),
                scenario("tech-search", "데이터베이스 검색 개념", TECHNOLOGY,
                        "PostgreSQL FTS와 pg_trgm을 BM25, OpenSearch와 비교해 검색 품질과 인덱스 구성을 설명한다.",
                        List.of("Postgres FTS", "Okapi BM25", "PostgreSQL 트라이그램 검색"), technologyCorpus(),
                        reuse("Postgres FTS", "PostgreSQL Full Text Search"),
                        reuse("Okapi BM25", "BM25"),
                        reuse("PostgreSQL 트라이그램 검색", "pg_trgm")),

                scenario("job-screening", "지원 전형의 표현 차이", JOB_POSTING,
                        "신입 개발자 공고는 코딩 테스트와 테크니컬 인터뷰, 별도의 채용 과제를 포함한다.",
                        List.of("신입 채용", "코딩 테스트", "기술 면접", "채용 과제"), jobCorpus(),
                        reuse("신입 채용", "신입사원 채용"),
                        reuse("코딩 테스트", "코딩 시험"),
                        reuse("기술 면접", "테크니컬 인터뷰"),
                        create("채용 과제", "채용 과제")),
                scenario("job-compensation", "지원 공고의 보상과 고용 형태", JOB_POSTING,
                        "정규직 채용 공고가 스톡옵션과 유연 근무제를 제공하고 수습 기간을 안내한다.",
                        List.of("정규직 채용", "스톡옵션", "유연 근무", "수습 기간"), jobCorpus(),
                        reuse("정규직 채용", "정규직 채용"),
                        criticalReuse("스톡옵션", "주식매수선택권"),
                        reuse("유연 근무", "유연근무제"),
                        create("수습 기간", "수습 기간")),
                scenario("job-related-boundaries", "서로 다른 채용 단계의 경계", JOB_POSTING,
                        "지원자는 포트폴리오 검토 뒤 직무 인터뷰를 거치며 최종 합격 후 온보딩을 시작한다.",
                        List.of("포트폴리오 검토", "직무 인터뷰", "온보딩"), jobCorpus(),
                        reuse("포트폴리오 검토", "포트폴리오 심사"),
                        create("직무 인터뷰", "직무 인터뷰"),
                        create("온보딩", "온보딩")),

                scenario("kyoto-landmarks", "교토 명소의 한국어 표기", KYOTO_TRAVEL,
                        "아라시야마 치쿠린과 후시미 이나리 타이샤, 기요미즈데라를 하루에 방문한다.",
                        List.of("아라시야마 치쿠린", "후시미 이나리 타이샤", "청수사"), kyotoCorpus(),
                        reuse("아라시야마 치쿠린", "아라시야마 대나무숲"),
                        reuse("후시미 이나리 타이샤", "후시미 이나리 신사"),
                        reuse("청수사", "기요미즈데라")),
                scenario("kyoto-transit", "교토 교통수단과 상품의 경계", KYOTO_TRAVEL,
                        "ICOCA로 교토 지하철을 타고 이동하며 교토 버스 일일권 구매 여부를 비교한다.",
                        List.of("ICOCA", "교토 지하철", "교토 버스 일일권"), kyotoCorpus(),
                        criticalReuse("ICOCA", "이코카 교통카드"),
                        reuse("교토 지하철", "교토 지하철"),
                        create("교토 버스 일일권", "교토 버스 일일권")),
                scenario("kyoto-regional-boundaries", "교토와 인근 지역의 경계", KYOTO_TRAVEL,
                        "센본도리이와 기온 거리, 우지를 둘러보고 간사이 광역 교통권을 검토한다.",
                        List.of("센본도리이", "기온 거리", "우지 여행", "간사이 광역 교통권"), kyotoCorpus(),
                        reuse("센본도리이", "센본도리이"),
                        reuse("기온 거리", "기온 거리"),
                        create("우지 여행", "우지 여행"),
                        criticalCreate("간사이 광역 교통권", "간사이 광역 교통권")),

                scenario("ai-product-names", "AI 제품명의 한글 표기", AI_PRODUCTIVITY,
                        "생성 AI와 Notion AI를 함께 사용해 문서 작성과 문서 검색을 자동화한다.",
                        List.of("생성 AI", "Notion AI", "문서 검색"), aiProductivityCorpus(),
                        reuse("생성 AI", "생성형 AI"),
                        reuse("Notion AI", "노션 AI"),
                        reuse("문서 검색", "문서 검색")),
                scenario("ai-model-patterns", "AI 모델과 검색 패턴의 약어", AI_PRODUCTIVITY,
                        "프롬프트 설계에 RAG를 연결해 사내 문서를 검색하고 답변을 생성하는 구조를 설명한다.",
                        List.of("프롬프트 설계", "RAG", "벡터 검색"), aiProductivityCorpus(),
                        reuse("프롬프트 설계", "프롬프트 엔지니어링"),
                        reuse("RAG", "검색 증강 생성"),
                        create("벡터 검색", "벡터 검색")),
                scenario("ai-agent-boundaries", "AI 에이전트와 자동화의 경계", AI_PRODUCTIVITY,
                        "AI 에이전트가 할 일의 우선순위를 추천하는 생산성 흐름을 설명한다.",
                        List.of("AI 에이전트", "AI 작업 우선순위 추천"), aiProductivityCorpus(),
                        create("AI 에이전트", "AI 에이전트"),
                        create("AI 작업 우선순위 추천", "AI 작업 우선순위 추천"))
        );
    }

    private static List<NodeResolutionEvalScenario.SimilarSourceFixture> technologyCorpus() {
        return List.of(
                source("tech-oidc", "OpenID Connect 로그인과 PKCE를 적용한 인증 흐름을 설명한다.",
                        "OpenID Connect", "Proof Key for Code Exchange"),
                source("tech-token", "JWT 구조와 Access Token, ID Token의 검증 차이를 설명한다.",
                        "JSON Web Token", "Access Token", "ID Token"),
                source("tech-oauth", "OAuth Authorization Code와 Refresh Token 흐름을 설명한다.",
                        "OAuth Authorization Code", "OAuth Refresh Token"),
                source("tech-federation", "SAML 기반 연합 인증과 Identity Provider의 역할을 설명한다.",
                        "SAML 2.0", "Identity Provider"),
                source("tech-spring", "Spring Security에서 OAuth 로그인을 구성한다.",
                        "Spring Security", "OpenID Provider"),
                source("tech-postgres", "PostgreSQL Full Text Search와 pg_trgm 인덱스를 비교한다.",
                        "PostgreSQL Full Text Search", "pg_trgm"),
                source("tech-search-engine", "BM25 순위화와 OpenSearch 검색 방식을 설명한다.",
                        "BM25", "OpenSearch"),
                source("tech-session-security", "세션 쿠키 보호와 CSRF 방어 설정을 정리한다.",
                        "세션 쿠키", "CSRF 방어"),
                source("tech-passkeys", "WebAuthn과 패스키 기반 비밀번호 없는 로그인을 설명한다.",
                        "WebAuthn", "패스키"),
                source("tech-vector-index", "HNSW 인덱스와 벡터 데이터베이스의 근사 검색을 비교한다.",
                        "HNSW", "벡터 데이터베이스"),
                source("tech-search-ranking", "검색 결과 재순위화와 Reciprocal Rank Fusion을 설명한다.",
                        "검색 재순위화", "Reciprocal Rank Fusion"),
                source("tech-cache", "Redis 캐시와 캐시 무효화 전략을 정리한다.",
                        "Redis 캐시", "캐시 무효화"));
    }

    private static List<NodeResolutionEvalScenario.SimilarSourceFixture> jobCorpus() {
        return List.of(
                source("job-entry", "신입사원 채용과 경력직 채용의 지원 자격을 비교한다.",
                        "신입사원 채용", "경력직 채용"),
                source("job-screening", "서류 전형과 코딩 시험으로 지원자를 평가한다.",
                        "서류 전형", "코딩 시험"),
                source("job-interview", "테크니컬 인터뷰와 인성 면접의 평가 항목을 설명한다.",
                        "테크니컬 인터뷰", "인성 면접"),
                source("job-portfolio", "포트폴리오 심사와 인턴 채용 절차를 안내한다.",
                        "포트폴리오 심사", "인턴 채용"),
                source("job-employment", "정규직 채용과 계약직 고용 조건을 비교한다.",
                        "정규직 채용", "계약직 채용"),
                source("job-benefits", "주식매수선택권과 복리후생 제도를 소개한다.",
                        "주식매수선택권", "복리후생"),
                source("job-work-style", "유연근무제와 재택근무 운영 방식을 설명한다.",
                        "유연근무제", "재택근무"),
                source("job-resume", "이력서 검토와 자기소개서 평가 기준을 안내한다.",
                        "이력서 검토", "자기소개서"),
                source("job-offer", "입사 제안과 연봉 협상 절차를 설명한다.",
                        "입사 제안", "연봉 협상"),
                source("job-equity", "스톡옵션 베스팅과 행사 조건을 정리한다.",
                        "스톡옵션 베스팅", "스톡옵션 행사"),
                source("job-hours", "선택근무제와 근무시간 운영 원칙을 비교한다.",
                        "선택근무제", "근무시간"),
                source("job-probation", "수습 평가와 정규직 전환 절차를 안내한다.",
                        "수습 평가", "정규직 전환"));
    }

    private static List<NodeResolutionEvalScenario.SimilarSourceFixture> kyotoCorpus() {
        return List.of(
                source("kyoto-arashiyama", "교토 아라시야마 대나무숲과 도게츠교 산책 코스를 소개한다.",
                        "아라시야마 대나무숲", "도게츠교"),
                source("kyoto-inari", "후시미 이나리 신사와 센본도리이 방문 동선을 설명한다.",
                        "후시미 이나리 신사", "센본도리이"),
                source("kyoto-east", "기요미즈데라와 기온 거리를 잇는 교토 동부 여행 코스다.",
                        "기요미즈데라", "기온 거리"),
                source("kyoto-station", "교토역 교통 허브에서 시내버스로 환승하는 방법을 안내한다.",
                        "교토역 교통 허브", "교토 시내버스"),
                source("kyoto-rail", "게이한 전철과 교토 지하철의 노선 및 환승 방식을 비교한다.",
                        "게이한 전철", "교토 지하철"),
                source("kyoto-card", "이코카 교통카드와 간사이 교통패스의 사용 범위를 설명한다.",
                        "이코카 교통카드", "간사이 교통패스"),
                source("kyoto-nearby", "나라 공원과 오사카성으로 이동하는 간사이 근교 일정이다.",
                        "나라 공원", "오사카성"),
                source("kyoto-temples", "금각사와 은각사를 함께 방문하는 사찰 코스를 소개한다.",
                        "금각사", "은각사"),
                source("kyoto-bus", "교토 버스 노선과 지하철 환승 방법을 안내한다.",
                        "교토 버스", "버스 환승"),
                source("kyoto-food", "니시키 시장과 교토 전통 음식을 둘러보는 일정을 소개한다.",
                        "니시키 시장", "교토 전통 음식"),
                source("kyoto-stay", "교토 료칸과 도심 호텔의 숙박 경험을 비교한다.",
                        "교토 료칸", "교토 호텔"),
                source("kyoto-season", "교토 벚꽃 명소와 단풍 시기의 여행 동선을 정리한다.",
                        "교토 벚꽃", "교토 단풍"));
    }

    private static List<NodeResolutionEvalScenario.SimilarSourceFixture> aiProductivityCorpus() {
        return List.of(
                source("ai-products", "챗지피티와 노션 AI로 문서를 작성하는 방법을 설명한다.",
                        "챗지피티", "노션 AI"),
                source("ai-architecture", "대규모 언어 모델과 검색 증강 생성의 결합 구조를 설명한다.",
                        "대규모 언어 모델", "검색 증강 생성"),
                source("ai-generation", "생성형 AI와 프롬프트 엔지니어링의 기본 원리를 설명한다.",
                        "생성형 AI", "프롬프트 엔지니어링"),
                source("ai-automation", "업무 자동화와 AI 워크플로를 설계하는 방법을 설명한다.",
                        "업무 자동화", "AI 워크플로"),
                source("ai-tasks", "할 일 관리와 캘린더 관리로 업무를 계획한다.",
                        "할 일 관리", "캘린더 관리"),
                source("ai-meetings", "자동 회의 요약과 회의록 정리 기능을 비교한다.",
                        "자동 회의 요약", "회의록 정리"),
                source("ai-knowledge", "개인 지식 관리와 문서 검색 체계를 구축한다.",
                        "개인 지식 관리", "문서 검색"),
                source("ai-tool-use", "AI 도구 호출과 외부 시스템 연동 패턴을 설명한다.",
                        "도구 호출", "외부 시스템 연동"),
                source("ai-multi-agent", "다중 AI 에이전트의 역할 분담과 협업 구조를 정리한다.",
                        "다중 AI 에이전트", "에이전트 협업"),
                source("ai-semantic-search", "텍스트 임베딩과 의미 검색으로 문서를 찾는 방법을 설명한다.",
                        "텍스트 임베딩", "의미 검색"),
                source("ai-evaluation", "AI 응답 평가와 회귀 테스트 데이터셋 운영 방법을 정리한다.",
                        "AI 응답 평가", "회귀 테스트"),
                source("ai-guardrails", "AI 가드레일과 출력 검증으로 응답 안정성을 높인다.",
                        "AI 가드레일", "출력 검증"));
    }

    private static NodeResolutionEvalScenario scenario(
            String id,
            String name,
            NodeResolutionEvalScenario.Domain domain,
            String summary,
            List<String> extractedSubjects,
            List<NodeResolutionEvalScenario.SimilarSourceFixture> corpus,
            NodeResolutionEvalScenario.ExpectedResolution... expected
    ) {
        return new NodeResolutionEvalScenario(
                id, name, domain, summary, extractedSubjects, corpus, List.of(expected)
        );
    }

    private static NodeResolutionEvalScenario.SimilarSourceFixture source(
            String id,
            String summary,
            String... subjects
    ) {
        return new NodeResolutionEvalScenario.SimilarSourceFixture(
                id, summary, List.of(subjects)
        );
    }

    private static NodeResolutionEvalScenario.ExpectedResolution reuse(
            String candidate,
            String canonicalSubject
    ) {
        return new NodeResolutionEvalScenario.ExpectedResolution(
                candidate, NodeResolutionEvalScenario.Action.REUSE, canonicalSubject,
                NodeResolutionEvalScenario.Priority.STANDARD
        );
    }

    private static NodeResolutionEvalScenario.ExpectedResolution criticalReuse(
            String candidate,
            String canonicalSubject
    ) {
        return new NodeResolutionEvalScenario.ExpectedResolution(
                candidate, NodeResolutionEvalScenario.Action.REUSE, canonicalSubject,
                NodeResolutionEvalScenario.Priority.REGRESSION_CRITICAL
        );
    }

    private static NodeResolutionEvalScenario.ExpectedResolution create(
            String candidate,
            String canonicalSubject
    ) {
        return new NodeResolutionEvalScenario.ExpectedResolution(
                candidate, NodeResolutionEvalScenario.Action.CREATE, canonicalSubject,
                NodeResolutionEvalScenario.Priority.STANDARD
        );
    }

    private static NodeResolutionEvalScenario.ExpectedResolution criticalCreate(
            String candidate,
            String canonicalSubject
    ) {
        return new NodeResolutionEvalScenario.ExpectedResolution(
                candidate, NodeResolutionEvalScenario.Action.CREATE, canonicalSubject,
                NodeResolutionEvalScenario.Priority.REGRESSION_CRITICAL
        );
    }
}
