package com.swimming.backend.user.service;

import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-service;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key=test",
        "app.place.background.cdn-base-url=https://cdn.example.com"
})
class UserServicePersistenceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("비밀번호 변경 트랜잭션이 새 비밀번호 해시를 DB에 반영한다")
    void persistsChangedPasswordHash() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("persistence@example.com")
                .passwordHash(passwordEncoder.encode("old-password"))
                .nickname("persistence-user")
                .timezone("Asia/Seoul")
                .build());

        userService.changePassword(user.getId(), "old-password", "new-password");

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("new-password", persisted.getPasswordHash())).isTrue();
    }
}
