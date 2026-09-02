# F13. Task 매트릭스를 탐색하고 배치한다

- Depends on: F04, F11
- Related: TASK-1, TASK-2, TASK-4

## Goal

사용자가 자신이 소유한 Task를 `즉시`와 `중요` 조합에 따라 네 영역에서 확인하고, Task가 많아져도 영역별 무한 스크롤로 탐색할 수 있게 한다. 이후에는 같은 조회·정렬 모델 위에서 카드를 영역 안팎으로 드래그해 순서와 분류를 함께 변경할 수 있게 한다.

## 배경

현재 매트릭스는 `GET /api/tasks?mode=all`로 사용자의 Task 전체를 조회한 다음 프론트에서 `priority`, `urgent` 값을 네 번 필터링한다. 이 방식은 모든 Task를 한 번에 전송하고 렌더링하므로 Task 수가 늘어날수록 DB 조회, 네트워크 응답과 DOM 크기가 함께 증가한다.

드래그앤드롭을 도입하면 Matrix의 표시 순서는 더 이상 생성 ID나 생성 시각 순서가 아니다. 따라서 ID만 사용하는 커서와 기존 `order_idx`를 그대로 사용할 수 없다.

- `order_idx`는 폴더 내부 또는 미분류 Task 목록의 순서다.
- Matrix에는 여러 폴더의 Task와 미분류 Task가 함께 표시된다.
- Matrix 순서를 `order_idx`에 저장하면 폴더 상세의 정렬과 충돌한다.
- 수동 정렬 이후 `ORDER BY id DESC`와 `id < cursorId`를 사용하면 화면 순서와 페이지 경계가 달라진다.

Matrix는 Task 생성·제목·상태 API를 재사용하되, 조회와 배치 변경에는 별도의 API 계약과 정렬값을 사용한다.

## 용어와 영역

| `section` | `priority` | `urgent` | 화면 표시 |
|---|---:|---:|---|
| `PRIORITY_URGENT` | `true` | `true` | 즉시 · 중요 |
| `URGENT` | `false` | `true` | 즉시 |
| `PRIORITY` | `true` | `false` | 중요 |
| `STANDARD` | `false` | `false` | 일반 |

`section`은 Matrix API의 입력 표현이다. Task에는 기존처럼 `priority`, `urgent` Boolean 값을 저장한다.

## User Flow

### 조회

핀보드 진입 → 매트릭스 선택 → 네 영역의 첫 페이지 병렬 조회 → 영역별 Task 확인 → 영역 하단 접근 → 해당 영역의 다음 페이지 조회

### 드래그앤드롭

Task 드래그 시작 → 같은 영역 또는 다른 영역에 드롭 → 화면을 낙관적으로 갱신 → 배치 변경 요청 → 성공 시 서버 위치 반영 또는 실패 시 원래 위치로 복구

## 범위와 단계

### 1단계: 영역별 무한 스크롤

- 네 Matrix 영역을 각각 독립적으로 커서 페이지 조회한다.
- 한 영역의 로딩이나 오류가 다른 영역을 가리지 않는다.
- 각 영역은 자체 `items`, `nextCursor`, `hasNext`, `status`를 가진다.
- 마지막 카드에 도달하기 전에 다음 페이지를 미리 요청한다.
- 같은 Task가 한 영역에 중복 append되지 않도록 Task ID로 중복을 제거한다.

### 2단계: 드래그앤드롭

- 같은 영역 안에서 Task의 Matrix 순서를 변경한다.
- 다른 영역으로 이동하면 `priority`, `urgent`, Matrix 순서를 한 트랜잭션에서 변경한다.
- 프론트가 배열 인덱스나 정렬 숫자를 계산해 보내지 않고, 드롭 위치의 이전·다음 Task ID를 보낸다.
- 드래그 실패 시 낙관적으로 이동한 카드를 원래 위치로 복구한다.
- 카드 내부의 제목 편집, 상태 선택과 메뉴 동작을 보호하기 위해 전용 드래그 핸들을 사용한다.
- 키보드 사용자를 위한 이동 수단을 함께 제공한다.

## API 원칙

- Task 생성은 기존 `POST /api/tasks`를 사용한다.
- 제목과 상태 변경은 기존 Task 수정 API를 사용한다.
- 전체·미분류 Task 목록은 기존 `GET /api/tasks`를 사용한다.
- Matrix 영역 조회만 `GET /api/tasks/matrix`로 분리한다.
- Task 순서를 바꾸는 동작은 `PATCH /api/tasks/{taskId}/placement`를 사용하고 Matrix는 `scope=MATRIX`로 구분한다.
- 조회 커서는 서버가 발급한 불투명 문자열이다. 클라이언트는 커서 내용을 해석하거나 생성하지 않는다.
- 성공 응답은 DTO를 직접 반환하고 오류는 RFC 7807 `ProblemDetail`로 반환한다.

## API 계약

### Matrix 영역 조회

```http
GET /api/tasks/matrix?section=PRIORITY_URGENT&size=20&cursor={cursor}
Authorization: Bearer {accessToken}
```

#### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 제약 | 설명 |
|---|---|---:|---:|---|---|
| `section` | enum | 예 | - | 정의된 네 값 중 하나 | 조회할 Matrix 영역 |
| `size` | integer | 아니요 | `20` | `1..50` | 한 페이지의 최대 Task 수 |
| `cursor` | string | 아니요 | - | 서버가 직전 응답에서 발급한 값 | 다음 페이지 시작 위치 |

첫 페이지는 `cursor` 없이 요청한다. 다음 페이지는 직전 응답의 `nextCursor`를 그대로 전달한다.

#### `200 OK`

```json
{
  "section": "PRIORITY_URGENT",
  "items": [
    {
      "id": 812,
      "projectId": 3,
      "title": "API 명세 작성",
      "status": "TODO",
      "priority": true,
      "urgent": true,
      "positionCursor": "b3JkZXI9NDA5NiZpZD04MTI",
      "createdAt": "2026-09-01T15:30:00",
      "updatedAt": "2026-09-01T15:30:00"
    }
  ],
  "nextCursor": "b3JkZXI9NDA5NiZpZD04MTI",
  "hasNext": true
}
```

#### 응답 필드

| 이름 | 타입 | 설명 |
|---|---|---|
| `section` | enum | 요청한 Matrix 영역 |
| `items` | array | Matrix 정렬순으로 조회된 Task |
| `items[].positionCursor` | string | 해당 Task 위치를 나타내는 서버 발급 커서 |
| `nextCursor` | string 또는 `null` | 다음 페이지 요청에 사용할 커서. 다음 페이지가 없으면 `null` |
| `hasNext` | boolean | 다음 페이지 존재 여부 |

`positionCursor`는 드래그 후 현재 로드된 목록의 마지막 Task가 달라졌을 때 다음 조회 커서를 다시 설정하는 데 사용한다. 클라이언트는 값을 해석하지 않는다.

서버는 `size + 1`개를 조회하여 `hasNext`를 계산하고, 응답에는 최대 `size`개만 포함한다. 전체 개수를 구하기 위한 `COUNT(*)`는 기본 조회에서 실행하지 않는다.

#### 오류

| 상태 | `code` | 조건 |
|---:|---|---|
| `400` | `INVALID_MATRIX_SECTION` | 지원하지 않는 `section` |
| `400` | `INVALID_PAGE_SIZE` | `size`가 허용 범위 밖임 |
| `400` | `INVALID_MATRIX_CURSOR` | 커서를 해석할 수 없거나 요청 영역과 맞지 않음 |
| `401` | 기존 인증 오류 코드 | 인증되지 않은 요청 |

잘못된 커서를 첫 페이지 요청으로 조용히 대체하지 않는다.

### Matrix 배치 변경

```http
PATCH /api/tasks/{taskId}/placement
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### Request Body

```json
{
  "scope": "MATRIX",
  "targetSection": "URGENT",
  "previousTaskId": 105,
  "nextTaskId": 91
}
```

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `scope` | enum | 예 | 정렬 범위. 현재는 `MATRIX`만 지원 |
| `targetSection` | enum | 예 | 드롭한 Matrix 영역 |
| `previousTaskId` | long 또는 `null` | 예 | 드롭 위치 바로 위의 Task ID |
| `nextTaskId` | long 또는 `null` | 예 | 드롭 위치 바로 아래의 Task ID |

위치 표현 규칙은 다음과 같다.

| 위치 | `previousTaskId` | `nextTaskId` |
|---|---|---|
| 영역의 첫 번째 | `null` | 기존 첫 Task ID |
| 두 Task 사이 | 위 Task ID | 아래 Task ID |
| 영역의 마지막 | 기존 마지막 Task ID | `null` |
| 비어 있는 영역 | `null` | `null` |

서버는 이전·다음 Task가 요청 사용자의 소유인지, 삭제되지 않았는지, `targetSection`에 속하는지, 실제로 인접하는지 검증한다. 이동 대상 Task 자신을 이웃 ID로 전달할 수 없다.

다른 영역으로 이동할 때 서버는 `targetSection`을 다음 값으로 변환하고 Matrix 순서와 함께 원자적으로 저장한다.

| `targetSection` | 저장할 `priority` | 저장할 `urgent` |
|---|---:|---:|
| `PRIORITY_URGENT` | `true` | `true` |
| `URGENT` | `false` | `true` |
| `PRIORITY` | `true` | `false` |
| `STANDARD` | `false` | `false` |

#### `200 OK`

```json
{
  "scope": "MATRIX",
  "task": {
    "id": 812,
    "projectId": 3,
    "title": "API 명세 작성",
    "status": "TODO",
    "priority": false,
    "urgent": true,
    "positionCursor": "b3JkZXI9MTUzNiZpZD04MTI",
    "createdAt": "2026-09-01T15:30:00",
    "updatedAt": "2026-09-01T16:10:00"
  },
  "sourceSection": "PRIORITY_URGENT",
  "targetSection": "URGENT",
  "rebalancedSections": []
}
```

`rebalancedSections`에는 정렬 간격 부족으로 순서값을 재배치한 영역을 반환한다. 배열에 포함된 영역은 기존 `positionCursor`가 더 이상 유효하지 않으므로 프론트가 해당 영역의 첫 페이지를 다시 조회한다.

#### 오류

| 상태 | `code` | 조건 |
|---:|---|---|
| `400` | `INVALID_MATRIX_SECTION` | 지원하지 않는 `targetSection` |
| `400` | `INVALID_TASK_PLACEMENT` | 지원하지 않는 scope이거나 이웃 ID 조합이 유효하지 않음 |
| `401` | 기존 인증 오류 코드 | 인증되지 않은 요청 |
| `404` | `TASK_NOT_FOUND` | Task가 없거나 요청 사용자 소유가 아님 |
| `409` | `TASK_PLACEMENT_CONFLICT` | 이웃 Task가 더 이상 인접하지 않는 등 요청 이후 배치가 변경됨 |

다른 사용자의 Task 존재 여부를 노출하지 않기 위해 소유권이 없는 Task도 `404 TASK_NOT_FOUND`로 처리한다.

## 커서와 정렬 규칙

### 저장값

`tasks`에 Matrix 전용 순서값을 추가한다.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `matrix_rank` | `BIGINT` | `NOT NULL` | Matrix 영역 안의 수동 정렬값 |

`matrix_rank`가 큰 Task를 먼저 표시한다. 같은 값이 존재해도 `id DESC`를 보조 정렬로 사용해 순서를 결정적으로 유지한다.

```sql
ORDER BY matrix_rank DESC, id DESC
```

초기 데이터는 기존 최신순과 가깝게 유지하도록 ID를 기준으로 간격을 두어 채운다. 새 Task는 현재 `priority`, `urgent`가 가리키는 영역의 첫 번째 위치에 배치한다.

### 간격 기반 순서값

기본 간격은 `1024`다.

```text
Task A: 3072
Task B: 2048
Task C: 1024
```

A와 B 사이로 이동하면 두 값의 중간값을 사용한다.

```text
Task A: 3072
이동 Task: 2560
Task B: 2048
```

- 첫 번째로 이동: 현재 최댓값 `+ 1024`
- 마지막으로 이동: 현재 최솟값 `- 1024`
- 두 Task 사이: 두 값의 중간값
- 중간값을 만들 수 없음: 해당 영역을 `1024` 간격으로 재배치

영역 재배치는 여러 Task를 수정하므로 사용자 ID, 영역 조건을 포함한 벌크 쿼리로 처리하고 필요한 `updatedAt` 정책을 명시한다. 배치 변경 전체는 하나의 트랜잭션에서 수행한다.

### 커서 구성

커서는 최소한 다음 값을 포함하고 서버만 인코딩·디코딩한다.

```text
section
matrixRank
taskId
```

조회 조건은 정렬 조건과 동일한 튜플을 사용한다.

```sql
WHERE user_id = :userId
  AND is_deleted = false
  AND is_priority = :priority
  AND is_urgent = :urgent
  AND (
    matrix_rank < :cursorRank
    OR (matrix_rank = :cursorRank AND id < :cursorTaskId)
  )
ORDER BY matrix_rank DESC, id DESC
LIMIT :sizePlusOne
```

커서에 담긴 `section`이 요청의 `section`과 다르면 `INVALID_MATRIX_CURSOR`로 처리한다.

## 인덱스

영역 필터와 커서 조회를 위해 다음 복합 인덱스를 사용한다.

```sql
CREATE INDEX idx_tasks_matrix_page
ON tasks (
  user_id,
  is_deleted,
  is_priority,
  is_urgent,
  matrix_rank,
  id
);
```

기존 `(user_id, is_deleted, created_at)` 인덱스는 전체·미분류 Task 최신순 조회에서 계속 사용한다.

nullable 필터를 하나의 쿼리로 합치는 다음 패턴은 Matrix 조회에서 사용하지 않는다.

```sql
(:priority IS NULL OR is_priority = :priority)
```

Matrix 조회는 항상 `section`을 필수로 받아 두 Boolean 조건을 확정한 쿼리를 실행한다.

## 기존 Task 변경과 Matrix 순서

- `POST /api/tasks`로 생성한 Task에도 서버가 해당 영역의 `matrix_rank`를 할당한다.
- 기존 priority·urgent 변경 API로 영역이 달라지면 변경 후 영역의 첫 번째 위치에 새 `matrix_rank`를 할당한다.
- Matrix 드래그는 두 Boolean과 `matrix_rank`를 `placement` API 한 번으로 변경한다.
- priority와 urgent를 두 API로 연속 호출하여 영역 간 드래그를 표현하지 않는다.
- 제목과 상태 변경은 영역과 순서를 변경하지 않는다.
- Task 삭제 후 남은 Task의 `matrix_rank`는 즉시 다시 번호를 매기지 않는다.

## 프론트 상태와 렌더링

각 영역은 다음 상태를 독립적으로 관리한다.

```ts
interface MatrixSectionState {
  items: MatrixTaskResponse[]
  nextCursor: string | null
  hasNext: boolean
  status: 'idle' | 'loading' | 'ready' | 'error'
}
```

### 최초 조회

- 네 영역의 첫 페이지를 병렬 요청한다.
- `Promise.allSettled`에 해당하는 방식으로 영역별 성공·실패를 분리한다.
- 한 영역이 실패해도 성공한 세 영역은 렌더링한다.

### 다음 페이지

- 각 영역 하단에 `IntersectionObserver` sentinel을 둔다.
- 데스크톱에서 영역이 독립 스크롤 컨테이너라면 해당 컨테이너를 observer의 `root`로 사용한다.
- `rootMargin`으로 하단 도달 전에 다음 페이지를 요청한다.
- `hasNext && status !== 'loading'`일 때만 요청한다.
- 응답 Task는 ID 기준으로 중복을 제거한 뒤 기존 배열 뒤에 추가한다.
- `hasNext=false`가 되면 observer를 해제한다.

### 개수 표시

기본 조회에서는 전체 개수를 세지 않는다.

- 다음 페이지가 있으면 `20+개`처럼 현재까지 불러온 수 뒤에 `+`를 표시한다.
- 마지막 페이지까지 조회하면 `37개`처럼 확정된 수를 표시한다.
- 정확한 전체 개수가 제품 요구사항이 되면 네 영역을 한 번에 `GROUP BY is_priority, is_urgent`로 집계하는 별도 계약을 추가한다.

### 드래그 상태

- pointer drop 직후 source와 target 배열을 낙관적으로 갱신한다.
- 요청 중에는 같은 Task의 중복 배치를 막는다.
- 성공 응답의 `task.positionCursor`를 이동한 카드에 반영한다.
- 각 영역의 마지막 카드가 달라지면 그 카드의 `positionCursor`를 `nextCursor`로 사용한다.
- `rebalancedSections`에 포함된 영역은 첫 페이지부터 다시 조회한다.
- 실패하면 source와 target 배열, 커서와 개수를 요청 전 스냅샷으로 복구한다.

## 접근성 및 상호작용

- 카드 전체가 아닌 전용 드래그 핸들에만 drag listener를 연결한다.
- 제목 편집, 상태 선택, 메뉴 버튼 클릭이 드래그를 시작하지 않아야 한다.
- 핸들에는 Task와 현재 위치를 설명하는 접근 가능한 이름을 제공한다.
- 키보드 센서 또는 동등한 키보드 이동 기능을 제공한다.
- 드롭 완료 후 이동한 영역과 위치를 스크린 리더가 알 수 있도록 안내한다.
- 드래그 중인 카드가 스크롤 영역에 잘리지 않도록 overlay를 사용한다.
- 영역별 로딩 sentinel은 중복으로 읽히지 않게 하나의 상태 문구만 제공한다.

## 동시성과 정합성

- 배치 변경은 인증 사용자 소유 Task와 이웃 Task만 대상으로 한다.
- 이동 대상, 이전 Task와 다음 Task의 현재 영역·인접 관계를 트랜잭션 안에서 검증한다.
- 요청 시점 이후 이웃 관계가 바뀌었으면 임의 위치에 저장하지 않고 `409 TASK_PLACEMENT_CONFLICT`를 반환한다.
- 프론트는 충돌 시 관련 source·target 영역을 재조회하고 사용자가 다시 이동할 수 있게 한다.
- 페이지 조회 중 새 Task가 추가되거나 기존 Task가 이동되어도 한 응답 안의 정렬은 `(matrix_rank, id)`로 결정적이어야 한다.

## 데이터 변경

구현 시 다음 문서를 함께 변경한다.

- `backend/src/main/resources/schema.sql`: `matrix_rank`와 `idx_tasks_matrix_page` 추가
- `docs/erd.mmd`: Task의 `matrix_rank` 추가
- 배포 DB용 migration SQL: 기존 Task의 `matrix_rank` backfill과 인덱스 생성

## 테스트 시나리오

### Backend

- 영역별 Boolean 조합에 맞는 Task만 조회한다.
- 첫 페이지와 다음 페이지 사이에 중복과 누락이 없다.
- 같은 `matrix_rank`를 가진 Task는 ID로 결정적 정렬된다.
- `size + 1` 조회로 `hasNext`와 `nextCursor`를 계산한다.
- 다른 영역의 커서를 전달하면 `400`을 반환한다.
- 다른 사용자의 Task와 커서를 통해 데이터가 노출되지 않는다.
- 같은 영역 이동은 Boolean 값을 유지하고 순서만 변경한다.
- 다른 영역 이동은 Boolean과 순서를 한 트랜잭션에서 변경한다.
- 유효하지 않거나 더 이상 인접하지 않은 이웃은 `409`를 반환한다.
- 정렬 간격이 없을 때 영역 재배치 후 순서가 유지된다.

### Frontend

- Matrix 진입 시 네 영역을 독립적으로 조회한다.
- 한 영역 조회 실패가 다른 영역 렌더링을 막지 않는다.
- sentinel이 여러 번 관찰되어도 같은 커서 요청을 중복 실행하지 않는다.
- 다음 페이지를 기존 목록 뒤에 중복 없이 추가한다.
- `hasNext=false` 이후 추가 요청을 보내지 않는다.
- 드래그 성공 시 source와 target 영역 및 개수가 갱신된다.
- 드래그 실패 시 카드와 커서가 이전 상태로 복구된다.
- 제목 입력, 상태 선택과 메뉴 조작이 드래그와 충돌하지 않는다.
- 키보드만으로 Task 위치를 변경할 수 있다.

## Acceptance Criteria

### 1단계

- [ ] Matrix는 전체 Task 목록을 한 번에 조회하지 않는다.
- [ ] 네 영역은 각각 첫 페이지를 조회하고 독립적인 로딩·오류 상태를 표시한다.
- [ ] 영역별 조회는 서버 발급 커서와 `size`를 사용한다.
- [ ] 다음 페이지 조회 시 중복 또는 누락 없이 기존 목록 뒤에 추가된다.
- [ ] 다른 사용자의 Task는 어떤 영역과 커서로도 조회되지 않는다.
- [ ] Matrix 커서 조회가 복합 인덱스를 사용한다.
- [ ] 좁은 화면에서도 각 영역의 로딩·빈 상태·오류 상태를 구분할 수 있다.

### 2단계

- [ ] 같은 영역 안에서 드래그한 순서가 다시 조회해도 유지된다.
- [ ] 다른 영역으로 드래그하면 중요·즉시 값과 순서가 함께 저장된다.
- [ ] 배치 변경은 이동 대상과 이웃 Task의 소유권 및 위치를 검증한다.
- [ ] 낙관적 변경 실패 시 원래 위치로 복구된다.
- [ ] 정렬값 재배치가 발생하면 해당 영역 커서를 무효화하고 다시 조회한다.
- [ ] 카드 내부 입력과 버튼을 정상적으로 사용할 수 있다.
- [ ] 키보드 사용자가 동등하게 순서를 변경할 수 있다.

## Out of Scope

- 여러 Task 동시 드래그
- Matrix 영역 사용자 정의
- 사용자별 Matrix 영역 이름 변경
- 다른 사용자와 Matrix 실시간 공동 편집
- Matrix 전체 정확한 개수 집계 API
- 폴더 상세의 `order_idx`와 Matrix 순서 통합
