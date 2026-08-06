package com.example.jobtracker.repository.job;

import com.example.jobtracker.entity.job.ApplicationStatus;
import com.example.jobtracker.entity.job.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Optional<JobPosting> findByIdAndUserId(Long id, Long userId);

    // status, keyword는 null이면 조건을 무시한다 (필터 미적용)
    @Query("""
            SELECT j FROM JobPosting j
            WHERE j.userId = :userId
              AND (:status IS NULL OR j.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(j.companyName) LIKE :keyword
                   OR LOWER(j.position) LIKE :keyword)
            ORDER BY j.updatedAt DESC
            """)
    List<JobPosting> search(@Param("userId") Long userId,
                             @Param("status") ApplicationStatus status,
                             @Param("keyword") String keyword);

    List<JobPosting> findByUserId(Long userId);
}
