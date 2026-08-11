package com.example.jobtracker.dto.auth;

/** 이력서 원본 파일 다운로드용 데이터 (JSON 응답이 아닌 파일 응답 구성에 사용) */
public record ResumeFileData(String fileName, String contentType, byte[] data) {
}
