package com.example.jobtracker.exception;

import java.time.LocalDateTime;

/** 에러 응답 공통 형식 */
public record ErrorResponse(String message, LocalDateTime timestamp) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, LocalDateTime.now());
    }
}
