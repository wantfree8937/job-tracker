package com.example.jobtracker.dto.auth;

import jakarta.validation.constraints.Size;

/** 이력서/포트폴리오 저장 요청 DTO */
public record ProfileRequest(
        @Size(max = 5000, message = "이력서/포트폴리오는 최대 5000자까지 입력할 수 있습니다") String profileText
) {
}
