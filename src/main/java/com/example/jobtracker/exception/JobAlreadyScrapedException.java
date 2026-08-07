package com.example.jobtracker.exception;

/** 이미 스크랩(내 공고로 복사)한 수집 공고를 다시 스크랩하려는 경우 */
public class JobAlreadyScrapedException extends RuntimeException {

    public JobAlreadyScrapedException() {
        super("이미 스크랩한 공고입니다");
    }
}
