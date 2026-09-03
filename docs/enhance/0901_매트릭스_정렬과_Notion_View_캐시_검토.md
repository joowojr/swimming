# 매트릭스 정렬과 Notion View 캐시 검토

- 분석일: 2026-09-01
- 대상 기능: Matrix 영역별 커서 조회와 향후 드래그앤드롭
- 관련 Feature: `docs/features/M1/F13-task-matrix.md`
- 범위: 정렬 저장 모델, 커서 안정성, 드래그 위치 표현, Notion 공개 View Query 캐시 구조
- 제외: 실제 구현, 드래그앤드롭 라이브러리 선정, Notion 비공개 내부 구현 추정

## 1. 결론

Matrix 정렬은 **이중 연결 리스트로 저장하지 않는다**.

드래그 요청의 `previousTaskId`, `nextTaskId`는 연결 리스트처럼 보이지만, 두 값은 사용자가 드롭한 위치를 서버에 전달하는 일시적인 앵커다. DB에는 각 Task의 `prev_id`, `next_id`를 저장하지 않고 간격을 둔 `matrix_rank`를 저장한다.

```text
클라이언트 요청
previousTaskId=105, nextTaskId=91
              ↓
서버가 두 이웃의 rank 조회
previous.rank=3072, next.rank=2048
              ↓
이동 Task.matrix_rank=2560
```

기본 구현은 다음 조합을 사용한다.

| 관심사 | 결정 |
|---|---|
| 영속 정렬 | 간격을 둔 `BIGINT matrix_rank` |
| 동률 정렬 | `id DESC` |
| 드롭 위치 요청 | `previousTaskId`, `nextTaskId` |
| 페이지네이션 | `(matrix_rank, id)` 기반 불투명 커서 |
| 영역 구분 | `priority`, `urgent` 조합 |
| 캐시 | 초기 버전에는 별도 View 결과 캐시를 두지 않음 |
| 확장 | 동시 편집과 커서 불안정이 실제 문제가 되면 `queryId` 기반 View 스냅샷 검토 |

Notion의 공개 View Query API는 정렬·필터된 결과 집합을 서버에 캐시하고 `query_id`로 후속 페이지를 읽는 구조를 제공한다. 이 구조는 향후 레퍼런스로 삼되, Notion 웹 Board가 동일한 내부 API를 사용한다고 단정하지 않는다.

## 2. 이중 연결 리스트와의 차이

### 2.1 이중 연결 리스트로 저장하는 경우

각 Task가 다음 컬럼을 가진다고 가정할 수 있다.

```text
previous_task_id
next_task_id
```

세 Task의 연결은 다음처럼 저장된다.

```text
A.prev=null, A.next=B
B.prev=A,    B.next=C
C.prev=B,    C.next=null
```

B를 D와 E 사이로 옮기면 B뿐 아니라 기존 이웃 A·C와 새 이웃 D·E까지 여러 행을 일관되게 변경해야 한다.

```text
A.next=C
C.prev=A
D.next=B
B.prev=D
B.next=E
E.prev=B
```

### 2.2 Matrix에 이중 연결 리스트를 사용하지 않는 이유

| 문제 | 영향 |
|---|---|
| 여러 행 갱신 | 카드 하나 이동에 최대 다섯 행을 함께 수정해야 함 |
| 양방향 불변식 | `A.next=B`인데 `B.prev<>A`인 손상 상태가 가능함 |
| 동시 이동 | 겹치는 이웃을 다른 순서로 잠가 데드락과 충돌 가능성이 커짐 |
| 삭제·영역 이동 | 이전·다음 연결을 항상 복구해야 함 |
| SQL 조회 | 연결 순서를 읽기 위해 반복 조회 또는 재귀 CTE가 필요함 |
| 인덱스 페이지네이션 | `ORDER BY` 가능한 단일 정렬키가 없어 keyset pagination과 맞지 않음 |
| 부분 로딩 | 아직 로딩하지 않은 노드를 따라가야 전체 순서를 알 수 있음 |

특히 Matrix는 영역별 무한 스크롤이 필요하다. 관계형 DB에서는 연결 포인터보다 인덱스로 정렬하고 범위 조회할 수 있는 rank가 적합하다.

### 2.3 이웃 ID를 요청에 사용하는 이유

프론트는 카드가 배열의 몇 번째 인덱스인지 알지만, 서버의 전체 영역에는 아직 로딩하지 않은 Task가 존재할 수 있다. 프론트가 `order=7`이나 직접 계산한 rank를 보내면 서버의 실제 위치와 어긋날 수 있다.

이웃 ID는 사용자의 의도를 다음처럼 표현한다.

```text
"105 아래, 91 위에 배치"
```

서버는 두 이웃이 실제로 같은 영역에서 인접하는지 확인하고 새 rank를 계산한다. 즉 **요청 계약만 연결 리스트의 삽입 표현을 빌리고, 저장과 조회는 rank를 사용한다.**

## 3. 희소 rank 정렬

### 3.1 저장 예시

큰 값을 먼저 표시하고 기본 간격은 `1024`로 둔다.

```text
A.matrix_rank = 3072
B.matrix_rank = 2048
C.matrix_rank = 1024
```

A와 B 사이에 D를 넣을 때는 중간값만 저장한다.

```text
A.matrix_rank = 3072
D.matrix_rank = 2560
B.matrix_rank = 2048
C.matrix_rank = 1024
```

대부분의 이동은 D 한 행만 변경한다.

### 3.2 조회와 커서

```sql
SELECT ...
FROM tasks
WHERE user_id = :userId
  AND is_deleted = false
  AND is_priority = :priority
  AND is_urgent = :urgent
  AND (
    matrix_rank < :cursorRank
    OR (matrix_rank = :cursorRank AND id < :cursorTaskId)
  )
ORDER BY matrix_rank DESC, id DESC
LIMIT :sizePlusOne;
```

정렬과 커서가 같은 `(matrix_rank, id)` 튜플을 사용하므로 DB 인덱스 범위 조회가 가능하다.

### 3.3 간격 소진

두 이웃 rank 차이가 `1`이면 정수 중간값을 만들 수 없다. 이때 해당 사용자·영역만 다시 `1024` 간격으로 배치한다.

재배치가 발생하면 기존 rank를 포함한 커서는 유효하지 않다. 배치 변경 응답의 `rebalancedSections`에 해당 영역을 포함하고, 프론트는 그 영역의 첫 페이지를 다시 조회한다.

## 4. 무한 스크롤과 드래그의 충돌 지점

keyset pagination은 정렬 기준이 조회 도중 바뀌지 않을 때 가장 안정적이다. 드래그는 `matrix_rank`를 바꾸므로 다음 상황을 처리해야 한다.

### 4.1 현재 로딩된 범위 안에서 이동

- 프론트 배열을 낙관적으로 이동한다.
- 서버가 반환한 새 위치 커서를 이동 Task에 반영한다.
- 현재 로딩된 마지막 Task가 바뀌면 그 Task의 `positionCursor`를 다음 페이지 커서로 사용한다.

### 4.2 영역 간 이동

- source 영역에서 Task를 제거한다.
- target 영역의 드롭 위치에 삽입한다.
- source와 target의 `priority`, `urgent`, 개수와 마지막 커서를 갱신한다.
- 서버는 분류값과 rank를 한 트랜잭션에서 변경한다.

### 4.3 아직 로딩하지 않은 범위와의 경계

사용자는 화면에 로딩된 카드 사이에만 드롭할 수 있다. 로딩된 마지막 카드 아래로 드롭하는 경우 프론트는 `previousTaskId`만 보내고, 서버가 DB에서 실제 다음 Task를 확인해 위치를 결정한다.

### 4.4 동시 변경

다른 탭이나 기기에서 이웃 관계가 먼저 바뀌면 서버는 임의 위치에 저장하지 않고 `409 MATRIX_PLACEMENT_CONFLICT`를 반환한다. 프론트는 source와 target 영역을 재조회한다.

## 5. Notion에서 공식 확인되는 구조

### 5.1 Board 동작

Notion 공식 도움말은 Board View가 속성으로 항목을 그룹화하고, 카드를 같은 열 안에서 위아래로 옮기거나 다른 열로 이동할 수 있다고 설명한다.

- [Notion Help: Board view](https://www.notion.com/en-gb/help/boards)

공식 문서는 카드 이동 UX를 설명하지만 다음 내부 구현은 공개하지 않는다.

- 실제 정렬 컬럼 또는 rank 알고리즘
- Board 웹 클라이언트가 사용하는 페이지네이션 API
- DOM 가상화 여부
- 자동 스크롤 임계값과 속도
- 낙관적 갱신과 충돌 해결 프로토콜

따라서 Notion의 UI 동작은 제품 레퍼런스로 사용할 수 있지만 비공개 내부 구현을 사실처럼 문서화하지 않는다.

### 5.2 공개 API의 불투명 커서

Notion Data Source Query는 필터와 정렬 조건으로 결과를 조회하며 응답의 `next_cursor`, `has_more`로 다음 페이지를 탐색한다. `start_cursor`는 이전 응답의 값을 그대로 전달하며 클라이언트가 해석하지 않는 불투명 값이다.

- [Notion API: Query a data source](https://developers.notion.com/reference/query-a-data-source)
- [Notion API: Pagination](https://developers.notion.com/reference/intro)

공개 API의 주요 형태는 다음과 같다.

```json
{
  "results": [],
  "next_cursor": "opaque-value",
  "has_more": true
}
```

이 패턴은 Matrix API의 `nextCursor`, `hasNext` 설계에 참고한다.

### 5.3 공개 View Query의 결과 캐시

Notion의 공개 Create View Query API는 View의 필터·정렬 조건으로 결과 집합을 만들고 다음 정보를 반환한다.

- 후속 페이지를 식별하는 `query_id`
- 캐시 만료를 나타내는 `expires_at`
- 첫 페이지와 다음 페이지 커서
- 최대 10,000개의 캐시된 조회 결과
- 약 15분의 캐시 유효기간

- [Notion API: Create a view query](https://developers.notion.com/reference/create-view-query)

개념적인 흐름은 다음과 같다.

```text
View 필터·정렬 실행
        ↓
정렬된 결과 ID 집합 캐시
        ↓
queryId + 첫 페이지 반환
        ↓
같은 queryId에서 다음 페이지 조회
        ↓
만료 후 새 queryId 생성
```

이 방식의 장점은 다음 페이지를 읽는 동안 원본 데이터가 일부 변경돼도 하나의 Query 결과 안에서는 페이지 경계를 안정적으로 유지할 수 있다는 점이다.

주의할 점은 이 구조가 **Notion 공개 API의 계약**이라는 것이다. Notion 웹 Board가 내부적으로 같은 캐시를 사용한다는 근거는 공개되어 있지 않다.

### 5.4 Notion의 성능 방향

Notion은 큰 데이터베이스에서 단순 속성 필터, 필요한 속성만 표시, 큰 무필터 View 축소를 권장한다. 일부 View는 사용자가 한 번에 표시할 페이지 수를 `Load limit`으로 제한할 수 있다.

- [Notion Help: Optimize database performance](https://www.notion.com/en-gb/help/optimize-database-load-times-and-performance)
- [Notion Help: Timeline load limit](https://www.notion.com/help/timelines)

이를 통해 확인할 수 있는 방향은 전체 결과를 한 번에 렌더링하지 않고 View의 필터와 표시량을 제한한다는 점이다. Board의 정확한 로딩 트리거가 자동 무한 스크롤인지 명시적인 추가 로드인지까지는 공식 문서로 확인되지 않는다.

## 6. Matrix에 적용할 캐시 단계

### 6.1 1단계: DB keyset pagination만 사용

현재 권장안이다.

```text
GET /api/tasks/matrix
section + cursor + size
        ↓
복합 인덱스 keyset 조회
        ↓
items + nextCursor + hasNext
```

장점:

- 구조가 단순하다.
- Redis와 캐시 무효화가 필요 없다.
- 네 영역이 독립적으로 다음 페이지를 읽을 수 있다.
- 개인 사용자의 단일 Matrix 규모에 충분하다.

드래그로 순서가 바뀌면 영향받은 영역의 로컬 목록과 커서를 갱신하고, 충돌 또는 rank 재배치가 발생한 영역만 다시 조회한다.

### 6.2 2단계: 짧은 View revision 추가

동시 수정으로 커서 충돌이 실제로 자주 발생하면 응답에 `viewRevision`을 추가할 수 있다.

```json
{
  "section": "URGENT",
  "viewRevision": 42,
  "items": [],
  "nextCursor": "...",
  "hasNext": true
}
```

배치 변경이나 분류 변경 시 사용자 Matrix revision을 증가시킨다. 다음 페이지 요청의 revision이 현재 값과 다르면 `409`를 반환해 해당 영역을 다시 조회하게 한다.

이 단계는 결과 전체를 캐시하지 않으면서 오래된 커서를 빠르게 감지한다.

### 6.3 3단계: Notion식 Query 결과 스냅샷 검토

다음 조건이 실제로 발생할 때만 도입을 검토한다.

- 한 사용자의 Task가 수천 개 이상이고 같은 View를 반복 조회함
- 복잡한 필터·정렬로 DB 조회 비용이 측정상 큼
- 여러 기기 또는 공동 편집으로 페이지 중복·누락이 반복됨
- 동일한 Matrix View 결과를 여러 요청에서 재사용할 가치가 있음

가능한 계약은 다음과 같다.

```http
POST /api/task-matrix/queries
```

```json
{
  "queryId": "01J7...",
  "expiresAt": "2026-09-01T17:15:00+09:00",
  "sections": {
    "URGENT": {
      "items": [],
      "nextCursor": "...",
      "hasNext": true
    }
  }
}
```

후속 페이지:

```http
GET /api/task-matrix/queries/{queryId}/sections/URGENT?cursor={cursor}
```

Redis를 사용한다면 개념적으로 다음 키를 둘 수 있다.

```text
matrix:view:{userId}:{queryId}:PRIORITY_URGENT
matrix:view:{userId}:{queryId}:URGENT
matrix:view:{userId}:{queryId}:PRIORITY
matrix:view:{userId}:{queryId}:STANDARD
```

각 값에는 정렬된 Task ID 또는 페이지 경계를 저장하고 짧은 TTL을 적용한다.

이 구조에는 다음 비용이 따른다.

- Task 생성·삭제·분류·드래그 시 캐시 무효화 정책 필요
- 현재 스냅샷에 쓰기 결과를 어떻게 보일지 결정 필요
- 사용자별 결과 ID 집합의 Redis 메모리 사용
- query 만료와 화면 재조회 처리
- DB와 캐시 사이 정합성 및 장애 시 fallback 정책

따라서 현재 규모에서 먼저 도입하면 keyset pagination보다 복잡성이 크다.

## 7. 드래그와 View 스냅샷의 관계

View 결과 스냅샷은 읽기 페이지 경계를 안정화하지만, 드래그 결과를 자동으로 반영하지 않는다.

```text
queryId 스냅샷: A → B → C
사용자 드래그:  C → A → B
```

드래그 직후 선택지는 다음 세 가지다.

| 방식 | 장점 | 단점 |
|---|---|---|
| 기존 query 폐기 후 새 query 생성 | 가장 단순하고 정확함 | 다시 조회하고 스크롤 위치가 바뀔 수 있음 |
| 클라이언트가 스냅샷 위에 변경 overlay | 즉각적 UX | 로컬 병합과 rollback이 복잡함 |
| 서버 캐시를 쓰기와 함께 수정 | 후속 페이지도 새 순서 반영 | 캐시 동시성과 장애 처리가 복잡함 |

향후 Query 결과 캐시를 도입한다면 두 번째 방식이 사용자 경험에 가장 가깝다.

1. 프론트가 카드를 낙관적으로 이동한다.
2. 서버 DB에 배치를 저장한다.
3. 현재 화면은 로컬 overlay를 유지한다.
4. 다음 View 진입 또는 백그라운드 갱신에서 새 query를 만든다.
5. 실패하면 overlay를 롤백한다.

이는 Notion 웹 내부 구현을 설명하는 것이 아니라, 공개 View Query 캐시를 Matrix에 적용할 경우의 설계안이다.

## 8. 선택지 비교

| 방식 | 단건 이동 비용 | 순서 조회 | 커서 페이지 | 동시성 난이도 | 현재 채택 |
|---|---:|---|---|---|---|
| 배열 전체 `order_idx` 재저장 | O(n) UPDATE | 단순 | 가능 | 보통 | 아니오 |
| DB 이중 연결 리스트 | O(1)처럼 보이나 여러 행 UPDATE | 복잡 | 부적합 | 높음 | 아니오 |
| 희소 `matrix_rank` | 일반적으로 1행 UPDATE | 인덱스 정렬 | 적합 | 보통 | **예** |
| rank + View revision | 일반적으로 1행 UPDATE | 인덱스 정렬 | stale 감지 | 보통 | 필요 시 |
| rank + Query 결과 캐시 | 쓰기 + 캐시 처리 | 캐시 페이지 | 가장 안정적 | 높음 | 장기 검토 |

## 9. 도입 판단 기준

초기 구현에서는 다음 지표를 수집한 뒤 캐시 확장을 판단한다.

- Matrix 첫 페이지와 다음 페이지 DB 응답 시간
- 한 사용자당 Task 수 분포
- 다음 페이지 중복·누락 또는 `409` 발생률
- Matrix 재진입 시 동일 조회 반복 횟수
- rank 재배치 빈도
- 네 영역 병렬 조회의 DB 부하

다음 조건이 없다면 Redis Query 결과 캐시는 도입하지 않는다.

```text
인덱스 keyset 조회가 충분히 빠르고
동시 변경으로 인한 cursor 충돌이 드물며
동일 View 결과의 재사용률이 낮다.
```

## 10. 최종 결정

1. `previousTaskId`, `nextTaskId`는 드롭 위치를 표현하는 요청 앵커다.
2. DB에는 이중 연결 리스트를 저장하지 않는다.
3. Matrix 정렬은 희소 `matrix_rank`와 `id` tie-breaker로 저장한다.
4. 영역별 무한 스크롤은 `(matrix_rank, id)` 불투명 커서를 사용한다.
5. 초기 버전은 DB keyset pagination과 필요한 영역의 선택적 재조회로 구현한다.
6. Notion의 공개 `query_id`·TTL 기반 View 결과 캐시는 장기 확장 레퍼런스로 기록한다.
7. 캐시는 실제 성능 또는 동시성 지표가 필요성을 증명할 때 도입한다.
