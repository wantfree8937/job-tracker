package com.example.jobtracker.dto.auth;

import java.time.LocalDateTime;
import java.util.List;

/** 사용자 정보 응답 DTO. 비밀번호는 절대 포함하지 않는다. */
public record UserResponse(
        Long id,
        String email,
        String nickname,
        LocalDateTime createdAt,
        List<String> keywords
) {
}
