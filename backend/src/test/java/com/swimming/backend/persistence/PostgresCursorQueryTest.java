package com.swimming.backend.persistence;

import com.swimming.backend.task.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 커서 페이지 쿼리를 실제 PostgreSQL에 던져 본다. 읽기 전용이라 데이터를 건드리지 않는다.
 *
 * <pre>
 * docker compose up -d
 * ./gradlew postgresCheck
 * </pre>
 *
 * <p>다른 저장소 테스트는 H2를 PostgreSQL 모드로 쓴다. 그 모드는 바인딩 파라미터의 타입을
 * 너그럽게 넘겨 주기 때문에, {@code :cursor is null} 같은 구문이 진짜 PostgreSQL에서만
 * {@code could not determine data type of parameter}로 터지는 것을 잡지 못한다. 이 테스트가
 * 그 자리를 메운다.
 *
 * <p>로컬 DB가 떠 있어야 하므로 기본 {@code test}에서는 제외한다.
 */
@Tag("postgres")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/swimming",
        "spring.datasource.username=swimming",
        "spring.datasource.password=swimming1234",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
class PostgresCursorQueryTest {

    @Autowired TaskRepository taskRepository;

    @Test
    @DisplayName("폴더 할 일 첫 페이지와 다음 페이지 쿼리가 PostgreSQL에서 실행된다")
    void folderTaskPageQueries() {
        assertThatCode(() -> taskRepository.findFolderTaskFirstPage(2L, PageRequest.of(0, 21)))
                .doesNotThrowAnyException();
        assertThatCode(() -> taskRepository.findFolderTaskNextPage(
                2L, Instant.parse("2026-03-01T00:00:00Z"), 1L, PageRequest.of(0, 21)
        )).doesNotThrowAnyException();
    }

}
