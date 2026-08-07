package com.example.jobtracker.entity.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 외부 크롤러가 수집한 채용 공고 (모든 사용자 공유) = collected_jobs 테이블과 매핑 */
@Getter
@Setter
@Entity
@Table(name = "collected_jobs")
public class CollectedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 출처:원본id 형태 (예: "잡코리아:12345") — 중복 수집 방지용
    @Column(nullable = false, unique = true)
    private String jobKey;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String source;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
