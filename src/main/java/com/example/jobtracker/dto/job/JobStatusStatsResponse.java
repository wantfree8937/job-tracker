package com.example.jobtracker.dto.job;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/** 상태별 공고 개수 집계 (대시보드용). JSON으로는 {"WISH": 3, "APPLIED": 1, ...} 형태로 평탄화된다. */
public record JobStatusStatsResponse(@JsonValue Map<String, Long> counts) {
}
