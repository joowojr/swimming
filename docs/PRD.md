## 1. 제품 개요

### 목적

혼자 일하는 사람도 집중력 있고 효율적으로, 그리고 웰빙하게 목표를 이뤄가도록 돕는 워크스페이스. 성취 압박이 아니라 자율적 폴더 관리와 사람들 사이의 동기부여를 지향한다.

### 용어

- 사용자에게 노출하는 기능명과 화면 문구에서는 `폴더`를 사용한다.
- `폴더`는 코드와 API에서 `folder`로 식별되는 기존 프로젝트 도메인의 제품 용어다. 새로운 별도 도메인을 뜻하지 않는다.
- 사용자에게 노출되는 프론트엔드 화면 경로는 `/folders`, `/folders/{id}`를 사용한다.
- API 경로, 요청·응답 필드, 요구사항 ID처럼 구현 계약을 그대로 가리킬 때는 `/folders`, `projectId`, `PROJ-*` 등의 기술 식별자를 유지한다.
- `미분류`는 폴더가 연결되지 않아 API에서 `projectId=null`인 Task를 뜻한다.

### 목표 (Goals)

- 폴더와 task를 몰입 세션으로 실행하는 루프를 완성한다.
- 혼자(개인 세션)와 함께(그룹 세션) 두 가지 몰입 모드를 제공한다.
- 진척을 압박이 아니라 축적으로 보여준다.

### 비목표 (Non-goals, v1 제외)

- Notion 수준의 범용 폴더 관리(하위 task, 의존성, 협업 편집, 간트).
- 실시간 현지 상태(날씨·시간) 도시 동기화.
- 팀/조직용 관리자 기능.

---

## 2. 타깃 사용자

혼자 일하며 목표를 이루려는 사람. 취업 준비생, 디지털 노마드, 재택근무자를 포함한다. 공통 상황은 "곁에 아무도 없는 환경에서 스스로 집중과 동기를 만들어야 하는" 것이다.

핵심 감정 흐름: 동경 → 접속 → 몰입 → 연결.

---

## 3. v1 범위

### 포함 (In)

계정/인증, 폴더/task 관리, 데일리 플랜, 개인 세션, 그룹 세션(정해진 시간·고정 타이머·목표 공유·세션 기록), 세션 기록/측정, 진척/통계/streak, 도시 선택, 개인 음악.

### 제외 (Out)

실시간 도시 동기화, 범용 PM 확장, 팀 기능, 모바일 네이티브 앱(웹 우선), 화상/음성 채팅.

---

## 4. 기능 요구사항

### 4.1 계정 / 인증

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| AUTH-1 | Google 계정으로 로그인한다. | P0 |
| AUTH-2 | JWT 기반 인증, 액세스/리프레시 토큰을 발급한다. | P0 |
| AUTH-3 | 사용자 프로필(닉네임, 타임존)을 관리한다. | P0 |
| AUTH-4 | 소셜 로그인(Google 등). | P2 |

### 4.2 폴더 관리

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| PROJ-1 | 폴더를 생성·조회·수정·보관(archive)한다. | P0 |
| PROJ-2 | 폴더는 이름, 설명, 목표일(선택), 상태를 가진다. | P0 |
| PROJ-3 | 목표일은 옅게 표시하며 지연을 붉게 경고하지 않는다. | P0 |
| PROJ-4 | 폴더별 진척(완료 task 비율, 누적 세션·시간)을 조회한다. | P0 |
| PROJ-5 | 폴더에 대표 도시를 연결한다. | P1 |
| PROJ-6 | 사용자 정의 태그를 생성·조회·수정·삭제하고 폴더에 최대 하나 연결한다. 태그 삭제 시 폴더의 연결은 자동 해제한다. | P0 |

### 4.3 Task 관리

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| TASK-1 | 폴더 하위에 task를 생성·수정·삭제한다. | P0 |
| TASK-2 | task는 상태(대기/하는 중/끝냄/보류)를 가진다. | P0 |
| TASK-3 | task별 누적 세션 수를 표시한다. | P0 |
| TASK-4 | task 순서 정렬(수동). | P1 |

### 4.4 데일리 플랜

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| PLAN-1 | 날짜별 계획에 폴더 연결 여부와 관계없이 Task를 담는다. | P0 |
| PLAN-2 | 세션은 데일리 플랜에 담긴 Task 안에서 폴더 연결 여부와 관계없이 선택한다. | P0 |
| PLAN-3 | 오늘 담은 예상 시간이 권장치를 넘으면 부드럽게 안내한다(경고색 없음). | P1 |

### 4.5 개인 세션

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| PS-1 | 언제든 즉시(on-demand) 시작한다. | P0 |
| PS-2 | 타이머를 사용자가 선택한다(25/45/60/커스텀). | P0 |
| PS-3 | 도시·음악을 자유롭게 설정한다. | P0 |
| PS-4 | 오늘 task에서 목표를 선택한다(비공개). | P0 |
| PS-5 | 진행 화면은 몰입형(배경 풀블리드 + 코너 위젯)으로 집중 모드(알림 차단)를 제공한다. | P0 |
| PS-6 | "이어서 하기"로 마지막 설정과 다음 task를 자동 선택해 1탭 진입한다. | P1 |

### 4.6 그룹 세션

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| GS-1 | 정해진 시간에 시작하는 세션 목록을 조회·참여한다. | P0 |
| GS-2 | 타이머와 도시는 룸 단위로 고정된다. 음악만 개인 설정한다. | P0 |
| GS-3 | 시작 시 각자 폴더-task 목표를 공유한다(공개/비공개 선택). | P0 |
| GS-4 | 진행 중 참가자 존재감(인원·아바타)을 표시한다. | P0 |
| GS-5 | 공용 타이머와 참가자 상태를 실시간 동기화한다. | P0 |
| GS-6 | 폴더/task가 없는 참가자를 위한 즉석 task 입력 fallback을 제공한다. | P0 |
| GS-7 | 90분 등 긴 세션의 스프린트/휴식 내부 구조를 지원한다. | P1 |
| GS-8 | 상시 오픈 룸(정해진 시간 외 상시 참여). | P2 |

### 4.7 세션 기록 / 측정

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| REC-1 | 세션 종료 시 "무엇을 끝냈나"를 기록한다. | P0 |
| REC-2 | 세션에서 진행한 각 task의 완료 여부를 기록한다. | P0 |
| REC-3 | 세션 수와 Task 완료 상태를 측정해 폴더 진척에 반영한다. | P0 |
| REC-4 | 개인·그룹 세션은 동일한 측정 스키마를 쓴다. | P0 |
| REC-5 | 중도 종료 세션도 부분 기록으로 반영한다. | P0 |
| REC-6 | 그룹 세션은 세션 기록 결과를 참가자와 공유한다. | P1 |

### 4.8 진척 / 통계 / streak

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| STAT-1 | "이번 주 접속 일수·몰입 시간·머문 도시"를 축적 프레임으로 보여준다. | P0 |
| STAT-2 | 지연 경고·경고색을 쓰지 않는다(웰빙 원칙). | P0 |
| STAT-3 | 연속 접속(streak)을 격려 장치로 제공한다. | P1 |
| STAT-4 | streak이 끊겨도 벌하지 않는 톤으로 처리한다. | P1 |

### 4.9 도시 / 공간

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| CITY-1 | 도시 목록을 제공하고 세션 배경으로 선택한다. | P0 |
| CITY-2 | 초기에는 소수(3~5개) 도시로 시작해 밀도를 확보한다. | P0 |
| CITY-3 | 실시간 현지 상태 동기화는 제외한다. | P0 |

### 4.10 음악 / 사운드

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| MUSIC-1 | 기본 사운드/음악을 제공하고 개인이 설정한다. | P1 |
| MUSIC-2 | 외부 음원(YouTube 등) 연동. | P2 |

### 4.11 메모

| ID | 요구사항 | 우선순위 |
| --- | --- | --- |
| NOTE-1 | 사용자는 떠오른 내용을 기본·폴더·세션 컨텍스트의 메모로 저장하고 조회한다. | P0 |
| NOTE-2 | 사용자는 자신의 활성 메모 내용만 수정하거나 보관·삭제할 수 있다. | P0 |
| NOTE-3 | 폴더·세션 메모는 사용자가 소유한 대상에만 연결한다. | P0 |
| NOTE-4 | 보관한 메모는 기본 조회 결과에서 제외하되 기록으로 유지한다. | P0 |
| NOTE-5 | 삭제한 메모는 soft delete로 기록하되 활성·보관 조회와 수정 대상에서 제외한다. | P0 |

---

## 5. 핵심 사용자 플로우

### 개인 경로

홈(폴더 관리 탭, 기본) → 폴더 선택 → 오늘 task 선택 → 세션 구성(타이머·도시·음악) → 세션 진행 → 세션 기록(완료 여부·한 줄 메모) → 진척 반영.

### 그룹 경로

홈 → 그룹 세션 탭 → 정해진 세션 참여 → 대기실(도시·타이머 고정 확인, 목표 선택·공개 여부) → 목표 공유 → 세션 진행(공용 타이머·참가자 존재감) → 세션 기록(공유) → 진척 반영.

두 경로는 "세션 시작"으로 수렴하며, 홈 기본 진입은 폴더 관리 탭이다.

---

## 6. 데이터 모델

주요 엔티티와 핵심 필드.

| 엔티티 | 핵심 필드 | 관계 |
| --- | --- | --- |
| User | id, email, passwordHash, nickname, timezone, createdAt | 1-N Project, 1-N Session |
| ProjectTag | id, userId, name | N-1 User, 1-N Project |
| Project | id, userId, tagId(nullable), name, description, targetDate(nullable), status, cityId(nullable) | N-1 User, N-1 ProjectTag, 1-N Task |
| Task | id, userId, projectId(nullable), title, status(todo/doing/done/hold), orderIdx | N-1 User, 선택적으로 N-1 Project, 1-N Session |
| DailyPlan | id, userId, planDate | 1-N DailyPlanItem |
| DailyPlanItem | id, dailyPlanId, taskId, orderIdx | N-1 Task |
| Session | id, userId, type(personal/group), taskId, cityId, plannedDuration, actualDuration, startAt, endAt, groupRoomId(nullable), status, summary | 1-N SessionTask |
| SessionTask | sessionId, taskId, isCompleted(default false), createdAt, updatedAt | N-1 Session, N-1 Task |
| GroupRoom | id, cityId, startAt, durationMin, capacity, hostType(auto/host), status | 1-N GroupParticipant |
| GroupParticipant | id, roomId, userId, taskId(nullable), adHocGoal(nullable), goalVisibility(public/private), joinedAt | N-1 GroupRoom |
| City | id, name, backgroundAssetUrl, defaultMusicRef | 1-N Session |
| Note | id, userId, content, status, isDeleted, contextType, projectId(nullable), sessionId(nullable), createdAt, updatedAt | N-1 User, 선택적으로 N-1 Project 또는 N-1 Session |

측정은 Session(actualDuration)과 SessionTask(isCompleted)에서 파생한다.

- SessionTask 생성 시 `isCompleted=false`로 저장하고, 세션 종료 시 완료한 Task만 `true`로 변경한다. 현재 `Task.status`에는 완료 여부를 반영한다.
- 실제 진행 시간이 계획 시간보다 짧으면 부분 기록인 `INTERRUPTED`, 계획 시간을 채우면 `COMPLETED`로 기록한다. 두 상태 모두 세션 이력과 Task 결과를 보존한다.

---

## 7. API 설계 (REST)

베이스 경로 `/api`. 인증은 Authorization 헤더의 Bearer 토큰.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | /auth/google | Google 로그인(토큰 발급) |
| POST | /auth/refresh | 토큰 갱신 |
|  |  |  |
| GET | /folders | 내 폴더 목록 |
| POST | /folders | 폴더 생성 |
| GET | /folders/{id} | 폴더 상세(폴더 정보·Task 진척·Task 목록) |
| PATCH | /folders/{id} | 폴더 수정 |
| GET | /folder-tags | 내 폴더 태그 목록 |
| POST | /folder-tags | 폴더 태그 생성 |
| PATCH | /folder-tags/{id} | 폴더 태그 이름 수정 |
| DELETE | /folder-tags/{id} | 폴더 연결 해제 후 태그 삭제 |
| GET | /tasks?mode=all\|unclassified | 내 전체 Task 또는 폴더 없는 Task 목록 |
| POST | /tasks | 폴더·계획 연결을 선택할 수 있는 Task 생성 |
| GET | /folders/{id}/tasks | task 목록 |
| PUT | /folders/{id}/tasks/order | task 순서 저장 |
|  |  |  |
| PATCH | /tasks/{id} | task 수정(제목·상태) |
| DELETE | /tasks/{id} | task 삭제 |
|  |  |  |
| GET | /daily-plans?from_date=&to_date= | 날짜 범위의 계획 조회 |
| POST | /daily-plans/{date}/items | 기존 `taskIds`를 연결하거나 선택적 `projectId`와 제목으로 Task 생성 |
| PUT | /daily-plans/{date} | 계획 항목 순서 저장 |
| DELETE | /daily-plans/{date}/items/{itemId} | 계획 항목 제거 |
|  |  |  |
| POST | /sessions | 개인 세션 시작 |
| GET | /sessions/{id} | 개인 세션 상세 조회 |
| GET | /sessions/active | 현재 진행 중인 세션 조회 |
| PUT | /sessions/{id}/music-url | 세션의 마지막 YouTube URL 저장 |
| POST | /sessions/{id}/end | 세션 종료(한 줄 메모·Task 완료 여부 기록 포함) |
| GET | /sessions?from=&to= | 세션 이력 |
|  |  |  |
| GET | /group-sessions | 예정 그룹 세션 목록 |
| POST | /group-sessions/{roomId}/join | 그룹 세션 참여 |
| POST | /group-sessions/{roomId}/goal | 목표 설정·공개 여부 |
| POST | /group-sessions/{roomId}/leave | 나가기 |
|  |  |  |
| GET | /stats/summary | 이번 주 접속·시간·도시 요약 |
| GET | /stats/streak | 연속 접속 |
| GET | /cities | 도시 목록 |
|  |  |  |
| POST | /notes | 메모 생성(생성된 ID 반환) |
| GET | /notes | 내 메모 목록 조회(상태·컨텍스트 필터 지원, 기본 활성) |
| GET | /notes/{id} | 내 활성 메모 상세 조회 |
| PATCH | /notes/{id} | 내 활성 메모 내용 수정 |
| PATCH | /notes/{id}/archive | 내 활성 메모 보관 |
| PATCH | /notes/{id}/restore | 내 보관 메모 복원 |
| DELETE | /notes/{id} | 내 메모 soft delete |

### 폴더 상세 조회

`GET /api/folders/{id}`는 폴더 상세 화면의 중앙 영역에 필요한 데이터만 반환한다. 현재 구현된 Project와 Task 도메인만 사용하며, Task 상태로 계산할 수 있는 진척과 정렬된 Task 목록을 폴더 기본 정보와 함께 제공한다.

#### 성공 응답

- 상태 코드: `200 OK`
- `targetDate`는 `YYYY-MM-DD` 형식이며 설정하지 않은 경우 `null`이다.
- `progress.completionPct`는 `DONE` 상태인 Task 수를 전체 Task 수로 나눈 정수 백분율이다. Task가 없으면 `0`이다.
- `tasks`는 `orderIdx` 오름차순, 동일한 순서에서는 `id` 오름차순으로 정렬한다.
- Task 상태는 `TODO`, `DOING`, `DONE`, `HOLD` 중 하나다.

```json
{
  "id": 10,
  "name": "제안서 폴더",
  "description": "고객사 제안서를 완성합니다.",
  "targetDate": "2026-09-01",
  "status": "IN_PROGRESS",
  "tag": {
    "id": 3,
    "name": "제안서"
  },
  "progress": {
    "totalTaskCount": 4,
    "completedTaskCount": 1,
    "completionPct": 25
  },
  "tasks": [
    {
      "id": 21,
      "title": "자기소개서 다듬기",
      "status": "DOING",
      "orderIdx": 0
    },
    {
      "id": 22,
      "title": "포트폴리오 정리",
      "status": "TODO",
      "orderIdx": 1
    }
  ]
}
```

#### 오류 응답

- 인증되지 않은 요청은 `401 Unauthorized`를 반환한다.
- 폴더가 없거나 다른 사용자의 폴더인 경우 `404 Not Found`와 `FOLDER_NOT_FOUND` ProblemDetail을 반환한다.

#### 제외 범위

- 누적 세션 수와 몰입 시간
- Task별 세션 수와 몰입 시간
- 이번 주 활동, 머문 장소, 최근 세션 등 우측 사이드바 데이터
- D-day, 지연 여부, 남은 기간처럼 압박 신호가 될 수 있는 파생값

### WebSocket (STOMP) - 그룹 세션 실시간

| 채널 | 방향 | 용도 |
| --- | --- | --- |
| /topic/rooms/{roomId}/presence | 구독 | 참가자 입장·퇴장·인원 |
| /topic/rooms/{roomId}/timer | 구독 | 공용 타이머 구간 전환 |
| /topic/rooms/{roomId}/goals | 구독 | 공유된 목표 목록 |
| /app/rooms/{roomId}/join | 발행 | 참여 알림 |
| /app/rooms/{roomId}/goal | 발행 | 목표 공유 |
| /app/rooms/{roomId}/leave | 발행 | 나가기 알림 |

#### 공용 타이머 (/topic/rooms/{roomId}/timer)

- 매초 브로드캐스트하지 않는다. **현재 구간의 시작·종료 시각과 서버 현재 시각만** 내려주고, 남은 시간은 각 클라이언트가 계산한다.
- 메시지는 상태가 바뀔 때만 발행한다(시작, 구간 전환, 일시정지, 종료).
- 클라이언트는 접속 시 서버 타임스탬프로 시간차(오프셋)를 계산해 항상 서버 시간 기준으로 렌더링한다.
- 구간 종료 시각은 참가자 세션의 종료 시각 기준이 된다. 각자의 종료 요청이 도착한 시각을 쓰면 같은 룸의 `actual_duration_sec`가 네트워크 지연만큼 흩어진다.

#### 나가기 (/app/rooms/{roomId}/leave)

- 사용자가 명시적으로 나갈 때 발행한다. 룸에는 퇴장 presence가 방송되고, 해당 참가자의 세션은 종료 처리된다.
- 브라우저 강제 종료·네트워크 단절은 이 채널로 잡히지 않는다. STOMP 연결 종료 이벤트를 함께 처리해야 `IN_PROGRESS`로 남는 세션이 생기지 않는다. `sessions.active_user_id`가 UNIQUE이므로, 회수되지 않으면 그 사용자는 다음 세션을 시작할 수 없다.

---

## 8. 시스템 아키텍처

### 구성

- Frontend: React SPA. REST 호출, 그룹 세션은 STOMP over WebSocket 구독, 타이머 로직, 오디오 재생.
- Backend: Spring Boot. REST API + WebSocket(STOMP). 인증, 도메인 로직, 측정 집계.
- DB: 관계형 DB(PostgreSQL). JPA/Hibernate로 매핑.
- 실시간 상태: 그룹 세션 타이머·존재감 동기화. 단일 인스턴스는 인메모리, 수평 확장 시 Redis pub/sub로 브로드캐스트.
- 정적 자산: 도시 배경 영상/이미지는 오브젝트 스토리지(S3 등) + CDN.

### 흐름 요약

React가 REST로 폴더·task·세션 CRUD를 처리하고, 그룹 세션 진행 중에는 WebSocket으로 타이머·참가자를 실시간 동기화한다. 세션 종료 시 세션 기록이 REST로 저장된다.

---

## 9. 비기능 요구사항

| 영역 | 요구사항 |
| --- | --- |
| 성능 | 세션 시작·전환 지연 최소화, 그룹 타이머 동기화 오차 1초 이내 목표 |
| 보안 | HTTPS, JWT 인증, 비밀번호 BCrypt 해시, 입력 검증 |
| 확장성 | WebSocket 수평 확장 대비 Redis pub/sub 설계 여지 |
| 접근성 | 키보드 내비게이션, 스크린리더 라벨, 다크 모드 |
| 웰빙 | 지연 경고·압박 신호 배제 원칙을 UI 요구사항으로 강제 |
| 국제화 | 타임존 처리(그룹 세션 시간 표기), 다국어 여지 |

---

## 10. 기술 스택 상세

### Frontend (React)

- 빌드: Vite
- 라우팅: React Router
- 상태관리: 경량 스토어(예: Zustand) 또는 Redux Toolkit
- 통신: REST(fetch/axios), 실시간(STOMP.js + SockJS)
- 스타일: 디자인 토큰 기반, 다크 모드 지원

### 세션 화면 컴포넌트 구현 기술

세션 진행 화면(몰입형)의 각 위젯에 사용할 프론트엔드 기술.

| 컴포넌트 | 추천 라이브러리/기술 | 비고 |
| --- | --- | --- |
| 포모도로 타이머 (개인) | 시작 시간 기반 계산: 남은 시간 = 정한 길이 - (현재 시간 - 시작 시간) | 별도 타이머 라이브러리 불필요. 내 기기 시계만으로 계산. 종료 알림음은 Howler.js/Web Audio, 데스크톱 알림은 Notification API |
| 포모도로 타이머 (그룹) | 개인과 동일한 시작 시간 기반 계산 (기준만 서버 시간) | 계산식은 개인과 같음. 차이는 시작 시간을 서버가 정해 모두에게 통일하고, 기기 간 시계 오차 보정을 위해 서버 시간 기준으로 계산한다는 점. 상태 전달은 WebSocket(STOMP) |
| 팝업 투두 리스트 (플로팅) | react-rnd 또는 react-draggable + @dnd-kit/sortable | 위젯 이동은 react-rnd(드래그·리사이즈), task 재정렬은 @dnd-kit/sortable(접근성·유지보수 우수). 데이터는 REST + Zustand |
| 유튜브 뮤직 플레이어 | react-player 또는 react-youtube | react-player는 다중 소스(향후 SoundCloud 등) 확장에 유리, react-youtube는 YouTube IFrame Player API 직접 제어. 재생/일시정지/스킵/볼륨 프로그래매틱 제어 |
| 도시 배경 영상 (풀블리드) | 네이티브 HTML5 video | loop/muted/playsInline, CDN의 mp4/webm, 프리로드. 별도 라이브러리 불필요 |
| 앰비언스 사운드 | Howler.js | 크로스브라우저 오디오, 루프·페이드·다중 트랙 |
| 집중 모드 | Notification API + 인앱 알림 억제 | OS 알림 차단은 웹의 한계가 있어 인앱 억제 + 안내로 처리 |

유의사항

- YouTube: IFrame Player API 이용약관상 플레이어 가시성 요건과 브라우저 자동재생 정책(음소거 자동재생 또는 사용자 제스처 필요)을 지켜야 한다.
- 그룹 타이머 동기화: 남은 시간 계산식은 개인과 같다. 다만 (1) 시작 시간을 서버가 하나로 정해 모두에게 알리고, (2) 기기마다 시계가 조금씩 다르므로 접속 시 서버 타임스탬프로 시간차(오프셋)를 계산해 항상 서버 시간 기준으로 렌더링한다.
- 화면 갱신: "현재 시간 - 시작 시간"으로 매번 다시 계산하므로, 탭이 백그라운드에 있다가 돌아와도 자동으로 올바른 값으로 맞춰진다(Web Worker 등은 선택 사항).
- 일시정지·휴식 구간: 90분을 "45분 - 휴식 - 45분"처럼 나누거나 일시정지를 허용하면, 현재 구간(phase)의 시작·끝 시간을 기준으로 같은 방식으로 계산한다.

### Backend (Spring Boot 3.x)

- Spring Web (REST)
- Spring Security + JWT
- Spring WebSocket (STOMP)
- Spring Data JPA + Hibernate
- Validation (Bean Validation)
- DB: PostgreSQL
- (선택) Redis: 실시간 브로드캐스트·세션 캐시

### 인프라

- 컨테이너 기반 배포, 오브젝트 스토리지 + CDN(도시 자산)

---

## 11. 마일스톤

| 단계 | 범위 |
| --- | --- |
| M1 | 인증 + 폴더/task CRUD + 데일리 플랜 |
| M2 | 개인 세션(몰입 화면·타이머) + 세션 기록/측정 + 진척 반영 |
| M3 | 그룹 세션(예정 목록·참여·목표 공유·WebSocket 타이머·존재감) |
| M4 | 통계/streak(축적 프레임) + 도시 3~5개 + 톤/웰빙 마감 |

핵심 차별점(폴더-세션 커플링, 완료 상태 측정, 축적 프레임)이 M2에서 먼저 증명되도록 배치한다.

---

## 12. 성공 지표 (KPI 후보)

- 재방문율(주간 활성 사용자), 세션 완료율
- 폴더당 평균 세션·완료 task 수
- 그룹 세션 참여율·유지율(빈 방 비율)
- 정성: "압박 없이 집중된다"는 사용자 피드백

---

## 13. 리스크 & 미결정 사항

| 항목 | 내용 |
| --- | --- |
| 콜드스타트 | 그룹 세션은 유동성 의존. 초기 소수 고밀도 시간대로 시작 |
| 시차 | 그룹 세션 시간 표기·매칭. 사용자 타임존 기준 노출 |
| streak 죄책감 | 격려 장치가 압박이 되지 않게 실패 처리 톤 설계 |
| D-day 표기 | 목표일을 배지로 남길지, 완전히 뺄지 미결정 |
| 그룹 진행 주체 | 자동 진행 vs 호스트. v1은 앱 자동 진행 유력 |
| 실시간 동기화 확장 | WebSocket 다중 인스턴스 시 Redis 도입 시점 |
