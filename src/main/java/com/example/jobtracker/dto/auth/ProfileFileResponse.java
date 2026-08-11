package com.example.jobtracker.dto.auth;

/** 이력서 파일 저장/조회 응답 DTO (파일명 + 타입 + 추출된 텍스트, 조회 시 text는 null) */
public record ProfileFileResponse(String fileName, String fileType, String text) {
}
