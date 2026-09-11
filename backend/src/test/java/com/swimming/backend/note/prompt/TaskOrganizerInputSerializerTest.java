package com.swimming.backend.note.prompt;

import com.swimming.backend.note.dto.out.FolderContext;
import com.swimming.backend.note.dto.out.TaskContext;
import com.swimming.backend.note.dto.out.TaskOrganizerInput;
import com.swimming.backend.task.domain.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskOrganizerInputSerializerTest {

    @Test
    @DisplayName("Task 제목을 소속 Folder 내부에 줄 단위로 직렬화한다")
    void serializesTaskTitlesInsideTheirFolder() {
        TaskOrganizerInput input = new TaskOrganizerInput(
                "로그인 흐름 정리",
                LocalDate.of(2026, 9, 9),
                List.of(
                        new FolderContext(10L, "Swimming", "생산성 서비스"),
                        new FolderContext(20L, "포트폴리오", "취업 자료")
                ),
                List.of(
                        new TaskContext(1L, 10L, "로그인 API 수정", TaskStatus.TODO),
                        new TaskContext(2L, 20L, "이력서 업데이트", TaskStatus.DOING),
                        new TaskContext(3L, 10L, "Organizer 테스트", TaskStatus.DONE)
                )
        );

        assertThat(TaskOrganizerInputSerializer.serialize(input)).isEqualTo(
                "<task-organizer-input>"
                        + "<memo><![CDATA[로그인 흐름 정리]]></memo>"
                        + "<folders>"
                        + "<folder id=\"10\"><name>Swimming</name><description>생산성 서비스</description>"
                        + "<tasks>로그인 API 수정\nOrganizer 테스트</tasks></folder>"
                        + "\n<folder id=\"20\"><name>포트폴리오</name><description>취업 자료</description>"
                        + "<tasks>이력서 업데이트</tasks></folder>"
                        + "</folders>"
                        + "</task-organizer-input>"
        );
    }

    @Test
    @DisplayName("Task가 없는 Folder에는 tasks 요소를 만들지 않고 XML 특수문자를 이스케이프한다")
    void omitsEmptyTasksAndEscapesXml() {
        TaskOrganizerInput input = new TaskOrganizerInput(
                "메모",
                List.of(
                        new FolderContext(10L, "개발 & 테스트", "<백엔드>"),
                        new FolderContext(20L, "빈 폴더", null)
                ),
                List.of(
                        new TaskContext(1L, 10L, "A < B & C", TaskStatus.TODO),
                        new TaskContext(2L, 999L, "알 수 없는 Folder", TaskStatus.TODO)
                )
        );

        String serialized = TaskOrganizerInputSerializer.serialize(input);

        assertThat(serialized)
                .contains("<folder id=\"10\"><name>개발 &amp; 테스트</name><description>&lt;백엔드&gt;</description><tasks>A &lt; B &amp; C</tasks></folder>")
                .contains("<folder id=\"20\"><name>빈 폴더</name><description></description></folder>")
                .doesNotContain("알 수 없는 Folder")
                .doesNotContain("folderId=")
                .doesNotContain("<task>");
    }
}
