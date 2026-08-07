package com.example.jobtracker.dto.job;

import com.example.jobtracker.entity.job.CollectedJob;

import java.time.LocalDateTime;

/** 수집 공고 응답 DTO */
public record CollectedJobResponse(
        Long id,
        String company,
        String title,
        String url,
        String source,
        String jobKey,
        LocalDateTime createdAt,
        boolean scrapedByMe
) {
    public static CollectedJobResponse from(CollectedJob job, boolean scrapedByMe) {
        return new CollectedJobResponse(
                job.getId(),
                job.getCompany(),
                job.getTitle(),
                job.getUrl(),
                job.getSource(),
                job.getJobKey(),
                job.getCreatedAt(),
                scrapedByMe
        );
    }
}
