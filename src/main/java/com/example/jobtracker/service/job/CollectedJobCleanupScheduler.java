package com.example.jobtracker.service.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 마감 지난 수집 공고를 매일 새벽 자동 삭제 (사용자 데이터인 JobPosting은 건드리지 않음)
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectedJobCleanupScheduler {

    private final CollectedJobService collectedJobService;

    @Value("${job-tracker.retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredJobs() {
        int deleted = collectedJobService.deleteExpired(retentionDays);
        if (deleted > 0) {
            log.info("마감 지난 수집 공고 {}건 자동 삭제", deleted);
        }
    }
}
