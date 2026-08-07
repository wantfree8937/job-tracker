package com.example.jobtracker.exception;

/** 원티드/잡코리아 검색이 모두 실패한 경우 */
public class JobSearchFailedException extends RuntimeException {

    public JobSearchFailedException() {
        super("공고 검색에 실패했습니다");
    }
}
