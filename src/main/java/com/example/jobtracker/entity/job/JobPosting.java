package com.example.jobtracker.entity.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 채용 공고 스크랩 + 지원 현황 엔티티 = job_postings 테이블과 매핑 */
@Getter
@Setter
@Entity
@Table(name = "job_postings", indexes = @Index(columnList = "userId"))
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String position;

    private String link;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.WISH;

    private String memo;

    // 지역·경력·업종 (스크랩 시 CollectedJob에서 복사, 직접 등록한 공고는 null)
    private String region;
    private String experience;
    private String industry;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
