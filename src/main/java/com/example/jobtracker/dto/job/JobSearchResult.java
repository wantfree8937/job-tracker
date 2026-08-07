package com.example.jobtracker.dto.job;

/** 자유 키워드 검색 결과 DTO */
public record JobSearchResult(String keyword, int collected, int skipped) {
}
