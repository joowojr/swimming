package com.swimming.backend.user.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.dto.UserAuthInfo;
import com.swimming.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
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

    @Test
    @DisplayName("사용자의 타임존을 반환한다")
    void returnsUserTimezone() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));

        assertThat(userService.getTimezone(1L)).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 타임존은 조회할 수 없다")
    void rejectsMissingUserTimezone() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getTimezone(99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("현재 비밀번호를 확인하고 새 비밀번호 해시를 저장한다")
    void changesPasswordWhenCurrentPasswordMatches() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "password-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-password-hash");

        userService.changePassword(1L, "old-password", "new-password");

        assertThat(user.getPasswordHash()).isEqualTo("new-password-hash");
    }

    @Test
    @DisplayName("현재 비밀번호가 다르면 변경하지 않는다")
    void rejectsWrongCurrentPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong-password", "password-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, "wrong-password", "new-password"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH));
    }

    @Test
    @DisplayName("현재 비밀번호와 같은 새 비밀번호는 사용할 수 없다")
    void rejectsReusedPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("password-hash", "password-hash")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(1L, "password-hash", "password-hash"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_REUSE_NOT_ALLOWED));
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
