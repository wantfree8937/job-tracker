package com.example.jobtracker.dto.job;

/** 크롤링 결과 DTO (웹/앱이 기대하는 필드명에 맞춤) */
public record CrawlResult(int loaded, int skipped) {
}
