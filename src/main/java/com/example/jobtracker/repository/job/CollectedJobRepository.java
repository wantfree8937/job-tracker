package com.example.jobtracker.repository.job;

import com.example.jobtracker.entity.job.CollectedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CollectedJobRepository extends JpaRepository<CollectedJob, Long> {

    boolean existsByJobKey(String jobKey);

    Optional<CollectedJob> findByJobKey(String jobKey);

    @Modifying
    @Query("DELETE FROM CollectedJob c WHERE c.deadline IS NOT NULL AND c.deadline < :cutoff")
    int deleteByDeadlineBeforeAndDeadlineIsNotNull(@Param("cutoff") LocalDate cutoff);

    // source, keyword는 null이면 조건을 무시한다 (필터 미적용)
    @Query("""
            SELECT c FROM CollectedJob c
            WHERE (:source IS NULL OR c.source = :source)
              AND (:keyword IS NULL
                   OR LOWER(c.company) LIKE :keyword
                   OR LOWER(c.title) LIKE :keyword)
            ORDER BY c.createdAt DESC
            """)
    List<CollectedJob> search(@Param("source") String source, @Param("keyword") String keyword);

    @Query("""
            SELECT c FROM CollectedJob c
            WHERE (:source IS NULL OR c.source = :source)
              AND (:keyword IS NULL OR LOWER(c.company) LIKE :keyword)
            ORDER BY c.createdAt DESC
            """)
    List<CollectedJob> searchByCompany(@Param("source") String source, @Param("keyword") String keyword);

    @Query("""
            SELECT c FROM CollectedJob c
            WHERE (:source IS NULL OR c.source = :source)
              AND (:keyword IS NULL OR LOWER(c.title) LIKE :keyword)
            ORDER BY c.createdAt DESC
            """)
    List<CollectedJob> searchByTitle(@Param("source") String source, @Param("keyword") String keyword);
}
