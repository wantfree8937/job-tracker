package com.example.jobtracker.dto.auth;

/** 로그인 성공 시 발급하는 JWT 응답 DTO */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse of(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn);
    }
}
