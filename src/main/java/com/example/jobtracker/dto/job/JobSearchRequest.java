package com.example.jobtracker.dto.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 자유 키워드 검색 요청 DTO */
public record JobSearchRequest(
        @NotBlank(message = "키워드를 입력해주세요")
        @Size(min = 2, max = 20, message = "키워드는 2~20자로 입력해주세요")
        @Pattern(regexp = "^[가-힣a-zA-Z0-9\\s]+$", message = "키워드에 특수문자를 사용할 수 없습니다")
        String keyword
) {
}
