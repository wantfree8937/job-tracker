package com.example.jobtracker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import lombok.extern.slf4j.Slf4j;

/** 컨트롤러에서 발생한 예외를 한 곳에서 공통 형식(ErrorResponse)으로 응답한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // @Valid 검증 실패 (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(message));
    }

    // 이메일 중복 (409)
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(e.getMessage()));
    }

    // 로그인 실패: 이메일 없음/비밀번호 불일치 (401)
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(e.getMessage()));
    }

    // 공고를 찾을 수 없거나 내 소유가 아님 (404)
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(JobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(e.getMessage()));
    }

    // 이미 스크랩한 수집 공고 (409)
    @ExceptionHandler(JobAlreadyScrapedException.class)
    public ResponseEntity<ErrorResponse> handleJobAlreadyScraped(JobAlreadyScrapedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(e.getMessage()));
    }

    // 링크 프리뷰 실패: 접속 불가/파싱 실패 (422)
    @ExceptionHandler(LinkPreviewFailedException.class)
    public ResponseEntity<ErrorResponse> handleLinkPreviewFailed(LinkPreviewFailedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(e.getMessage()));
    }

    // 원티드/잡코리아 검색 모두 실패 (502)
    @ExceptionHandler(JobSearchFailedException.class)
    public ResponseEntity<ErrorResponse> handleJobSearchFailed(JobSearchFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of(e.getMessage()));
    }

    // 크롤링할 키워드가 없음 (400)
    @ExceptionHandler(NoKeywordsException.class)
    public ResponseEntity<ErrorResponse> handleNoKeywords(NoKeywordsException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(e.getMessage()));
    }

    // 딥시크 API 호출/응답 파싱 실패 (502)
    @ExceptionHandler(AiRequestFailedException.class)
    public ResponseEntity<ErrorResponse> handleAiRequestFailed(AiRequestFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of(e.getMessage()));
    }

    // 이력서 URL/PDF 텍스트 추출 실패, 이력서 파일 개수 초과 (400)
    @ExceptionHandler(ProfileParseFailedException.class)
    public ResponseEntity<ErrorResponse> handleProfileParseFailed(ProfileParseFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(e.getMessage()));
    }

    // 이력서 파일을 찾을 수 없거나 내 소유가 아님 (404)
    @ExceptionHandler(ResumeFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResumeFileNotFound(ResumeFileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(e.getMessage()));
    }

    // 업로드 파일이 크기 제한을 초과함 (400)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of("파일 크기는 최대 10MB까지 업로드할 수 있습니다"));
    }

    // 업로드 파일 파트가 누락됨 (400)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of("파일이 필요합니다"));
    }

    // 잘못된 status 쿼리 파라미터 값 (400)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of("유효하지 않은 요청 값입니다"));
    }

    // 요청 본문 JSON 파싱 실패 (예: 잘못된 status 값) (400)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of("요청 본문을 읽을 수 없습니다"));
    }

    // 그 외 서버 오류 (500) — 스택 트레이스를 로그에 남겨야 원인 추적 가능
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("예상치 못한 서버 오류 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("서버 내부 오류가 발생했습니다"));
    }
}
