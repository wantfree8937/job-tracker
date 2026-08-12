package com.example.jobtracker.dto.auth;

/** 이력서 파일 저장/조회 응답 DTO (id + 파일명 + 타입 + 추출된 텍스트, 목록 조회 시 text는 null) */
public record ProfileFileResponse(Long id, String fileName, String fileType, String text) {
}
