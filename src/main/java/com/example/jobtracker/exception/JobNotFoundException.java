package com.example.jobtracker.exception;

/** 요청한 공고가 없거나 내 소유가 아닌 경우 (보안상 존재 여부도 숨긴다) */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException() {
        super("공고를 찾을 수 없습니다");
    }
}
