package com.swimming.backend.note.service;

import com.swimming.backend.note.dto.out.ProjectContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.task.domain.TaskStatus;

import java.util.List;

final class RemoteWorkerCertificationTestData {

    private RemoteWorkerCertificationTestData() {
    }

    static List<TaskOrganizerTestScenario> scenarios() {
        ProjectContext certification = project(
                201L,
                "정보처리기사 준비",
                "5월 목표로 단원별 이론, 기출문제, 오답노트와 실기 코드를 공부"
        );
        List<TaskContext> certificationTasks = List.of(
                task(2011L, certification, "단원별 이론 정리"),
                task(2012L, certification, "기출 3개년 풀기"),
                task(2013L, certification, "오답노트 정리"),
                task(2014L, certification, "실기 코드 연습")
        );

        return List.of(
                scenario(
                        "remote-worker-mixed-study-housework",
                        "자격증 공부와 집안일이 섞인 메모",
                        "정보처리기사 이론 정리는 Project에 분류하고 빨래와 분리수거는 다듬은 미분류 항목으로 남기는지 평가",
                        """
                                정처기 4단원 데이터베이스 부분 읽고 헷갈린거 정리해야됨
                                세탁기 끝나면 빨래 널기 아 분리수거도 오늘임
                                """,
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-split-study-actions",
                        "한 문장에 뭉친 세 가지 공부 행동",
                        "이론 복습, 기출 풀이, 오답노트 반영을 각각 실행 가능한 정보처리기사 Task로 분리하는지 평가",
                        "오늘 운영체제 이론 다시 보고 2023년 기출 20번까지 풀고 틀린건 오답노트에 옮겨야지",
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-merge-practical-code",
                        "흩어진 실기 코드 연습 메모 병합",
                        "같은 실기 SQL 작성·실행·오류 확인 메모를 하나의 응집된 정보처리기사 Task로 병합하는지 평가",
                        """
                                저녁에 실기 SQL 조인 문제 코드 쳐보기
                                실행해서 결과 이상한지도 보고
                                아까 그 조인 틀린 부분 다시 고치기
                                """,
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-ambiguous-notes",
                        "자격증 단서가 없는 모호한 학습 메모",
                        "대상 자료나 자격증 단서가 없는 정리·문제 풀이를 유일한 Project에 강제 배정하지 않고 미분류하는지 평가",
                        "2장 정리 좀 하고 문제도 몇개 풀기 근데 무슨 자료였지 나중에 확인",
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-certification-abbreviations",
                        "자격증 공부 축약어와 구어체",
                        "정처기·기출·오답이라는 축약 표현을 정보처리기사 Project와 기존 Task 맥락에 연결하는지 평가",
                        "정처기 기출 22년도꺼 마저 풀고 틀린 세문제 오답에 추가",
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-unrelated-office-work",
                        "유일한 자격증 Project와 무관한 회사 업무",
                        "재택근무 중 처리할 회사 업무를 정보처리기사 Project에 강제 배정하지 않고 다듬은 미분류 항목으로 남기는지 평가",
                        "내일 오전 팀 주간회의 자료 숫자 최신걸로 바꾸고 민지님한테 슬랙 보내기",
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-no-projects",
                        "Project 정보가 없는 저녁 메모",
                        "Project가 없을 때 공부와 집안일 행동을 각각 원문 보존된 미분류 항목으로 다듬는지 평가",
                        "퇴근하고 네트워크 기출 열문제 풀기\n택배 온거 경비실에서 찾아오기",
                        List.of(),
                        List.of()
                ),
                scenario(
                        "remote-worker-rough-typo-source",
                        "오타와 급한 표현이 섞인 오답 메모",
                        "러프한 원문은 sourceText에 그대로 보존하고 제목은 정보처리기사 오답 정리 행동으로 다듬는지 평가",
                        "정처기 데베 틀린거 왜틀렷는지 오답노트 정리하구 정규화 다시보긔",
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-existing-task-context",
                        "기존 Task 문맥으로 Project 판단",
                        "Project 이름 없이 틀린 기출과 해설 키워드를 기존 기출·오답 Task 묶음으로 읽어 정보처리기사에 분류하는지 평가",
                        "21년도 틀린 7개 해설 다시 읽고 왜 틀렸는지 옆에 한줄씩 적기",
                        List.of(certification),
                        certificationTasks
                ),
                scenario(
                        "remote-worker-actions-vs-distractions",
                        "공부 행동과 재택 환경 기록이 섞인 메모",
                        "명시된 기출 풀이와 실기 코드 연습만 Task로 만들고 알림 상태와 완료된 일정 기록은 미분류하는지 평가",
                        """
                                오늘 45분 타이머 켜고 기출 15문제 풀기
                                회사 메신저 알림은 저녁에도 계속 옴
                                실기 자바 배열 문제 코드 한번 직접 치기
                                5월 시험 일정은 달력에 적어둠
                                """,
                        List.of(certification),
                        certificationTasks
                )
        );
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
