# ARCHITECTURE.md

시스템 구조와 설계 결정을 기록한다. 기능 요구사항은 `docs/PRD.md`, 스키마는 `docs/ERD.mmd`를 참고한다.

---

## 0. 용어 규칙

- 제품 UI와 사용자 관점의 설명에서는 프로젝트 도메인의 기능명을 `폴더`로 표기한다.
- 프론트엔드 라우트는 제품 용어에 맞춰 `/folders`, `/folders/:folderId`를 사용한다.
- 백엔드 패키지·클래스와 API 경로·필드에는 `folder` 식별자를 유지한다. 물리 DB에서는 제품 용어에 맞춰 `folders`, `folder_tags`, `folder_id`, `folder_tag_id`를 사용한다.
- 문서에서 구현 요소를 직접 설명할 때는 `ProjectUseCase`, `/api/folders`, `projectId`처럼 실제 코드와 일치하는 이름을 사용한다.
- 폴더가 연결되지 않은 Task는 제품에서 `미분류`, 데이터 계약에서는 `projectId=null`로 표현한다.

---

## 1. 전체 구성

```
┌─────────────────────────────────────────────────────────┐
│  Browser                                                 │
│  React SPA (Vite)                                        │
│   ├─ REST 호출 (/api)                                    │
│   ├─ STOMP over WebSocket (/ws)  ← 그룹 세션만           │
│   └─ 배경 영상·오디오 재생 (CDN)                          │
└───────────────┬─────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────┐
│  Spring Boot                                             │
│   ├─ REST Controller  (/api/**)                          │
│   ├─ STOMP Controller (/app/**) → Broker (/topic/**)     │
│   ├─ UseCase (유스케이스 조율·응답 DTO 매핑)              │
│   ├─ Service (도메인 로직·트랜잭션·집계)                  │
│   └─ Repository (JPA)                                    │
└───────────────┬─────────────────────────────────────────┘
                │
        ┌───────▼────────┐        ┌──────────────────┐
        │   PostgreSQL   │        │  Object Storage  │
        │  (도메인 데이터) │        │  + CDN (배경/음원) │
        └────────────────┘        └──────────────────┘
```

프론트와 백엔드는 별도 프로세스로 실행하며, 개발 중에는 Vite 프록시가 `/api`와 `/ws`를 백엔드로 넘긴다.

---

## 2. 저장소 구조 (모노레포)

```
swimming/
├── AGENTS.md
├── docker-compose.yml        DB 등 로컬 인프라
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── db/migration/          Flyway 버전 마이그레이션
│   └── erd.mmd
├── backend/                  Spring Boot
└── frontend/                 React (Vite)
```

한 저장소에 두되 배포는 분리 가능하도록 폴더를 완전히 독립시킨다.

---

## 3. 백엔드 구조

### 아키텍처 스타일
**패키지 바이 피처(package by feature)** 를 따른다. 최상위는 도메인 단위로 나누고, 각 도메인 안에서 레이어드(Controller → UseCase → Service → Repository)를 유지한다. 즉 레이어드를 도메인별로 잘라놓은 구조다.

DDD나 헥사고날 아키텍처는 도입하지 않는다. 도메인 규칙이 복잡하지 않은 데 비해 클래스 수와 초기 비용이 크기 때문이다. 다만 **협업 규칙은 지금부터 지켜서**, 나중에 특정 도메인이 복잡해지면 그 도메인만 계층을 넓힐 수 있게 한다.

### 계층
```
Controller  →  UseCase  →  Service  →  Repository  →  DB
   (HTTP)       (유스케이스)   (도메인)      (영속성)
```

- Controller: 요청 검증, 인증 주체 확인, `ResponseEntity`·상태 코드·헤더 조립을 담당한다. Properties, 도메인 로직, Repository를 두지 않는다.
- UseCase: 하나의 사용자 행동을 완성한다. 같은 도메인 및 하위 도메인의 Service를 조율하고, Service DTO를 최종 응답 DTO로 매핑하며, 쿠키처럼 응답에 필요한 메타데이터 값을 준비한다. `ResponseEntity`와 Repository를 직접 다루지 않는다.
- Service: 자기 도메인의 엔티티와 Repository를 사용해 도메인 규칙, 트랜잭션 경계, 집계 계산을 담당한다. 다른 도메인이 필요하면 상대 Service만 호출하며, 상대 Service는 DTO 또는 순수 도메인 객체를 반환할 수 있다.
- Repository: JPA 인터페이스. 복잡한 조회는 쿼리 메서드 또는 JPQL로 처리한다.
- DTO: 엔티티를 그대로 노출하지 않는다. 요청·응답 DTO, UseCase 결과 DTO, 도메인 간 전달 DTO를 목적별로 분리한다.

### 계층 의존 규칙

1. Controller는 같은 도메인의 UseCase만 호출한다.
2. UseCase는 Service를 조율하지만 Repository와 엔티티를 직접 참조하지 않는다.
3. Service는 자기 도메인의 Repository만 직접 사용한다.
4. 다른 도메인은 반드시 해당 도메인의 Service를 통해 호출한다. JPA 엔티티를 직접 전달하지 않고 DTO 또는 순수 도메인 객체를 사용한다.
5. `ResponseEntity`, HTTP 상태 코드, 응답 헤더 조립은 Controller에만 둔다.
6. `@ConfigurationProperties`는 Config, UseCase, Service에서 사용하며 Controller에 주입하지 않는다.
7. 단순 위임만 하는 UseCase·Service나 중복 Facade 계층을 추가하지 않는다. 각 계층에는 위 책임에 해당하는 실제 작업이 있어야 한다.

작은 단일 동작도 Controller가 Service를 직접 호출하는 예외를 두지 않는다. 현재 `UserController`와 `HealthController`의 UseCase 도입은 후속 리팩터링 대상으로 남아 있으며, 이 상태는 계층 규칙의 예외가 아니라 알려진 위반이다.

### 쓰기 방식 선택 규칙

수정 방식은 쿼리 횟수만으로 결정하지 않는다. 변경 대상이 단건인지 여러 행인지, 도메인 규칙 계산이 필요한지, 영속성 컨텍스트의 변경 감지가 필요한지를 함께 판단한다.

| 변경 유형 | 기존에 피해야 할 방식 | 사용할 방식 | 선택 이유 |
|---|---|---|---|
| 단건의 여러 필드와 도메인 규칙 | 요청값을 바로 벌크 UPDATE로 반영 | Entity 조회 → 도메인 인스턴스 메서드 → 변경 감지 | 현재 상태 검증과 상태 전이 규칙을 도메인 객체가 책임진다. |
| 단건의 한 필드 | 도메인 전체를 새로 조립해 저장 | 소유 Entity 조회 → 해당 필드만 변경 → 변경 감지 | 수정 범위를 좁히고 다른 필드의 값을 덮어쓰지 않는다. |
| 여러 행에 같은 상태·완료값 적용 | 여러 Entity 조회 → 반복 변경 → `saveAll()` | `@Modifying` JPQL UPDATE | 조회·변경·저장 반복을 줄인다. |
| 여러 행 삭제·연결 해제 | 삭제 대상 Entity를 모두 조회 | 조건을 포함한 `@Modifying` JPQL DELETE/UPDATE | 영향 행 수로 소유권과 처리 결과를 확인할 수 있다. |

#### 단건 필드 수정

- Service는 트랜잭션 안에서 수정 대상의 소유 Entity를 조회한다.
- 조회한 Entity의 변경 메서드를 호출하고, 새 도메인 객체를 조립하거나 불필요한 `save()`를 호출하지 않는다.
- API 목적이 다른 수정은 메서드를 분리한다. 예를 들어 음악 URL, 계획 시간, Task 제목, Task 상태 수정을 하나의 범용 메서드로 합치지 않는다.
- 상태·소유자·값의 범위 검증은 도메인 또는 해당 Service에 두며 UseCase에 복제하지 않는다.
- 같은 트랜잭션에서 즉시 반영 시점이 필요할 때만 `flush()`를 명시한다.

#### 여러 행 상태·완료값 변경

- 동일한 변경을 여러 행에 적용할 때는 Repository의 `@Modifying` JPQL을 사용한다.
- JPQL에는 사용자·부모 ID 등 소유 범위 조건을 포함하고, 반환된 영향 행 수를 요청 대상 수와 비교한다.
- `flushAutomatically = true`로 실행 전에 변경 감지 내용을 반영하고, `clearAutomatically = true`로 실행 후 1차 캐시의 오래된 Entity를 정리한다.
- 벌크 JPQL은 도메인 메서드와 JPA Auditing을 거치지 않으므로 필요한 `updatedAt`은 `CURRENT_TIMESTAMP` 등 쿼리에 명시한다.
- 행마다 서로 다른 값이 필요하면 상태별 쿼리를 나누거나 별도 일괄 처리 쿼리를 설계한다.

#### 도메인 상태 전이와 영속 반영

도메인 상태가 바뀌는 유스케이스는 다음 범용 패턴을 따른다.

1. UseCase가 Service를 통해 소유 도메인 객체를 조회하고 요청값을 정리한다.
2. 상태 전이, 현재 상태 검증, 파생값 계산은 도메인 객체의 인스턴스 메서드가 담당한다.
3. UseCase는 계산이 끝난 도메인 결과를 Service에 전달한다. UseCase가 도메인 규칙을 다시 계산하지 않는다.
4. Service는 같은 Aggregate의 영속 Entity를 조회해 결과를 반영하고, 단건 필드는 변경 감지로 저장한다.
5. 상태 전이에 따라 여러 하위 행을 바꿔야 하면 해당 변경만 별도의 `@Modifying` JPQL로 실행하고 영향 행 수를 검증한다.
6. 다른 도메인의 후속 처리는 Service 간 연쇄 호출 대신 이벤트로 분리한다.

도메인 규칙이 필요한 단건 변경을 요청값 그대로 벌크 UPDATE해서는 안 된다. 반대로 동일한 변경을 여러 행에 적용하는 작업을 불필요하게 Entity 목록 조회와 `saveAll()`로 처리해서도 안 된다.

### 패키지 (도메인 단위)
```
com.swimming.backend
├── auth        인증·JWT
├── health      애플리케이션·DB 상태 확인
├── user        사용자
├── folder     폴더
├── task        task
├── plan        데일리 플랜
├── session     세션·기록
├── group       그룹 룸·참가자·실시간
├── place       도시·장소
├── knowledge   링크 수집
├── note        기본·폴더·세션 컨텍스트 메모와 Task 정리
└── common      공통 설정, 예외, 응답 포맷
```

도메인별로 controller / usecase / service / repository / domain / dto 를 각 패키지 안에 둔다. 현재 기능에서 필요하지 않은 하위 패키지는 미리 만들지 않는다. 기능을 세로로 잘라 개발하는 방식(수직 슬라이스)과 맞춘다.

### 도메인 간 협업 규칙

1. **도메인 간 호출은 UseCase가 상대 도메인의 Service를 불러 조율한다.** 예를 들어 `ProjectUseCase`는 `TaskService.getSummaries()`를 호출해 폴더 상세의 task 목록과 진척을 합친다. Service는 자기 도메인의 Repository만 다루고(`ProjectService`는 `ProjectRepository`·`ProjectTagRepository`만), 남의 Repository를 직접 주입하지 않는다. 소유권 검증 같은 규칙이 그 도메인의 Service 안에만 있어야 한 곳만 고치면 되기 때문이다.
2. **JPA 엔티티를 직접 주고받지 않는다.** 다른 도메인의 영속 엔티티를 그대로 넘기면 영속성 세부 구현과 변경 추적 범위가 도메인 경계를 넘어간다. 상대 Service는 호출 목적에 따라 `ProjectReference` 같은 DTO나 `Session` 같은 순수 도메인 객체를 반환한다.
3. **기반 식별자는 값으로 보유한다.** project는 소유자를 `user` Service로 조회하지 않고 `userId`(Long)만 들고 다닌다. 기반 도메인(user·place)을 향한 불필요한 런타임 의존을 만들지 않기 위해서다.
4. **의존 방향을 한쪽으로 유지한다.** 아래 방향을 따르면 순환 참조가 생기지 않는다.
6. **여러 Service가 엮이는 흐름은 UseCase가 조율한다.** Service끼리 순환 의존하거나 상호 조율 책임을 나눠 갖지 않는다.

### 의존 방향
```
common                              ← 모든 도메인이 참조
health                              ← 독립 인프라 점검
auth → user
folder → task
plan → folder, task
session → user, place, plan, task
note → folder, session, task
group → user, place, session, task
```

화살표는 Service 호출 방향이다. `/folders/{folderId}/tasks`처럼 폴더 소유권 확인과 Task 동작이 함께 필요한 사용자 행동은 `FolderUseCase`가 조율해 `folder → task` 방향을 유지한다. 반대 방향 호출은 두지 않는다.

### 엔티티 연관관계
JPA 엔티티끼리는 패키지가 달라도 연관관계를 맺을 수 있다. 다른 도메인 Entity 타입 참조는 Entity 매핑과 자기 Repository에 저장할 reference 조립에만 허용한다. Entity를 Service 입출력으로 전달하거나 상대 Repository를 직접 호출하는 것은 금지한다. 이 영속성 참조는 위 Service 호출 방향에 포함하지 않는다.

### 도메인 패키지 예시

모든 도메인은 같은 기본 계층 규칙을 따르되 현재 기능에 필요한 패키지만 만든다.

```
auth/
├── controller/      HTTP 요청·ResponseEntity 조립
├── usecase/         로그인·갱신·로그아웃 유스케이스
├── service/         JWT 등 인증 도메인 기능
├── dto/             요청·응답·UseCase 결과 DTO
└── config/          인증 설정 Properties
```

UseCase가 이미 application 역할을 하므로 같은 도메인에 `application`, `facade`, `orchestrator` 계층을 중복 추가하지 않는다. 도메인이 복잡해져 계층 확장이 필요하면 해당 도메인과 정본 문서만 함께 변경한다.

---

## 4. 인증

이메일·비밀번호 인증과 OAuth2/OIDC 인증은 모두 내부 `user_id`로 통합한 뒤 서비스 자체 액세스·리프레시 JWT를 발급한다. 다른 도메인은 로그인 공급자를 알지 않고 `user_id`만 사용한다.

```text
Email / Password ─┐
                  ├─ auth → user_id → Access / Refresh JWT
OAuth2 / OIDC ────┘
```

- `users`는 서비스 사용자와 프로필을 소유한다. 소셜 전용 사용자를 위해 `email`, `password_hash`는 nullable이며 비밀번호가 있으면 BCrypt로 해시한다.
- `user_auth_provider`는 외부 공급자 계정을 소유한다. 외부 사용자는 이메일이 아니라 `(provider, provider_subject)`로 식별한다.
- 소셜 로그인을 인증에만 사용하면 공급자 access token을 저장하지 않는다. 외부 API 호출이 필요해질 때만 토큰을 별도 저장소에 암호화하고 만료 시각·scope와 함께 관리한다.
- REST는 `Authorization: Bearer <token>`을 필터에서 검증해 SecurityContext에 주체를 설정한다.
- WebSocket은 STOMP CONNECT의 Authorization 헤더를 ChannelInterceptor에서 한 번 검증하고 세션에 주체를 유지한다.

---

## 5. 실시간 (그룹 세션)

### 프로토콜
WebSocket 위에 STOMP를 얹는다. 여러 참가자가 같은 룸을 구독하고, 서버가 룸 단위로 브로드캐스트하는 구조에 적합하기 때문이다.

### 채널
| 목적 | 방향 | 경로 |
|---|---|---|
| 입장·목표 전송 | 클라이언트 → 서버 | `/app/rooms/{roomId}/join`, `/goal`, `/leave` |
| 참가자 상태 | 서버 → 룸 전체 | `/topic/rooms/{roomId}/presence` |
| 공용 타이머 | 서버 → 룸 전체 | `/topic/rooms/{roomId}/timer` |
| 공유 목표 | 서버 → 룸 전체 | `/topic/rooms/{roomId}/goals` |

### 타이머 설계 (핵심)
매초 카운트다운을 브로드캐스트하지 않는다. 서버는 **현재 구간의 시작·종료 시각과 서버 현재 시각**만 내려주고, 남은 시간은 각 클라이언트가 계산한다.

```
남은 시간 = phaseEndAt - (클라이언트 현재시각 + 서버-클라이언트 오프셋)
```

- 메시지는 상태가 바뀔 때만 발행한다 (시작, 구간 전환, 일시정지, 종료).
- 개인 세션도 같은 계산식을 쓰되, 서버 기준 보정이 필요 없다.
- 이 방식은 네트워크 부하가 낮고, 탭이 백그라운드에 있다가 돌아와도 자동으로 값이 맞춰진다.

### 룸 상태 보관
룸의 타이머 상태(구간, 시작·종료 시각)는 서버 메모리에 두고, 영속이 필요한 정보(참가자, 목표, 세션 기록)는 DB에 저장한다. 서버를 여러 대로 확장하면 룸 상태 공유와 브로드캐스트를 위해 Redis pub/sub를 도입한다.

---

## 6. 데이터 계층

DB는 PostgreSQL이며, 실제 DDL의 정본은 `backend/src/main/resources/db/migration/*.sql`, 관계도는 `docs/erd.mmd`를 따른다.
최초 스키마는 `V1__initial_schema.sql`이며, 이후 스키마 변경은 기존 파일을 수정하지 않고 `V2__...`, `V3__...`처럼 새 Flyway 버전 마이그레이션으로 추가한다. 로컬과 운영 모두 Hibernate는 `validate`만 수행하고, 스키마 생성과 변경은 Flyway가 담당한다.
Flyway 이력이 없는 기존 로컬 DB만 local 프로파일의 `baseline-on-migrate`로 V1 기준선을 기록한다. 운영에서는 자동 baseline을 허용하지 않으며 빈 DB에 V1부터 적용한다.

### 스키마 요지
```
users ─┬─ user_auth_provider
       ├─ folder_tags ── folders ── tasks ──┬── sessions ── session_tasks
       │                                      └── daily_plan_items
       ├─ daily_plan_items
       ├─ group_participants ─┐
       └─ sessions            │
cities ── places ─────────────┴─ group_rooms
```

### 설계 원칙
- 모든 테이블은 bigint auto increment PK와 `created_at`, `updated_at`을 가진다.
- 외부 로그인 계정은 `user_auth_provider`에 저장하고 서비스 데이터는 항상 내부 `users.id`를 참조한다.
- 시간 길이는 초 단위 정수로 저장하고, 표시할 때만 분으로 변환한다 (`_sec` 접미사).
- 공간은 `cities`(도시 그룹, 국가·타임존 보유) 1:N `places`(배경 영상·기본 음악을 가진 실제 공간). 세션과 그룹 룸은 `place_id`를 참조한다.
- `sessions`는 개인·그룹 공통 테이블이며 `type`으로 구분한다. 개인 세션은 `group_room_id`가 null, 즉석 목표 참여는 `task_id`가 null일 수 있다.
- 한 줄 기록은 `sessions.summary`에 저장한다. SessionTask는 `is_completed=false`로 생성하고 종료 요청에서 완료한 Task만 `true`로 변경하며, 현재 `tasks.status`에는 완료 여부를 반영한다.
- 실제 진행 시간이 계획 시간보다 짧으면 `INTERRUPTED`, 계획 시간을 채우면 `COMPLETED`다. 두 상태 모두 종료된 세션으로서 실제 시간·Task 결과·한 줄 기록을 보존한다.
- 그룹 룸의 `duration_sec`는 룸의 설정값이고, 세션의 `planned_duration_sec`는 시작 시점에 복사된 기록이다. 룸 설정이 바뀌어도 과거 기록은 보존된다.
- 스키마가 유동적인 사용자 설정(위젯 배치 등)이 필요해지면 JSON 컬럼 하나로 처리한다. 검색·집계·관계가 필요한 데이터는 반드시 정규 컬럼으로 둔다.

### 무결성·인덱스
| 대상 | 내용 |
|---|---|
| user_auth_provider | (provider, provider_subject) unique, user_id index |
| daily_plan_items | (user_id, plan_date, task_id) unique |
| group_participants | (group_room_id, user_id) unique |
| sessions | (user_id, started_at), (user_id, group_room_id, started_at), (group_room_id, started_at) |

---

## 7. 집계 전략

측정은 세션마다 기록하고, 집계는 세 층위로 합산한다. 별도 집계 테이블 없이 `sessions`와 `session_tasks`를 조회 시 집계한다.

| 층위 | 산출물 | 원천 |
|---|---|---|
| task | 누적 세션 수, 완료 상태 | session_tasks, tasks.status |
| folder | 완료 task 비율 | tasks.status |
| 사용자·일자 | 하루 세션 수·몰입 시간, 주간 통계, streak | sessions.started_at 기준 그룹핑 |

세션 이력 조회는 `(user_id, started_at)` 인덱스를 타므로 초기 규모에서는 실시간 집계로 충분하다. 데이터가 쌓여 통계 조회가 무거워지면 일별 집계 테이블이나 캐시 계층을 추가한다. 이때 원천 데이터(`sessions`, `session_tasks`)는 그대로 두고 파생 테이블만 얹는다.

집계 결과는 축적 프레임으로 노출한다. 남은 양이 아니라 해온 양을 보여주며, 지연 경고나 경고색을 쓰지 않는다.

---

## 8. 프론트엔드 구조

```
frontend/src/
├── pages/        라우트 단위 화면 (홈, 폴더 상세, 세션, 그룹)
├── features/     도메인별 로직 (folder, task, session, group)
├── components/   공용 UI
├── api/          REST 클라이언트
├── ws/           STOMP 연결·구독 관리
├── store/        전역 상태 (Zustand)
└── styles/       디자인 토큰
```

### 상태 관리
- 서버 데이터: REST로 조회하고 필요한 만큼 스토어에 보관한다.
- 세션 진행 상태(남은 시간, 위젯 표시 여부): 로컬 상태. 타이머는 시작 시각 기반으로 렌더 시 계산한다.
- 그룹 룸 상태(참가자, 목표, 공용 타이머): WebSocket 구독으로 갱신되는 스토어에 보관한다.

### 세션 화면
배경(영상)을 풀블리드로 깔고, 위젯(타이머·task·음악·참가자)을 반투명 카드로 코너에 배치한다. 위젯은 개별로 토글 가능하며, 모두 접으면 배경만 남는다.

---

## 9. 정적 자산

배경 영상·이미지는 비공개 S3 버킷에 두고 CloudFront로 서빙한다. 버킷은 Block Public Access를 유지하며 OAC(Origin Access Control)로 CloudFront에만 오리진 접근을 허용한다.

DB에는 오브젝트 키만 저장하고(`places.background_asset_key`), 응답을 만들 때 `app.place.background.cdn-base-url`과 조합해 CDN URL로 내려준다. URL에 만료가 없어 긴 세션 중에도 끊기지 않고, 사용자마다 같은 주소라 브라우저와 CDN 캐시가 그대로 적중한다. 백엔드는 읽기 경로에서 AWS 자격증명을 쓰지 않는다.

---

## 10. 비기능 요건

| 영역 | 방침 |
|---|---|
| 보안 | HTTPS, JWT, BCrypt, 입력 검증, CORS 화이트리스트 |
| 성능 | 그룹 타이머 동기화 오차 1초 이내, 통계는 사전 집계로 경량화 |
| 확장성 | WebSocket 다중 인스턴스 대비 Redis pub/sub 도입 여지 |
| 시간 처리 | 서버는 UTC 저장, 표시는 사용자 타임존 기준 변환 |
| 접근성 | 키보드 내비게이션, 스크린리더 라벨, 다크 모드 |
| 관측 | Controller에 도달한 API 요청의 method·path·마스킹된 query·status·처리 시간·사용자 ID를 AOP로 기록하고, Request Body·인증 헤더·쿠키는 기록하지 않는다. 에러 추적과 세션 관련 주요 이벤트를 기록한다. |

---

## 11. 배포

- 백엔드: Jar 빌드 후 컨테이너로 배포
- 프론트: 정적 빌드 산출물을 CDN 또는 정적 호스팅으로 배포
- DB: 관리형 PostgreSQL 또는 컨테이너
- 로컬 개발: `docker compose`로 DB를 띄우고 백엔드·프론트는 각각 실행

---

## 12. 주요 설계 결정 (요약)

| 결정 | 이유 |
|---|---|
| 모노레포, 프로세스는 분리 | 초기 관리 단순, 배포는 독립 가능 |
| 패키지 바이 피처 + UseCase 계층 | Controller의 HTTP 책임, UseCase의 사용자 행동 조율, Service의 도메인 책임을 분리하면서 수직 슬라이스 단위를 유지 |
| 협업 규칙은 선도입 (UseCase 조율·Service 경유·JPA 엔티티 직접 전달 금지·단방향 의존) | 도메인 경계를 지키고 기능 확장 시 변경 범위를 해당 수직 슬라이스 안에 유지 |
| STOMP over WebSocket | 룸 단위 구독·브로드캐스트에 적합 |
| 타이머는 시각 기반 계산 | 네트워크 부하 최소, 백그라운드 탭 복귀 시 자동 보정 |
| 세션 단일 테이블(type 구분) | 개인·그룹 기록을 같은 스키마로 집계 |
| 일별 집계 테이블 분리 | 통계·streak 조회 경량화 |
| 시간은 초 단위 저장 | 반올림 오차 누적 방지 |
| city / place 분리 | 도시 안의 여러 공간을 배경 단위로 선택 |

---

## 13. 유예한 것 (v1 이후)

- Redis 도입 (WebSocket 수평 확장, 집계 캐싱)
- 도시 실시간 상태 동기화
- 소셜 로그인
- 모바일 네이티브 앱
- 팀·조직 기능
