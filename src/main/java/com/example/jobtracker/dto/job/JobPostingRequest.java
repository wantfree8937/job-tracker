package com.example.jobtracker.dto.job;

import com.example.jobtracker.entity.job.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 공고 생성 요청 DTO */
public record JobPostingRequest(
        @NotBlank String companyName,
        @NotBlank String position,
        @Size(max = 500) String link,
        LocalDate deadline,
        ApplicationStatus status,
        @Size(max = 2000) String memo
) {
}
