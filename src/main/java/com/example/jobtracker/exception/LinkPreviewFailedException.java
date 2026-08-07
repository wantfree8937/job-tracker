package com.example.jobtracker.exception;

/** 링크 접속 실패 또는 메타데이터 파싱 실패로 프리뷰를 만들 수 없는 경우 */
public class LinkPreviewFailedException extends RuntimeException {

    public LinkPreviewFailedException() {
        super("링크 정보를 가져올 수 없습니다");
    }
}
