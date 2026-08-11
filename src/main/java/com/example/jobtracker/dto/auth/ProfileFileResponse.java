package com.example.jobtracker.dto.auth;

/** 이력서 파일 저장 응답 DTO (저장한 파일명 + 추출된 텍스트) */
public record ProfileFileResponse(String fileName, String text) {
}
