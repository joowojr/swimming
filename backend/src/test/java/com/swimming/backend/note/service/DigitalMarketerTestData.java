package com.swimming.backend.note.service;

import com.swimming.backend.note.dto.out.ProjectContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.task.domain.TaskStatus;

import java.util.List;

final class DigitalMarketerTestData {

    private DigitalMarketerTestData() {
    }

    static List<TaskOrganizerTestScenario> scenarios() {
        ProjectContext commerceRenewal = project(
                301L,
                "커머스 리뉴얼",
                "목표일 2주 뒤인 클라이언트 A 업무. 리뉴얼 전환 측정, CRM과 오픈 캠페인 운영"
        );
        ProjectContext adminDashboard = project(
                302L,
                "관리자 대시보드",
                "클라이언트 B 업무. 마케팅 성과 지표 정의, 대시보드 데이터 검수와 운영 화면 개선"
        );
        ProjectContext sideApp = project(
                303L,
                "개인 사이드 앱",
                "개인 앱의 랜딩 페이지, 베타 사용자 모집과 콘텐츠 운영"
        );
        ProjectContext brandLaunch = project(
                304L,
                "신규 브랜드 런칭 캠페인",
                "클라이언트 C 업무. 신규 브랜드의 매체 계획, 크리에이터 시딩과 런칭 콘텐츠 운영"
        );
        ProjectContext freelanceOperations = project(
                305L,
                "프리랜서 운영",
                "세 클라이언트의 견적서, 인보이스, 미팅 일정과 업무 정산 관리"
        );

        List<TaskContext> tasks = List.of(
                task(3011L, commerceRenewal, "리뉴얼 구매 퍼널 GA4 이벤트 설계"),
                task(3012L, commerceRenewal, "리뉴얼 오픈 CRM 캠페인 작성"),
                task(3021L, adminDashboard, "캠페인 KPI 위젯 요구사항 정리"),
                task(3022L, adminDashboard, "대시보드 집계 데이터 QA"),
                task(3031L, sideApp, "사이드 앱 랜딩 페이지 카피 작성"),
                task(3041L, brandLaunch, "브랜드 런칭 매체 플랜 작성"),
                task(3042L, brandLaunch, "시딩 크리에이터 후보 정리"),
                task(3051L, freelanceOperations, "월별 클라이언트 인보이스 발송")
        );

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
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
                        tasks
                ),
                scenario(
                        "marketer-five-projects-one-note",
                        "다섯 Project 작업이 한 문장에 이어진 메모",
                        "커머스·대시보드·사이드 앱·브랜드 런칭·프리랜서 운영 행동을 각각 올바른 Project Task로 분리하는지 평가",
                        "리뉴얼 오픈메일 제목 두개 쓰고 대시보드 필터 문구 확인하고 사이드앱 베타 모집글 올리고 런칭 광고 소재 사이즈 전달, 지난달 인보이스도 보내기",
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
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
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
                        tasks
                ),
                scenario(
                        "marketer-ambiguous-conversion-report",
                        "여러 클라이언트에 해당할 수 있는 전환 리포트",
                        "어느 클라이언트인지 단서가 없는 전환 리포트 작업을 임의 Project에 배정하지 않고 다듬은 미분류 항목으로 남기는지 평가",
                        "지난주 전환 리포트 뽑고 광고비 비교해야되는데 어느 클라건지 안적어놨음",
                        projects(commerceRenewal, adminDashboard, brandLaunch),
                        tasks
                ),
                scenario(
                        "marketer-project-aliases",
                        "프로젝트 별칭과 축약어로 적은 메모",
                        "A몰·어드민·사앱·런칭이라는 표현을 각각 대응하는 네 Project로 연결하는지 평가",
                        "A몰 CRM 링크 수정 / 어드민 캠페인 표 헤더 바꾸기 / 사앱 랜딩 CTA 문구 쓰기 / 런칭 인플루언서 답장 체크",
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
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
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
                        tasks
                ),
                scenario(
                        "marketer-unrelated-housework",
                        "다섯 Project와 무관한 집안일",
                        "Project가 다섯 개 있어도 음식물 쓰레기와 세탁소 수령을 강제 배정하지 않고 미분류하는지 평가",
                        "음쓰 버리고 세탁소 맡긴 셔츠 퇴근 전에 찾아오기",
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
                        tasks
                ),
                scenario(
                        "marketer-rough-typo-source",
                        "오타와 축약이 섞인 대시보드 QA 메모",
                        "오타가 있는 원문은 sourceText에 보존하고 제목은 관리자 대시보드 데이터 QA 행동으로 다듬는지 평가",
                        "어드민 전환율 어제꺼 숫자 안마즘 원본시트랑 대조하구 날짜필터도 체쿠",
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
                        tasks
                ),
                scenario(
                        "marketer-existing-task-context",
                        "기존 Task의 고유 용어로 Project 판단",
                        "Project 이름 없이 구매 퍼널과 begin_checkout 맥락을 기존 GA4 Task 묶음으로 읽어 커머스 리뉴얼에 분류하는지 평가",
                        "begin_checkout만 두번 잡히는거 테스트 주문 로그랑 맞춰서 원인 확인",
                        projects(commerceRenewal, adminDashboard, brandLaunch),
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
                        projects(commerceRenewal, adminDashboard, sideApp, brandLaunch, freelanceOperations),
                        tasks
                )
        );
    }

    private static List<ProjectContext> projects(ProjectContext... projects) {
        return List.of(projects);
    }

    private static TaskOrganizerTestScenario scenario(
            String id,
            String name,
            String evaluationCriteria,
            String memo,
            List<ProjectContext> projects,
            List<TaskContext> tasks
    ) {
        return new TaskOrganizerTestScenario(
                id,
                name,
                evaluationCriteria,
                new TaskOrganizerInput(memo.strip(), projects, tasks)
        );
    }

    private static ProjectContext project(Long id, String name, String description) {
        return new ProjectContext(id, name, description);
    }

    private static TaskContext task(Long id, ProjectContext project, String title) {
        return new TaskContext(id, project.id(), title, TaskStatus.TODO);
    }
}
