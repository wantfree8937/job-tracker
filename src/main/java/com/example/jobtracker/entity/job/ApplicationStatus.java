package com.example.jobtracker.entity.job;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 지원 상태: 지원 예정 → 지원함 → 면접 → 합격/불합격 */
public enum ApplicationStatus {
    WISH, APPLIED, INTERVIEW, OFFER, REJECTED;

    // 요청 값이 소문자여도 매핑되도록 대소문자 무시 처리
    @JsonCreator
    public static ApplicationStatus from(String value) {
        return ApplicationStatus.valueOf(value.toUpperCase());
    }
}
