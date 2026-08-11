package com.example.jobtracker.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** 이력서 URL 파싱 요청 DTO */
public record ProfileUrlRequest(
        @NotBlank(message = "URL을 입력해주세요") String url
) {
}
