package com.example.jobtracker.entity.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티 = DB 테이블(users)과 1:1로 매핑되는 클래스.
 * "user"는 PostgreSQL 예약어라 테이블명은 users로 지정한다.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt로 암호화된 값만 저장

    @Column(nullable = false)
    private String nickname;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 관심 분야 키워드, 콤마 구분 문자열로 저장 (예: "안드로이드,백엔드")
    private String keywords;

    // 이력서/포트폴리오 자유 형식 텍스트, AI 면접이 참고 (최대 5000자라 TEXT 타입 필요)
    @Column(columnDefinition = "TEXT")
    private String profileText;
}
