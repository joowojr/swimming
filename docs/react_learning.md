# React 학습 기록

## 작성 원칙

각 학습 기록은 다음 순서로 작성한다.

1. 상황
2. 문제 원인 — 원인이 확인되었거나 설명할 필요가 있을 때만 작성한다.
3. 대안 비교
4. 결론

문제가 아닌 설계 선택을 기록하거나 원인을 확인할 수 없는 경우에는 `문제 원인` 항목을 억지로 만들지 않는다.

## 08-20 `react-router-dom` 추가 이유

### 상황

폴더 목록 화면에 이어 `GET /api/folders/{projectId}`를 사용하는 폴더 상세 화면을 구현하려고 했다. 사용자가 목록에서 폴더를 선택하면 해당 폴더의 상세 화면으로 이동하고, 상세 컴포넌트는 선택된 `projectId`로 API를 호출해야 한다.

현재 프론트엔드에는 라우팅 라이브러리가 없으므로 다음 두 가지를 결정할 필요가 있었다.

- 폴더 상세를 고유한 URL을 가진 독립 페이지로 취급할지
- 화면 전환을 위해 `react-router-dom` 의존성을 추가할지

### 대안 비교

| 방식 | URL 예시 | 새로고침 유지 | 직접 진입·공유 | 뒤로 가기 | 구현 복잡도 | 적합한 경우 |
| --- | --- | --- | --- | --- | --- | --- |
| `react-router-dom` | `/folders/10` | 가능 | 가능 | 기본 지원 | 낮음 | 목록과 상세가 독립적인 페이지일 때 |
| React 상태 | URL 변화 없음 | 불가능 | 불가능 | 별도 구현 필요 | 가장 낮음 | 일시적인 패널이나 모달을 열 때 |
| History API 직접 구현 | `/folders/10` | 추가 처리 시 가능 | 추가 처리 시 가능 | 직접 동기화 | 높음 | 라우터를 쓸 수 없는 특별한 제약이 있을 때 |
| 쿼리 파라미터 + 직접 처리 | `/folders?projectId=10` | 가능 | 가능 | 직접 동기화 | 중간 | 같은 화면의 필터나 선택 상태를 URL에 보존할 때 |

#### 1. `react-router-dom`

`react-router-dom`은 DOM 요소를 추가하기 위한 라이브러리가 아니라, React 웹 애플리케이션의 URL과 화면을 연결하는 라우팅 라이브러리다. 경로와 컴포넌트의 관계를 선언하고, 경로 파라미터를 통해 조회 대상을 결정한다.

```tsx
<Routes>
  <Route path="/folders" element={<ProjectDashboard />} />
  <Route path="/folders/:projectId" element={<ProjectDetail />} />
</Routes>
```

상세 컴포넌트는 `useParams()`로 `projectId`를 읽고 `GET /api/folders/{projectId}`를 호출할 수 있다. 목록의 폴더 카드는 `<Link>`나 `useNavigate()`로 상세 경로를 연다. URL, 화면, API 조회 대상이 같은 식별자를 기준으로 연결되므로 책임이 명확하다.

장점은 다음과 같다.

- 경로 매칭, 경로 파라미터, 중첩 경로, 존재하지 않는 경로 처리를 라이브러리가 담당한다.
- 브라우저의 뒤로 가기와 앞으로 가기가 라우팅 기록과 자연스럽게 동기화된다.
- 페이지를 새로고침하거나 상세 URL로 직접 접속해도 `projectId`를 복원할 수 있다.
- 화면 수가 늘어나도 경로 구조를 중심으로 확장하기 쉽다.

단점은 새 패키지와 라우터 설정이 필요하다는 점이다. 화면 전환이 하나뿐이고 URL이 필요하지 않다면 도입 비용이 이점보다 클 수 있다.

#### 2. React 상태로 선택한 폴더 관리

상위 컴포넌트가 선택된 폴더 ID를 상태로 보관하고, 값에 따라 목록 또는 상세 컴포넌트를 렌더링하는 방식이다.

```tsx
const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);

return selectedProjectId === null
  ? <ProjectDashboard onSelect={setSelectedProjectId} />
  : <ProjectDetail projectId={selectedProjectId} onBack={() => setSelectedProjectId(null)} />;
```

별도 라이브러리가 필요 없고 구현이 가장 단순하다. 선택 상태가 상위 컴포넌트에 명시적으로 드러나므로 목록 안에서 잠시 상세 패널을 여는 인터페이스에는 적합하다.

하지만 상태가 메모리에만 존재한다. 새로고침하면 초기값으로 돌아가며, 사용자는 특정 폴더 상세 화면의 주소를 복사할 수 없다. 브라우저의 뒤로 가기를 눌렀을 때 목록으로 돌아가게 하려면 URL 또는 history와 상태를 별도로 연결해야 한다. 이 연결을 추가하기 시작하면 단순하다는 장점이 줄어든다.

#### 3. 브라우저 History API 직접 사용

`window.history.pushState()`로 URL을 바꾸고 `popstate` 이벤트를 구독해 뒤로 가기와 앞으로 가기를 처리하는 방식이다.

```tsx
window.history.pushState({}, "", `/folders/${projectId}`);

useEffect(() => {
  const handlePopState = () => {
    // location.pathname을 해석하여 화면 상태를 갱신한다.
  };

  window.addEventListener("popstate", handlePopState);
  return () => window.removeEventListener("popstate", handlePopState);
}, []);
```

외부 의존성 없이 실제 URL을 사용할 수 있다는 장점이 있다. 반면 애플리케이션이 다음 책임을 직접 맡아야 한다.

- 현재 `pathname`과 컴포넌트 상태의 동기화
- `/folders/:projectId` 형태의 경로 분석과 값 검증
- `popstate` 이벤트 등록 및 정리
- 알 수 없는 경로와 잘못된 ID 처리
- 링크 클릭 시 전체 페이지 요청 방지
- 배포 서버가 상세 경로 요청을 SPA 진입 파일로 돌려주도록 설정

이는 라우터가 이미 해결한 문제를 다시 구현하는 것이다. 특별한 번들 크기 제약이나 라이브러리 사용 제한이 없다면 이 폴더에서는 복잡도에 비해 얻는 이점이 작다.

#### 4. 쿼리 파라미터로 선택 상태 표현

경로는 목록 화면으로 유지하고 선택한 폴더만 쿼리 파라미터에 기록하는 방식이다.

```text
/folders?projectId=10
```

새로고침과 링크 공유가 가능하며, 목록 위에 상세 패널을 겹쳐 표시하는 UI라면 의미가 있다. 필터, 검색어, 정렬, 페이지 번호처럼 기본 화면의 부가 상태를 URL에 보존할 때도 잘 맞는다.

그러나 폴더 상세를 독립된 화면으로 취급한다면 `/folders/10`보다 리소스 구조가 덜 분명하다. 라우터 없이 구현하면 `URLSearchParams`, `pushState`, `popstate`의 동기화 책임도 여전히 남는다. 따라서 쿼리 파라미터는 상세 페이지 자체보다 목록 화면의 선택 상태나 필터를 나타낼 때 더 적절하다.

### 결론

이번 폴더 상세 화면은 다음 조건에 해당한다.

- 폴더마다 고유한 상세 조회 API가 있다.
- 상세 화면은 목록과 구분되는 중앙 콘텐츠 구조를 가진다.
- URL의 폴더 ID를 API 경로의 폴더 ID로 그대로 사용할 수 있다.
- 새로고침과 브라우저 탐색 후에도 같은 상세 화면을 유지하는 것이 자연스럽다.

폴더 상세는 목록의 일시적인 패널보다 독립 페이지에 가깝다. 따라서 `/folders`와 `/folders/:projectId`를 구분하고 `react-router-dom`을 사용하는 것으로 결정한다. 이 구조에서는 URL의 `projectId`를 상세 조회 API에 전달하고, 새로고침·직접 진입·링크 공유·브라우저 탐색을 함께 지원할 수 있다.

만약 요구사항이 바뀌어 상세 화면을 목록 안에서만 잠시 표시하고 새로고침·공유·직접 진입을 지원하지 않게 된다면, 새 의존성을 추가하지 않고 React 상태 방식으로 구현하는 편이 더 적절하다.

## 08-20 데일리 플랜 드래그앤드롭 기술 선택

### 상황

날짜별 데일리 플랜에서 같은 날짜 안의 Task 순서를 드래그앤드롭으로 변경하려고 한다. 현재 프론트엔드는 React 19, TypeScript, Vite를 사용하며, 순서가 바뀐 Task ID 배열은 기존 `PUT /api/daily-plan` API로 저장할 수 있다.

드래그앤드롭은 마우스뿐 아니라 터치와 키보드에서도 사용할 수 있어야 한다. 기존 카드 마크업과 CSS를 가능한 한 유지하면서 정렬 기능만 추가하는 것도 중요하다. Context7의 최신 문서를 기준으로 `dnd-kit`, `@hello-pangea/dnd`, `react-dnd`, 브라우저 네이티브 Drag and Drop을 비교했다.

### 대안 비교

| 방식 | 장점 | 단점 | 현재 구조와의 적합성 |
| --- | --- | --- | --- |
| `dnd-kit` | Pointer·Keyboard sensor, sortable preset, 충돌 감지, TypeScript 지원, 자유로운 DOM 구조 | sensor와 sortable context를 직접 조합해야 함 | 높음 |
| `@hello-pangea/dnd` | 세로 목록 정렬이 간단하고 기본 키보드·스크린리더 지원이 강함 | render props와 wrapper 구조가 UI에 많이 침투함 | 중간 |
| `react-dnd` | 여러 종류의 draggable 객체와 복잡한 drop target에 유연함 | backend 설정이 필요하고 touch backend가 별도이며 단순 정렬에는 복잡함 | 낮음 |
| 네이티브 HTML Drag and Drop | 외부 의존성이 없음 | 모바일 touch와 키보드 접근성을 직접 구현해야 함 | 낮음 |

#### `dnd-kit`

`DndContext`가 전체 드래그 상태를 관리하고 `SortableContext`가 정렬 가능한 ID 순서를 관리한다. 각 Task 카드는 `useSortable`을 사용하며, 드래그 종료 시 `active.id`와 `over.id`로 기존 위치와 새 위치를 찾는다. `arrayMove`로 새로운 배열을 만들면 현재 `drafts[selectedDate]` 상태 구조에 바로 적용할 수 있다.

```tsx
const sensors = useSensors(
  useSensor(PointerSensor),
  useSensor(KeyboardSensor, {
    coordinateGetter: sortableKeyboardCoordinates,
  }),
);

const handleDragEnd = ({ active, over }: DragEndEvent) => {
  if (!over || active.id === over.id) return;

  setItems((items) => {
    const from = items.findIndex((item) => item.id === active.id);
    const to = items.findIndex((item) => item.id === over.id);
    return arrayMove(items, from, to);
  });
};
```

Pointer sensor는 마우스와 포인터 입력을 처리하고 Keyboard sensor는 키보드 이동을 제공한다. `verticalListSortingStrategy`를 사용하면 같은 날짜 column 안의 세로 Task 목록과 잘 맞는다. 라이브러리가 카드의 시각 구조를 강제하지 않으므로 현재 카드와 메뉴 구조를 유지하기 쉽다.

#### `@hello-pangea/dnd`

`DragDropContext → Droppable → Draggable` 구조로 목록을 선언한다. 목적지가 있는지 확인한 후 source index와 destination index를 사용해 배열을 재정렬하므로 단일 세로 목록에는 이해하기 쉽다.

기본 키보드 이동, focus 관리, screen reader 안내가 강점이다. 그러나 각 목록과 카드가 라이브러리의 render props 구조 안으로 들어가야 한다. 여러 날짜 column을 렌더링하면서 선택한 날짜만 편집하는 현재 컴포넌트에서는 wrapper와 placeholder가 UI 구조에 더 많이 침투한다.

#### `react-dnd`

`DndProvider`와 backend를 설정하고 각 요소에서 `useDrag`, `useDrop`을 조합한다. 서로 다른 객체 유형, 복잡한 drop target, 캔버스형 UI에는 유연하다.

하지만 기본 HTML5 backend는 touch를 처리하지 않아 별도의 touch backend를 고려해야 한다. 단순한 배열 순서 변경을 위해 provider, backend, drag source, drop target을 모두 구성해야 하므로 현재 요구사항보다 복잡하다.

#### 네이티브 HTML Drag and Drop

브라우저의 `draggable`, `dragstart`, `dragover`, `drop` 이벤트만 사용하면 패키지를 추가하지 않을 수 있다. 하지만 모바일 touch 지원이 일관적이지 않고 키보드 조작과 screen reader 안내를 직접 설계해야 한다. 의존성 하나를 줄이는 대신 입력 장치별 동작과 접근성 구현 부담이 커진다.

### 결론

현재 요구사항에는 `dnd-kit`이 가장 적합하다.

- 기존 Task 카드의 DOM과 스타일을 유지할 수 있다.
- `drafts[selectedDate]` 배열을 `arrayMove`로 바로 갱신할 수 있다.
- Pointer와 Keyboard sensor를 함께 사용해 마우스·터치·키보드를 지원할 수 있다.
- 이번에는 같은 날짜 column 안의 순서 변경만 구현하고, 변경된 `taskIds`는 기존 저장 API로 전송할 수 있다.

추가할 패키지는 `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities`다. Context7 문서에는 React 19 호환성을 명시적으로 보증하는 내용이 없었으므로, 설치 후 peer dependency 확인과 TypeScript 빌드를 통해 실제 호환성을 검증한다.

## 08-21 `getDailyPlans` 개발 환경 중복 호출

### 상황

대시보드에 처음 진입할 때 브라우저 Network 탭에서 `getDailyPlans` 요청이 두 번 발생한다. `DailyPlanSection`은 마운트 시 `useEffect`에서 조회 API를 호출하며, 애플리케이션 루트는 React의 `StrictMode`로 감싸져 있다.

### 문제 원인

개발 환경의 `StrictMode`는 Effect의 정리 로직을 검증하기 위해 최초 마운트 때 `setup → cleanup → setup` 순서로 Effect를 한 번 더 실행한다. 따라서 `useEffect` 안의 `getDailyPlans`도 두 번 실행된다. 이는 개발 환경에서만 수행되는 검사이며 프로덕션 빌드에서는 같은 이유로 Effect가 두 번 실행되지 않는다.

현재 Effect의 cleanup은 `active = false`로 첫 번째 응답이 상태를 갱신하지 못하게 할 뿐, 이미 시작된 HTTP 요청을 취소하지는 않는다. 그 결과 오래된 응답의 상태 반영은 막지만 Network 탭에는 실제 요청이 두 건 표시된다.

인증 재시도로 발생하는 중복 요청은 별도로 구분해야 한다. Network 흐름이 `GET daily-plans 401 → POST auth/refresh → GET daily-plans 200`이라면 `api/client`가 토큰을 갱신한 뒤 원래 요청을 재시도한 것이다. 또한 `/api/auth/refresh`의 `ECONNREFUSED`는 Vite 프록시가 연결할 백엔드 서버에 접근하지 못했다는 뜻이며, `StrictMode`의 중복 Effect와는 다른 문제다.

### 대안 비교

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| `StrictMode`를 유지하고 개발 중 중복 호출을 허용 | React의 Effect 정리 검사를 유지하며 별도 구현이 필요 없다. 프로덕션에서는 한 번만 호출된다. | 개발 중 요청과 로그가 두 건 발생한다. |
| `AbortController`로 cleanup 시 요청 취소 | 사용하지 않을 첫 번째 요청과 응답 처리를 중단할 수 있고 Effect의 정리 책임이 명확해진다. | Network 탭에는 취소된 요청이 보일 수 있으며, 취소 시점에 따라 서버에는 이미 요청이 도달했을 수 있다. API 클라이언트가 `signal`을 전달하도록 구성해야 한다. |
| 동일 요청의 진행 중 Promise를 공유하거나 데이터 조회 라이브러리 사용 | 같은 키의 동시 요청을 하나로 합치고 캐시·재시도·갱신 정책도 함께 관리할 수 있다. | 캐시 키, 만료, 무효화와 오류 처리 정책이 추가된다. 현재 조회 하나만을 위해 도입하면 복잡도가 더 커질 수 있다. |
| `StrictMode` 제거 | 개발 환경의 최초 중복 호출이 사라진다. | Effect cleanup과 부수 효과 문제를 조기에 발견하는 검사를 잃는다. |
| `useRef`로 최초 한 번만 실행되게 차단 | 화면상 중복 호출을 빠르게 숨길 수 있다. | `StrictMode` 검사를 우회하고 실제 재마운트나 의존 값 변경 시 필요한 조회까지 막아 오래된 데이터를 만들 수 있다. |

### 결론

현재 중복 호출이 `StrictMode`의 개발 환경 검사에서만 발생한다면 `StrictMode`를 유지한다. 조회 API는 부수 효과가 없는 `GET`으로 설계하고, 개발 중 두 건의 요청이 보인다는 이유만으로 `StrictMode`를 제거하거나 `useRef` 실행 잠금을 추가하지 않는다.

요청 비용이 크거나 컴포넌트가 사라진 뒤 불필요한 작업을 확실히 줄여야 한다면 먼저 `AbortController`를 적용한다. 취소 표시까지 포함해 Network 요청을 반드시 한 건으로 합쳐야 하거나 여러 화면에서 같은 데이터를 공유하게 될 때는 진행 중 요청 중복 제거 또는 데이터 조회 라이브러리를 검토한다.

확인할 때는 요청 횟수만 보지 않고 상태 코드와 순서를 함께 본다. 동일한 `GET` 두 건이 바로 발생하면 `StrictMode` 가능성이 높고, 중간에 `401`과 `/auth/refresh`가 있다면 인증 갱신 재시도 문제로 분리해 점검한다.

## 08-24 CSS 크기와 비율 단위

### 상황

대시보드에서 왼쪽 Daily Plan·Session 영역과 오른쪽 Note 영역의 너비를 정하려고 했다. 고정된 크기를 표현하는 `px`, 글자 크기를 기준으로 하는 `rem`·`em`, 부모 크기를 기준으로 하는 `%`, Grid의 남은 공간을 나누는 `fr`은 기준과 동작이 서로 다르다. 화면 크기와 사용자 글꼴 설정에 대응하려면 각 단위가 무엇을 기준으로 계산되는지 알고 선택해야 한다.

### 대안 비교

#### 핵심 단위 비교

| 단위 | 기준 | 대표 용도 | 주의점 |
| --- | --- | --- | --- |
| `px` | CSS 픽셀 | 테두리, 아이콘, 정밀한 최소 크기 | 화면이나 글자 크기에 비례해 변하지 않는다. |
| `rem` | 루트 요소의 글자 크기 | 간격, 너비, 높이, 반응형 기준 | 루트 글자 크기의 영향을 받는다. |
| `em` | 현재 요소의 계산된 글자 크기 | 글자와 함께 변해야 하는 내부 여백 | 중첩되거나 `font-size`에 사용하면 값이 누적될 수 있다. |
| `%` | 속성별 기준이 되는 상위 크기 | 부모 대비 너비와 위치 | `gap`과 함께 쓰면 합계가 컨테이너를 넘을 수 있다. |
| `fr` | Grid에서 고정 크기와 gap을 뺀 남은 공간 | Grid 컬럼 비율 | Grid 전용이며 콘텐츠의 최소 너비에 밀릴 수 있다. |
| `vw`, `vh` | viewport 너비·높이의 1% | 화면 기준 섹션과 오버레이 | 모바일 브라우저 주소창 변화에 주의한다. |
| `svh`, `lvh`, `dvh` | 작은·큰·현재 viewport 높이의 1% | 모바일 전체 높이 화면 | 목적에 따라 안정성 또는 실시간 반응성을 선택한다. |
| `ch` | 현재 글꼴의 숫자 `0` 너비 | 읽기 좋은 텍스트 줄 길이 | 모든 문자의 정확한 개수를 의미하지 않는다. |
| `lh`, `rlh` | 현재 요소·루트 요소의 줄 높이 | 줄 높이에 맞춘 간격 | 브라우저 지원 범위를 확인해야 한다. |
| `vmin`, `vmax` | viewport의 짧은 변·긴 변의 1% | 정사각형에 가까운 반응형 크기 | 극단적인 화면 비율에서는 너무 작거나 커질 수 있다. |

#### `px`: 고정된 CSS 픽셀

```css
.card {
  border-width: 1px;
}
```

`1px`은 물리적인 모니터 픽셀 하나와 항상 같다는 뜻이 아니라 브라우저가 사용하는 CSS 픽셀 하나다. 고해상도 화면에서는 하나의 CSS 픽셀을 여러 장치 픽셀로 그릴 수 있다.

테두리, 작은 아이콘처럼 정밀한 크기에 적합하다. 반면 본문 글자, 넓은 간격, 전체 레이아웃을 모두 `px`로 고정하면 사용자 글꼴 설정이나 화면 변화에 유연하게 대응하기 어렵다.

#### `rem`: 루트 글자 크기 기준

```css
.note {
  width: 20rem;
  padding: 1.5rem;
}
```

`1rem`은 `html` 요소의 계산된 `font-size`와 같다. 루트 글자 크기가 기본값인 `16px`이라면 `20rem`은 보통 `320px`이다. 루트 글자 크기가 바뀌면 `rem`을 사용한 크기도 함께 변하므로 접근성과 일관된 크기 체계에 유리하다.

폴더의 spacing·font·control 디자인 토큰도 `rem` 기반으로 만들면 전체 밀도를 한 기준으로 조절하기 쉽다. 다만 `html { font-size: 16px; }`처럼 루트 크기를 고정하면 사용자 설정을 따르는 장점이 줄어들 수 있다.

#### `em`: 현재 요소 글자 크기 기준

```css
.chip {
  padding-inline: 0.75em;
}
```

`1em`은 해당 요소의 계산된 글자 크기다. 버튼이나 chip의 여백을 글자 크기와 함께 키우고 싶을 때 유용하다.

`font-size` 자체에 `em`을 사용하면 부모의 글자 크기를 기준으로 계산된다. 중첩된 요소마다 `font-size: 1.2em`을 적용하면 크기가 계속 곱해질 수 있으므로 전체 글자 체계에는 보통 `rem`이 더 예측 가능하다.

#### `%`: 속성마다 기준이 달라지는 비율

```css
.left { width: 70%; }
.right { width: 30%; }
```

일반적인 `width: 70%`는 containing block의 너비를 기준으로 한다. 하지만 모든 속성의 `%`가 같은 기준을 쓰는 것은 아니다.

- `width`, `left`는 보통 containing block의 너비가 기준이다.
- `height`의 `%`는 부모 높이가 명확하게 계산되어 있어야 기대대로 동작한다.
- `transform: translateX(50%)`는 부모가 아니라 변환되는 요소 자신의 너비가 기준이다.
- 일부 padding과 margin의 `%`는 containing block의 inline 크기를 기준으로 계산된다.

Grid에서 `70% 30%`를 사용하면서 `gap`도 두면 두 컬럼이 이미 전체 너비를 사용한 뒤 gap이 추가된다.

```css
.layout {
  grid-template-columns: 70% 30%;
  gap: 1rem;
}
```

이 경우 실제 필요 너비가 `100% + 1rem`이 되어 가로 overflow가 생길 수 있다.

#### `fr`: Grid의 남은 공간 분배

```css
.layout {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 3fr);
  gap: 1rem;
}
```

`fr`은 Grid 컨테이너에서 고정 폭과 gap을 먼저 제외한 뒤 남은 공간을 비율로 나눈다. `7fr 3fr`은 사용 가능한 공간을 왼쪽 70%, 오른쪽 30%에 가깝게 배분한다. `%`와 달리 gap 때문에 전체 너비가 넘치지 않는다.

단순히 `7fr 3fr`만 사용하면 Grid item의 기본 최소 크기인 `auto` 때문에 긴 문자열이 컬럼을 밀어낼 수 있다. 콘텐츠가 컬럼 안에서 줄어들거나 말줄임되어야 한다면 `minmax(0, ...)`와 내부 요소의 `min-width: 0`을 함께 사용한다.

```css
.left,
.right {
  min-width: 0;
}
```

#### viewport 단위

```css
.session-screen {
  min-height: 100svh;
}
```

- `1vw`: viewport 너비의 1%
- `1vh`: viewport 높이의 1%
- `1vmin`: viewport 너비와 높이 중 작은 값의 1%
- `1vmax`: viewport 너비와 높이 중 큰 값의 1%
- `1svh`: 모바일 브라우저 UI가 보이는 작은 viewport 높이의 1%
- `1lvh`: 브라우저 UI가 숨겨진 큰 viewport 높이의 1%
- `1dvh`: 주소창 변화에 따라 현재 viewport 높이를 실시간 반영한 1%

고정된 모바일 화면이 브라우저 UI에 가리지 않아야 하면 `svh`, 화면을 현재 보이는 높이에 계속 맞추려면 `dvh`가 적합하다. `100vh`는 모바일 브라우저의 주소창 상태에 따라 콘텐츠가 가려지거나 높이가 어긋날 수 있다.

#### 텍스트 기준 단위

```css
.article {
  max-width: 65ch;
}
```

- `ch`는 현재 글꼴에서 숫자 `0`의 너비를 기준으로 한다. 본문 한 줄의 읽기 좋은 길이를 제한할 때 유용하다.
- `ex`는 현재 글꼴의 소문자 `x` 높이를 기준으로 한다.
- `cap`은 대문자의 높이를 기준으로 한다.
- `lh`는 현재 요소의 줄 높이, `rlh`는 루트 요소의 줄 높이를 기준으로 한다.

글꼴마다 글자 폭이 다르므로 `65ch`가 정확히 65글자를 의미하지는 않는다.

#### 절대 물리 단위

CSS에는 `cm`, `mm`, `Q`, `in`, `pt`, `pc`도 있다. 화면에서는 브라우저가 이 단위들을 CSS 픽셀과 대응해 계산하므로 실제 자로 잰 물리 크기와 정확히 일치한다고 기대하면 안 된다. 인쇄용 스타일을 제외하면 일반 웹 레이아웃에서는 거의 사용하지 않는다.

#### 단위 없는 값과 길이 외 단위

```css
.text {
  line-height: 1.5;
  opacity: 0.8;
  transform: rotate(0.25turn);
  transition-duration: 150ms;
}
```

- `0`은 대부분의 길이 속성에서 단위를 생략할 수 있다.
- 단위 없는 `line-height`는 요소의 글자 크기에 배율로 적용되며 자식에게도 배율로 상속되어 예측하기 쉽다.
- `deg`, `rad`, `grad`, `turn`은 회전 각도 단위다.
- `s`, `ms`는 animation과 transition의 시간 단위다.
- `opacity`, `flex-grow`, `font-weight` 같은 값은 길이가 아닌 단위 없는 숫자다.

#### 크기 키워드와 계산 함수

`auto`, `min-content`, `max-content`, `fit-content`는 단위는 아니지만 레이아웃 크기를 결정할 때 자주 사용한다.

- `min-content`: 콘텐츠가 가능한 한 많이 줄어든 최소 너비
- `max-content`: 줄바꿈하지 않았을 때 필요한 최대 너비
- `fit-content`: 사용 가능한 공간 안에서 `min-content`와 `max-content` 사이로 조절
- `auto`: 속성과 레이아웃 문맥에 따라 브라우저가 계산

서로 다른 단위는 계산 함수로 조합할 수 있다.

```css
.panel {
  width: min(40rem, 100%);
  padding-inline: max(1rem, 3vw);
  font-size: clamp(1rem, 2vw, 1.5rem);
  min-height: calc(100svh - var(--top-bar-height));
}
```

- `calc()`는 단위가 다른 값을 산술 계산한다.
- `min()`은 후보 중 가장 작은 값을 선택한다.
- `max()`는 후보 중 가장 큰 값을 선택한다.
- `clamp(최솟값, 선호값, 최댓값)`은 반응형 값의 범위를 제한한다.

### 결론

단위를 하나로 통일하기보다 값이 무엇을 기준으로 변해야 하는지에 따라 선택한다.

- 얇은 테두리와 정밀한 아이콘 크기에는 `px`
- 글자 설정과 함께 변해야 하는 간격·컨트롤·폭에는 `rem`
- 개별 컴포넌트의 글자 크기에 비례하는 내부 여백에는 `em`
- 부모 크기 대비 단일 요소의 크기에는 `%`
- gap을 제외한 Grid 컬럼 비율에는 `fr`
- 화면 높이에 맞춘 레이아웃에는 목적에 따라 `svh` 또는 `dvh`
- 본문 줄 길이에는 `ch`

Daily Plan·Session과 Note의 좌우 비율은 `%`보다 다음처럼 `fr`을 사용한다.

```css
.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 3fr);
  gap: var(--space-4);
}
```

이 방식은 gap을 제외한 실제 사용 가능 공간을 7:3으로 나누고, 긴 Task 제목이 컬럼의 최소 너비를 밀어내는 것도 방지한다. 고정값을 직접 반복하기보다 폴더의 `styles/tokens.css`에 정의된 간격·폰트 토큰을 중요 사용한다.
