package com.example.jobtracker.repository.user;

import com.example.jobtracker.entity.user.ResumeFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeFileRepository extends JpaRepository<ResumeFile, Long> {

    List<ResumeFile> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<ResumeFile> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long deleteByIdAndUserId(Long id, Long userId);
}
