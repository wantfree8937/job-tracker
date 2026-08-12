package com.example.jobtracker.exception;

/** 요청한 이력서 파일이 없거나 내 소유가 아닌 경우 */
public class ResumeFileNotFoundException extends RuntimeException {

    public ResumeFileNotFoundException() {
        super("이력서 파일을 찾을 수 없습니다");
    }
}
