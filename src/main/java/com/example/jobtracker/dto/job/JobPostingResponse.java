package com.example.jobtracker.dto.job;

import com.example.jobtracker.entity.job.JobPosting;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 공고 응답 DTO. userId는 프론트에 불필요하므로 제외한다. */
public record JobPostingResponse(
        Long id,
        String companyName,
        String position,
        String link,
        LocalDate deadline,
        String status,
        String memo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String region,
        String experience,
        String industry,
        String source
) {
    public static JobPostingResponse from(JobPosting job) {
        return new JobPostingResponse(
                job.getId(),
                job.getCompanyName(),
                job.getPosition(),
                job.getLink(),
                job.getDeadline(),
                job.getStatus().name(),
                job.getMemo(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getRegion(),
                job.getExperience(),
                job.getIndustry(),
                job.getSource()
        );
    }
}
