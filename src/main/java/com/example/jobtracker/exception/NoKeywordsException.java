package com.example.jobtracker.exception;

/** 크롤링할 키워드가 없는 경우 (요청 키워드도, 로그인 사용자의 관심 키워드도 없음) */
public class NoKeywordsException extends RuntimeException {

    public NoKeywordsException() {
        super("관심 분야 키워드가 없습니다");
    }
}
