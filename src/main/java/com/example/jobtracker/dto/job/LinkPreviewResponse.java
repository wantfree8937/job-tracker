package com.example.jobtracker.dto.job;

/** 링크 프리뷰 응답 DTO. 못 찾은 필드는 null */
public record LinkPreviewResponse(String company, String position, String memo) {
}
