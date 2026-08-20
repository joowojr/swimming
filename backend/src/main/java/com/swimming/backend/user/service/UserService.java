package com.swimming.backend.user.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.dto.UserAuthInfo;
import com.swimming.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Optional<UserAuthInfo> getAuthInfoByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(this::toAuthInfo);
    }

    public Optional<UserAuthInfo> getAuthInfoById(Long id) {
        return userRepository.findById(id)
                .map(this::toAuthInfo);
    }

    public String getTimezone(Long id) {
        return userRepository.findById(id)
                .map(User::getTimezone)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserAuthInfo toAuthInfo(User user) {
        return new UserAuthInfo(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getNickname(),
                user.getTimezone()
        );
    }
}
