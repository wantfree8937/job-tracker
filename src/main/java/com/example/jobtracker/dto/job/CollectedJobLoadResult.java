package com.example.jobtracker.dto.job;

/** 수집 공고 파일 로드 결과 DTO */
public record CollectedJobLoadResult(int loaded, int skipped) {
}
