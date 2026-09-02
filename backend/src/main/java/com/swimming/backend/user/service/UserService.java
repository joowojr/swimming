package com.swimming.backend.user.service;

import com.swimming.backend.common.exception.BusinessException;
import com.swimming.backend.common.exception.ErrorCode;
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
public class UserService {

    private final UserRepository userRepository;
    @Transactional(propagation = Propagation.REQUIRED)
    public UserAuthInfo findOrCreateGoogleUser(String googleSubject, String email, String nickname) {
        return userRepository.findByGoogleSubject(googleSubject)
                .map(this::toAuthInfo)
                .orElseGet(() -> toAuthInfo(userRepository.save(User.builder()
                        .googleSubject(googleSubject)
                        .email(email)
                        .nickname(nickname)
                        .timezone("Asia/Seoul")
                        .build())));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<UserAuthInfo> getAuthInfoById(Long id) {
        return userRepository.findById(id)
                .map(this::toAuthInfo);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public String getTimezone(Long id) {
        return userRepository.findById(id)
                .map(User::getTimezone)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserAuthInfo toAuthInfo(User user) {
        return new UserAuthInfo(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getTimezone()
        );
    }
}
