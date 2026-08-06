package com.example.jobtracker.exception;

/** 회원가입 시 이미 존재하는 이메일로 요청한 경우 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("이미 사용 중인 이메일입니다: " + email);
    }
}
