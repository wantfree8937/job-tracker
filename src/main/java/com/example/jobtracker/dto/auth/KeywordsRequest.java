package com.example.jobtracker.dto.auth;

import jakarta.validation.constraints.Size;

import java.util.List;

/** 관심 분야 키워드 설정 요청 DTO */
public record KeywordsRequest(
        @Size(max = 10, message = "키워드는 최대 10개까지 등록할 수 있습니다") List<String> keywords
) {
}
