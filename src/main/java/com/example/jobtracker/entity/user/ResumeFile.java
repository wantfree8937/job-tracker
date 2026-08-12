package com.example.jobtracker.entity.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** 사용자별 이력서 원본 파일 (최대 3개, User와 1:N) — AI 면접이 참고, 다운로드/재확인용 */
@Getter
@Setter
@Entity
@Table(name = "resume_files")
public class ResumeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(columnDefinition = "TEXT")
    private String fileName;

    @Column(columnDefinition = "TEXT")
    private String fileType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] content;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
