package com.example.jobtracker.dto.job;

import java.util.List;

/** 공고 크롤링 요청 DTO — keywords가 없으면 로그인 사용자의 관심 키워드를 사용한다 */
public record CrawlRequest(List<String> keywords) {
}
