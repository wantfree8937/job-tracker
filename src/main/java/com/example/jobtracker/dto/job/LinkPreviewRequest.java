package com.example.jobtracker.dto.job;

import jakarta.validation.constraints.NotBlank;

/** 링크 프리뷰 요청 DTO */
public record LinkPreviewRequest(@NotBlank String link) {
}
