package com.example.jobtracker.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 단위 테스트.
 * 모든 예외가 공통 형식(ErrorResponse) + 올바른 HTTP 상태 코드로 응답되는지 확인한다.
 * (MockMvc 통합 테스트에서 놓치기 쉬운 400/500 케이스까지 직접 커버)
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 검증_실패는_400과_필드_메시지를_반환한다() {
        // given: @Valid 검증 실패 상황 (fieldErrors에 실패한 필드 정보가 담김)
        FieldError fieldError = new FieldError("signUpRequest", "email", "올바른 이메일 형식이 아닙니다");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // when
        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);

        // then: 400 + 어떤 필드가 왜 실패했는지 메시지에 포함
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("email").contains("올바른 이메일 형식이 아닙니다");
    }

    @Test
    void 이메일_중복은_409를_반환한다() {
        // when
        ResponseEntity<ErrorResponse> resp =
                handler.handleEmailAlreadyExists(new EmailAlreadyExistsException("test@example.com"));

        // then: 409 + 중복 이메일 안내
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("test@example.com");
    }

    @Test
    void 로그인_실패는_401을_반환한다() {
        // when
        ResponseEntity<ErrorResponse> resp = handler.handleInvalidCredentials(new InvalidCredentialsException());

        // then: 401 + 로그인 실패 안내
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).isNotBlank();
    }

    @Test
    void 링크_프리뷰_실패는_422를_반환한다() {
        // when
        ResponseEntity<ErrorResponse> resp = handler.handleLinkPreviewFailed(new LinkPreviewFailedException());

        // then: 422 + 프리뷰 실패 안내
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).isNotBlank();
    }

    @Test
    void 공고_검색_모두_실패는_502를_반환한다() {
        // when
        ResponseEntity<ErrorResponse> resp = handler.handleJobSearchFailed(new JobSearchFailedException());

        // then: 502 + 검색 실패 안내
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).isNotBlank();
    }

    @Test
    void 요청_본문_파싱_실패는_400을_반환한다() {
        // given: 잘못된 JSON 본문으로 인한 파싱 실패 상황
        org.springframework.http.converter.HttpMessageNotReadableException ex =
                mock(org.springframework.http.converter.HttpMessageNotReadableException.class);

        // when
        ResponseEntity<ErrorResponse> resp = handler.handleNotReadable(ex);

        // then: 400 + 본문을 읽을 수 없다는 안내
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).isNotBlank();
    }

    @Test
    void 예상치_못한_예외는_500을_반환한다() {
        // when: 런타임 예외가 GlobalExceptionHandler까지 전달된 상황
        ResponseEntity<ErrorResponse> resp = handler.handleException(new RuntimeException("DB 연결 실패"));

        // then: 500 + 사용자에게는 내부 오류만 노출 (원인은 로그에만 기록)
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).contains("서버 내부 오류");
    }
}
