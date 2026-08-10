package com.example.jobtracker.exception;

/** 딥시크 API 호출 또는 응답 파싱 실패 */
public class AiRequestFailedException extends RuntimeException {

    public AiRequestFailedException() {
        super("면접 질문 생성에 실패했습니다");
    }
}
