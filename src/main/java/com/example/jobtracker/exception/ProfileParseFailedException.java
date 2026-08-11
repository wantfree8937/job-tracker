package com.example.jobtracker.exception;

/** 이력서 URL/PDF에서 텍스트를 추출하지 못한 경우 */
public class ProfileParseFailedException extends RuntimeException {

    public ProfileParseFailedException(String message) {
        super(message);
    }
}
