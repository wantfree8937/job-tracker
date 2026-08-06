package com.example.jobtracker.dto.job;

import com.example.jobtracker.entity.job.ApplicationStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 공고 부분 수정 요청 DTO. 요청에 포함된(null이 아닌) 필드만 반영한다. */
public record JobPostingUpdateRequest(
        String companyName,
        String position,
        @Size(max = 500) String link,
        LocalDate deadline,
        ApplicationStatus status,
        @Size(max = 2000) String memo
) {
}
