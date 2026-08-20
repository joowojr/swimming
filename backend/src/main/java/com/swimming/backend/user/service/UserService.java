package com.swimming.backend.user.service;

import com.swimming.backend.user.domain.User;
import com.swimming.backend.user.dto.UserAuthInfo;
import com.swimming.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(
        propagation = Propagation.REQUIRED,
        readOnly = true
)
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
