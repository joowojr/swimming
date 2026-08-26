# Project Instructions

## Codex Development Scope

### Work Codex May Implement

- Backend, database, API contracts, backend tests, infrastructure, and project documentation may be implemented through the normal implementation workflow.
- When the user explicitly requests frontend implementation, Codex may create, complete, or modify frontend components, pages, hooks, stores, types, API integration, validation, routing, and feature logic within the requested Feature Spec.
- An explicit frontend implementation request follows the normal implementation workflow: confirm the API contract, present the affected files and plan, wait for approval, implement, and verify the relevant behavior.
- For frontend work, Codex may implement visual styling after the user has written the relevant components and behavior.
- Frontend styling work may include design tokens, CSS, responsive layout, typography, spacing, colors, visual states, animations, and presentation-related accessibility improvements.
- Codex may add or adjust class names and presentation-only attributes in an existing user-authored component when required to apply styling.
- Before frontend styling work, Codex must present the target screens, affected files, and design plan and wait for approval.
- Codex must verify that user-authored frontend behavior still works after styling changes.

### Frontend Work Requiring Explicit User Request

- Without an explicit user request for frontend implementation, Codex must not create, complete, or rewrite frontend components, pages, hooks, stores, or frontend Features for the user.
- Without an explicit user request for frontend implementation, Codex must not implement frontend state, data flow, API calls, types, event behavior, validation, routing behavior, or business logic.
- Codex must not change user-authored component boundaries or behavioral markup solely for styling convenience.
- If a visual requirement needs a component structure or behavior change, Codex must explain the requirement and let the user implement that change first.
- When the user requests tutoring or review rather than implementation, Codex must not provide complete, production-ready frontend component code as an answer or disguised through a large example.

### Frontend Tutoring Responsibilities

Frontend component development is a guided learning exercise when the user requests tutoring, review, hints, or step-by-step guidance. In that mode, act as a strict tutor while the user implements components and behavior. An explicit request for Codex to implement the frontend uses the normal implementation workflow instead.

- Let the user make the first implementation attempt, then review it with concrete explanations, focused hints, and the smallest useful next step.
- Ask questions only when they are necessary to uncover an important design reason or resolve an ambiguity. Ask no more than one or two at a time, and prefer actionable feedback when the context is already clear.
- Explain the concepts and trade-offs behind review findings so the user can make and defend the final decision.
- Write to `docs/react_learning.md` only when the user explicitly asks to record specific content, and record only the content the user requested.
- Structure each entry in `docs/react_learning.md` in this order: **상황 → 문제 원인(원인이 있을 때만) → 대안 비교 → 결론**. Do not add an empty 문제 원인 section when the topic is not about a problem or its cause is unknown.

## Stack

### Frontend

* React
* TypeScript
* Vite

### Backend

* JAVA
* Spring Boot
* MySQL
* Redis
* WebSocket

## Development Approach

* 기능은 가능한 한 Vertical Slice 단위로 구현한다.
* 하나의 Feature는 Frontend, API, Backend, DB, Test까지 포함해 완성한다.
* 현재 요청 또는 Feature Spec 범위 밖의 기능은 구현하지 않는다.
* API contract를 먼저 확인하거나 정의한 뒤 Frontend와 Backend를 구현한다.
- Do not preserve backward compatibility. Remove obsolete paths instead of adding compatibility layers, fallbacks, or migrations.
- Choose the simplest implementation that fully meets the current requirements. Avoid speculative abstractions, configuration, and indirection.
- Grow the system in layers. Start from the smallest version that works end to end, and add each new capability on top of a product that already works. Never trade a working product for unfinished complexity.
- Keep components modular and concerns clearly separated.
- Prefer established, well-maintained libraries when they reduce overall complexity or improve reliability. Do not reimplement common functionality without a clear reason.
- Lean on the dependencies already in the project before writing your own implementation or adding packages. Do not assume a library lacks a capability without checking its documentation and types.
- Make architectural decisions for the long term. Do not accept a stopgap that only works for now and is meant to be replaced
- Study how established products solve the problem before designing a solution. Adopt their proven patterns and conventions rather than inventing from an approach from scratch.

## Source Documents

필요한 문서만 읽는다.

* 제품 개요: `docs/prd.md`
* 사용자 흐름: `docs/user-flows.md`
* 기능 명세: `docs/features/`

구현 시 현재 Feature와 직접 관련된 문서만 추가로 읽는다.

## Implementation Workflow

1. 현재 Feature의 요구사항과 Acceptance Criteria를 확인한다.
2. 관련 기존 코드를 먼저 탐색한다.
3. 변경 예정 파일과 구현 계획을 정리한다.
4. 필요한 API contract를 확인한다.
5. Acceptance Criteria별 테스트를 설계한다.
6. 기능을 구현한다.
7. 관련 테스트를 실행한다.
8. 변경 파일과 테스트 결과를 보고한다.

## Constraints
* 요구사항 충돌을 임의로 해석하지 않는다.
* 기존 요구사항 또는 아키텍처와 충돌하면 구현 전에 보고한다.
* 관련 없는 리팩터링이나 파일명 변경을 함께 수행하지 않는다.
* 테스트를 통과시키기 위해 요구사항을 임의로 변경하지 않는다.

## 실행 방법

```bash
# DB (MySQL, Docker)
docker compose up -d

# Backend (포트 8080)
cd backend && ./gradlew bootRun

# Frontend (포트 5173)
cd frontend && npm run dev
```
## 프로젝트 규칙

개발 중 프론트는 Vite 프록시로 `/api`와 `/ws`를 8080으로 넘깁니다.

### UI / 문구 (중요)
- **압박형 표현을 쓰지 않는다.** "지연 위험", "3일 늦음" 같은 경고 문구와 경고색(빨강/앰버) 금지.
- 진척은 결핍("남은 task 8")이 아니라 **축적**("task 12/20 완료", "이번 주 4일 접속")으로 표현한다.
- 목표일은 "목표일"로 담백하게 표기한다. "마음속 목표", "소프트 목표" 같은 표현은 쓰지 않는다.
- 여행 테마를 과하게 입히지 않는다. 생산성 도구의 레이아웃을 유지하되 압박 신호만 걷어낸다.
- 세션 진행 화면은 몰입형: 배경이 풀블리드, 위젯은 코너에 반투명 카드로 배치.

### API
- REST는 `/api` 프리픽스, WebSocket 엔드포인트는 `/ws`.
- STOMP 규칙: `/app/...` = 클라이언트→서버(요청), `/topic/...` = 서버→룸 전체(방송), `/user` = 개인.
- 그룹 타이머는 매초 브로드캐스트하지 않는다. **구간의 시작·끝 시각과 서버 시간만** 보내고, 남은 시간은 클라이언트가 계산한다.
- 타이머 계산은 개인·그룹 모두 "남은 시간 = 정한 길이 - (현재 시각 - 시작 시각)". 그룹만 서버 시간을 기준으로 오프셋을 보정한다.

### API 응답
- 성공 응답은 **`ResponseEntity<DTO>`를 그대로 반환**한다. `ApiResponse` 같은 **자체 래퍼를 만들지 않는다.**
- 상태코드를 의미대로 쓴다: 조회 200, 생성 201(+ Location 헤더), 수정·삭제 후 본문 없으면 204.
- 에러는 **ProblemDetail(RFC 7807)** 로 통일한다. `spring.mvc.problemdetails.enabled=true`.
- 비즈니스 예외는 `BusinessException` + `ErrorCode` enum을 쓰고, **`GlobalExceptionHandler`에서만** 처리한다. 컨트롤러에서 try-catch 하지 않는다.
- 엔티티를 그대로 응답에 노출하지 않는다. 요청·응답 DTO를 따로 둔다.

### 공통 모듈 사용
- 모든 엔티티는 `common/entity/BaseTimeEntity`를 상속한다. 각 엔티티에 `created_at`/`updated_at`을 중복 선언하지 않는다.
- 비즈니스 예외는 `common/exception`의 `BusinessException` + `ErrorCode` enum을 쓴다. 새 에러가 필요하면 `ErrorCode`에 항목을 추가한다.
- `common`에 새 클래스를 추가하려면 **지금 두 곳 이상에서 실제로 쓰이는 경우에만** 한다. 나중에 쓸 것 같은 유틸·추상화를 미리 만들지 않는다.
- 프론트 API 호출은 반드시 `api/client.js`를 통한다. 컴포넌트에서 fetch/axios를 직접 부르지 않는다.
- 색·간격·폰트는 `styles/tokens.css`의 디자인 토큰을 쓴다. 값을 하드코딩하지 않는다.
- **공통 컴포넌트를 미리 만들지 않는다.** 같은 패턴이 3회 이상 반복되면 그때 `components/`로 추출한다.

초기 구성 방법과 예시 코드는 `docs/SETUP.md` 참고(세팅 완료 후에는 읽을 필요 없음).

### 코드
- Spring `@Transactional`은 기본 전파 정책을 사용하더라도 `propagation = Propagation.REQUIRED`를 명시한다.
- 백엔드 패키지는 `com.swimming.backend` 아래 도메인 단위로 나눈다 (auth, user, project, task, plan, session, group, place, stats, common).
- 아키텍처는 **패키지 바이 피처**다. 도메인별로 controller/service/repository/domain/dto를 둔다. **DDD·헥사고날 구조를 임의로 만들지 않는다** (domain/application/infrastructure 4계층 분리 금지).
- 도메인 간 협업 규칙: 다른 도메인은 **Service를 통해서만** 호출하고(남의 Repository 직접 주입 금지), **엔티티가 아니라 DTO를 주고받는다**. 의존 방향은 단방향(user·place → project → task → plan·session → group → stats)으로 유지한다.
- 여러 도메인이 엮이는 후속 처리(예: 세션 종료 후 통계 갱신)는 스프링 이벤트로 분리한다.
- 테스트 메서드명은 한글로 작성하거나, 영문 메서드명에는 테스트 의도를 설명하는 한글 `@DisplayName`을 반드시 추가한다.
- 커밋 전 빌드가 통과하는지 확인한다.
- 새 라이브러리를 추가할 때는 먼저 알린다.

---

## 개발 순서 (수직 슬라이스)

기능을 DB부터 화면까지 세로로 잘라 하나씩 완성한다. 한 번에 한 슬라이스만 진행한다.

1. 프로젝트 세팅 + DB 연결 + 헬스체크
2. 회원가입/로그인 (JWT)
3. 프로젝트 CRUD
4. Task CRUD
5. 데일리 플랜 (오늘 할 일)
6. 개인 세션 시작/종료 + 세션 기록
7. 진척 집계 + 통계 (축적 프레임, streak)
8. 그룹 세션 (WebSocket, 목표 공유, 공용 타이머)

---

## 작업 방식

- 구현 전에 **계획을 먼저** 제시한다. 어떤 파일을 만들고 각각 무슨 역할인지 설명한 뒤, 승인받고 코드를 쓴다.
- 기능 요구사항은 `docs/PRD.md`의 ID로 참조한다 (예: PROJ-1, GS-3).
- 스키마를 바꿔야 하면 `docs/schema.sql`과 `docs/erd.mmd`를 함께 갱신한다.
- 한 슬라이스가 끝나면 커밋한다.

---

## 미결정 사항

- D-day 배지를 남길지, 완전히 뺄지
- 그룹 세션 진행 주체: 앱 자동 진행 vs 호스트 (v1은 자동 유력)
- streak이 끊겼을 때의 처리 톤 (벌하지 않는 방향)
- 90분 세션의 내부 구간 구조 (예: 45분 - 휴식 - 45분)
