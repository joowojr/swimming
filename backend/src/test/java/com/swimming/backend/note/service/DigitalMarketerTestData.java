package com.swimming.backend.note.service;

import com.swimming.backend.note.dto.out.FolderContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.task.domain.TaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 세 클라이언트를 병행하는 프리랜서 마케터 시나리오.
 */
final class DigitalMarketerTestData {

    private static final int CAP = 10;

    private DigitalMarketerTestData() {
    }

    private static final FolderContext COMMERCE_RENEWAL = folder(
            301L,
            "커머스 리뉴얼",
            "목표일 2주 뒤인 클라이언트 A 업무. 리뉴얼 전환 측정, CRM과 오픈 캠페인 운영"
    );
    private static final FolderContext ADMIN_DASHBOARD = folder(
            302L,
            "관리자 대시보드",
            "클라이언트 B 업무. 마케팅 성과 지표 정의, 대시보드 데이터 검수와 운영 화면 개선"
    );
    private static final FolderContext SIDE_APP = folder(
            303L,
            "개인 사이드 앱",
            "개인 앱의 랜딩 페이지, 베타 사용자 모집과 콘텐츠 운영"
    );
    private static final FolderContext BRAND_LAUNCH = folder(
            304L,
            "신규 브랜드 런칭 캠페인",
            "클라이언트 C 업무. 신규 브랜드의 매체 계획, 크리에이터 시딩과 런칭 콘텐츠 운영"
    );
    private static final FolderContext FREELANCE_OPERATIONS = folder(
            305L,
            "프리랜서 운영",
            "세 클라이언트의 견적서, 인보이스, 미팅 일정과 업무 정산 관리"
    );

    private static final List<FolderContext> ALL_FOLDERS = List.of(
            COMMERCE_RENEWAL, ADMIN_DASHBOARD, SIDE_APP, BRAND_LAUNCH, FREELANCE_OPERATIONS
    );

    static List<TaskOrganizerTestScenario> scenarios() {
        List<TaskContext> tasks = allTasks();
        List<TaskContext> capped = cappedTasks();

        return List.of(
                scenario(
                        "marketer-three-clients-mixed",
                        "세 클라이언트 업무가 한 메모에 섞인 상황",
                        "구매 퍼널은 커머스 리뉴얼, KPI 표는 관리자 대시보드, 시딩 후보는 브랜드 런칭 캠페인으로 각각 분류하는지 평가",
                        """
                                A몰 결제완료 이벤트 이름 개발팀에 다시 보내기
                                관리자쪽 주간 KPI 표에서 ROAS 소수점 이상한거 확인
                                런칭 브랜드 시딩 후보 열명만 더 추리기
                                """,
                        ALL_FOLDERS,
                        tasks
                ),
                scenario(
                        "marketer-five-folders-one-note",
                        "다섯 Folder 작업이 한 문장에 이어진 메모",
                        "커머스·대시보드·사이드 앱·브랜드 런칭·프리랜서 운영 행동을 각각 올바른 Folder Task로 분리하는지 평가",
                        "리뉴얼 오픈메일 제목 두개 쓰고 대시보드 필터 문구 확인하고 사이드앱 베타 모집글 올리고 런칭 광고 소재 사이즈 전달, 지난달 인보이스도 보내기",
                        ALL_FOLDERS,
                        tasks
                ),
                scenario(
                        "marketer-merge-fragmented-commerce",
                        "파편화된 커머스 리뉴얼 측정 작업",
                        "결제 퍼널 이벤트·UTM·테스트 구매 확인을 하나의 커머스 리뉴얼 검증 Task로 병합하고 장보기는 미분류하는지 평가",
                        """
                                A 리뉴얼 결제 퍼널 이벤트 확인
                                utm source 새 규칙으로 들어오는지도 같이
                                우유랑 휴지 주문
                                아까 리뉴얼 테스트구매 한건 찍어서 GA에서 보기
                                """,
                        ALL_FOLDERS,
                        tasks
                ),
                scenario(
                        "marketer-ambiguous-conversion-report",
                        "여러 클라이언트에 해당할 수 있는 전환 리포트",
                        "어느 클라이언트인지 단서가 없는 전환 리포트 작업을 임의 Folder에 배정하지 않고 다듬은 미분류 항목으로 남기는지 평가",
                        "지난주 전환 리포트 뽑고 광고비 비교해야되는데 어느 클라건지 안적어놨음",
                        List.of(COMMERCE_RENEWAL, ADMIN_DASHBOARD, BRAND_LAUNCH),
                        tasks
                ),
                scenario(
                        "marketer-folder-aliases",
                        "프로젝트 별칭과 축약어로 적은 메모",
                        "A몰·어드민·사앱·런칭이라는 표현을 각각 대응하는 네 Folder로 연결하는지 평가",
                        "A몰 CRM 링크 수정 / 어드민 캠페인 표 헤더 바꾸기 / 사앱 랜딩 CTA 문구 쓰기 / 런칭 인플루언서 답장 체크",
                        ALL_FOLDERS,
                        tasks
                ),
                scenario(
                        "marketer-context-switch-fragments",
                        "프로젝트를 오가며 끊어서 적은 후속 작업",
                        "파편화된 메모를 내용별로 묶어 커머스 UTM, 대시보드 지표 정의, 브랜드 크리에이터 회신 Task로 분리하는지 평가",
                        """
                                A utm 이름 지난 캠페인이랑 겹침 확인
                                관리자 이탈률 정의 문서 어디있는지 찾아서 링크
                                다시 A 링크 개발팀에 전달
                                런칭 크리에이터 회신 온거 시트 반영
                                관리자 이탈률 계산 기준도 한줄 추가
                                """,
                        ALL_FOLDERS,
                        tasks
                ),
                scenario(
                        "marketer-unrelated-housework",
                        "다섯 Folder와 무관한 집안일",
                        "Folder가 다섯 개 있어도 음식물 쓰레기와 세탁소 수령을 강제 배정하지 않고 미분류하는지 평가",
                        "음쓰 버리고 세탁소 맡긴 셔츠 퇴근 전에 찾아오기",
                        ALL_FOLDERS,
                        tasks
                ),
                scenario(
                        "marketer-rough-typo-source",
                        "오타와 축약이 섞인 대시보드 QA 메모",
                        "오타가 있는 원문은 sourceText에 보존하고 제목은 관리자 대시보드 데이터 QA 행동으로 다듬는지 평가",
                        "어드민 전환율 어제꺼 숫자 안마즘 원본시트랑 대조하구 날짜필터도 체쿠",
                        ALL_FOLDERS,
                        tasks
                ),
                scenario(
                        "marketer-actions-vs-status-notes",
                        "실행 행동과 프로젝트 전환 기록이 섞인 메모",
                        "명시된 CRM 카피·대시보드 QA·런칭 소재 전달만 Task로 만들고 회의 종료와 열어둔 화면 같은 상태 기록은 미분류하는지 평가",
                        """
                                오전 A 미팅은 끝남
                                리뉴얼 장바구니 이탈 CRM 카피 초안 쓰기
                                관리자 대시보드 탭 열어둔 상태
                                어제 데이터 기준으로 KPI 카드 QA
                                런칭 피드 소재 규격 디자이너에게 전달
                                사이드앱은 이번주 손댄거 없음
                                """,
                        ALL_FOLDERS,
                        tasks
                ),

                // 아래 두 쌍은 Folder 이름·별칭이 메모에 없어 기존 Task 어휘로만 풀린다.
                // 근거 Task가 11번째 이후에 있어 capped 에서는 사라진다.
                scenario(
                        "marketer-existing-task-context",
                        "기존 Task의 고유 용어로 Folder 판단",
                        "Folder 이름 없이 구매 퍼널과 begin_checkout 맥락을 기존 GA4 Task 묶음으로 읽어 커머스 리뉴얼에 분류하는지 평가",
                        "begin_checkout만 두번 잡히는거 테스트 주문 로그랑 맞춰서 원인 확인",
                        List.of(COMMERCE_RENEWAL, ADMIN_DASHBOARD, BRAND_LAUNCH),
                        tasks
                ),
                scenario(
                        "marketer-existing-task-context-capped",
                        "기존 Task를 최신 10개로 자른 뒤 같은 판단",
                        "근거였던 GA4·begin_checkout Task가 잘려 나갔을 때 커머스 리뉴얼로 계속 분류하는지, 미분류로 내려가는지 평가",
                        "begin_checkout만 두번 잡히는거 테스트 주문 로그랑 맞춰서 원인 확인",
                        List.of(COMMERCE_RENEWAL, ADMIN_DASHBOARD, BRAND_LAUNCH),
                        capped
                ),
                scenario(
                        "marketer-tag-manager-vocabulary",
                        "Folder 설명에 없는 도구 이름으로 판단",
                        "태그 매니저라는 단서가 기존 Task에만 있을 때 커머스 리뉴얼로 분류하는지 평가",
                        "태그매니저에 새로 올린 스크립트 지난주꺼랑 중복인지 보기",
                        List.of(COMMERCE_RENEWAL, ADMIN_DASHBOARD, BRAND_LAUNCH),
                        tasks
                ),
                scenario(
                        "marketer-tag-manager-vocabulary-capped",
                        "태그 매니저 Task를 자른 뒤 같은 판단",
                        "Folder 설명에 없는 도구 어휘가 사라졌을 때 임의 배정하지 않고 미분류로 남기는지 평가",
                        "태그매니저에 새로 올린 스크립트 지난주꺼랑 중복인지 보기",
                        List.of(COMMERCE_RENEWAL, ADMIN_DASHBOARD, BRAND_LAUNCH),
                        capped
                )
        );
    }

    private static List<TaskContext> allTasks() {
        List<TaskContext> tasks = new ArrayList<>();
        tasksByFolder().forEach(tasks::addAll);
        return List.copyOf(tasks);
    }

    private static List<TaskContext> cappedTasks() {
        List<TaskContext> tasks = new ArrayList<>();
        tasksByFolder().forEach(folderTasks ->
                tasks.addAll(folderTasks.subList(0, Math.min(CAP, folderTasks.size())))
        );
        return List.copyOf(tasks);
    }

    /**
     * 각 목록은 생성일 최신순이다. 뒤로 갈수록 오래된 Task이고, cap 을 걸면 뒤에서부터 사라진다.
     *
     * <p>커머스 리뉴얼의 측정 도구 묶음(GA4, begin_checkout, UTM, 태그 매니저)과
     * 브랜드 런칭의 시딩 묶음은 의도적으로 11번째 이후에 뒀다.
     */
    private static List<List<TaskContext>> tasksByFolder() {
        return List.of(
                folderTasks(3010L, COMMERCE_RENEWAL, List.of(
                        "리뉴얼 오픈 CRM 캠페인 작성",
                        "장바구니 이탈 메일 발송 시간 조정",
                        "오픈 배너 A/B 문구 정리",
                        "쿠폰 코드 발급 규칙 확인",
                        "리뉴얼 랜딩 모바일 깨짐 수정 요청",
                        "오픈 알림톡 발송 리스트 정리",
                        "회원 등급별 혜택 안내 문구",
                        "리뉴얼 FAQ 페이지 문구 검토",
                        "오픈 첫주 프로모션 일정 확정",
                        "리뉴얼 공지 팝업 노출 조건",
                        "리뉴얼 구매 퍼널 GA4 이벤트 설계",
                        "begin_checkout 파라미터 스펙 정리",
                        "UTM 파라미터 규칙 정리",
                        "테스트 주문 로그 수집 방식 확인",
                        "전환 태그 매니저 스크립트 배포",
                        "구매 완료 페이지 스크립트 점검"
                )),
                folderTasks(3020L, ADMIN_DASHBOARD, List.of(
                        "캠페인 KPI 위젯 요구사항 정리",
                        "대시보드 집계 데이터 QA",
                        "날짜 필터 기본값 변경",
                        "권한별 메뉴 노출 정리",
                        "표 정렬 옵션 추가 요청",
                        "리포트 다운로드 포맷 확인",
                        "지표 카드 색상 기준 정리",
                        "대시보드 로딩 지연 확인",
                        "주간 리포트 자동 발송 설정",
                        "사용자 피드백 정리",
                        "이탈률 지표 정의 문서화",
                        "ROAS 계산 기준 합의",
                        "대시보드 초기 화면 설계",
                        "데이터 소스 연결 점검",
                        "접속 통계 수집 범위 확인",
                        "대시보드 접근 로그 정리"
                )),
                folderTasks(3030L, SIDE_APP, List.of(
                        "사이드 앱 랜딩 페이지 카피 작성",
                        "베타 모집 폼 항목 정리",
                        "앱 소개 이미지 교체",
                        "뉴스레터 발행 주기 결정",
                        "사용 후기 수집 방법 정리",
                        "랜딩 CTA 버튼 문구 후보",
                        "오픈그래프 태그 설정",
                        "베타 안내 메일 초안",
                        "콘텐츠 발행 캘린더 작성",
                        "앱스토어 설명 문구 다듬기",
                        "초기 사용자 인터뷰 질문 정리",
                        "서비스 이름 후보 정리",
                        "사용자 온보딩 튜토리얼 초안",
                        "랜딩 폼 스팸 필터 적용",
                        "도메인 연결 확인",
                        "초기 컨셉 무드보드 정리"
                )),
                folderTasks(3040L, BRAND_LAUNCH, List.of(
                        "브랜드 런칭 매체 플랜 작성",
                        "런칭 피드 소재 규격 정리",
                        "인플루언서 회신 시트 정리",
                        "런칭 광고 예산 배분",
                        "티저 콘텐츠 일정 확인",
                        "브랜드 톤앤매너 가이드 확인",
                        "매체별 소재 사이즈 정리",
                        "런칭 기념 이벤트 경품 확정",
                        "광고 계정 세팅 점검",
                        "캠페인 명명 규칙 정리",
                        "시딩 크리에이터 후보 정리",
                        "크리에이터 제품 발송 리스트",
                        "시딩 가이드라인 문서 작성",
                        "협업 계약서 양식 준비",
                        "브랜드 로고 사용 규정 확인",
                        "런칭 사전 알림 신청 페이지 준비"
                )),
                folderTasks(3050L, FREELANCE_OPERATIONS, List.of(
                        "월별 클라이언트 인보이스 발송",
                        "다음달 미팅 일정 조율",
                        "견적서 양식 업데이트",
                        "계약 만료일 정리",
                        "업무 시간 기록 정리",
                        "세금계산서 발행 확인",
                        "클라이언트별 정산 내역 대조",
                        "프로젝트 종료 보고서 양식 정리",
                        "경비 영수증 정리",
                        "신규 문의 응대 템플릿 작성",
                        "포트폴리오 업데이트",
                        "연간 수입 정리",
                        "업무 계약 조건 비교표 작성",
                        "클라이언트 온보딩 체크리스트 정리",
                        "작업 관리 도구 정리",
                        "사업자 등록 정보 갱신"
                ))
        );
    }

    private static List<TaskContext> folderTasks(
            long baseId,
            FolderContext folder,
            List<String> titles
    ) {
        List<TaskContext> tasks = new ArrayList<>();
        for (int index = 0; index < titles.size(); index++) {
            tasks.add(new TaskContext(
                    baseId + index,
                    folder.id(),
                    titles.get(index),
                    TaskStatus.TODO
            ));
        }
        return List.copyOf(tasks);
    }

    /**
     * 운영에서는 Folder와 Task를 한 번에 조회하므로 목록에 없는 Folder의 Task는 넘어가지 않는다.
     * Folder를 일부만 쓰는 시나리오에서도 같은 조건을 맞춘다.
     */
    private static TaskOrganizerTestScenario scenario(
            String id,
            String name,
            String evaluationCriteria,
            String memo,
            List<FolderContext> folders,
            List<TaskContext> tasks
    ) {
        Set<Long> folderIds = folders.stream()
                .map(FolderContext::id)
                .collect(Collectors.toSet());

        return new TaskOrganizerTestScenario(
                id,
                name,
                evaluationCriteria,
                new TaskOrganizerInput(
                        memo.strip(),
                        folders,
                        tasks.stream()
                                .filter(task -> folderIds.contains(task.folderId()))
                                .toList()
                )
        );
    }

    private static FolderContext folder(Long id, String name, String description) {
        return new FolderContext(id, name, description);
    }
}
