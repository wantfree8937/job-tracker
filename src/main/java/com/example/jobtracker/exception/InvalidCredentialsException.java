package com.example.jobtracker.exception;

/** 로그인 시 이메일이 존재하지 않거나 비밀번호가 일치하지 않는 경우 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다");
    }
}
