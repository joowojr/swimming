package com.swimming.backend.user.service;

import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.dto.UserAuthInfo;
import com.swimming.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void returnsAuthenticationInfoByEmail() {
        User user = user();
        when(userRepository.findByEmailIgnoreCase("joowojr@gmail.com"))
                .thenReturn(Optional.of(user));

        Optional<UserAuthInfo> result = userService.getAuthInfoByEmail("joowojr@gmail.com");

        assertThat(result).contains(expectedAuthInfo());
    }

    @Test
    void returnsAuthenticationInfoById() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Optional<UserAuthInfo> result = userService.getAuthInfoById(1L);

        assertThat(result).contains(expectedAuthInfo());
    }

    @Test
    void returnsEmptyWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(userService.getAuthInfoById(99L)).isEmpty();
    }

    private User user() {
        User user = User.builder()
                .email("joowojr@gmail.com")
                .passwordHash("password-hash")
                .nickname("joowojr")
                .timezone("Asia/Seoul")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private UserAuthInfo expectedAuthInfo() {
        return new UserAuthInfo(
                1L,
                "joowojr@gmail.com",
                "password-hash",
                "joowojr",
                "Asia/Seoul"
        );
    }
}
