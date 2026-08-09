package com.example.jobtracker.dto.job;

/** 경력 정보 일괄 백필 결과 DTO */
public record BackfillExperienceResult(int processed, int filled) {
}
