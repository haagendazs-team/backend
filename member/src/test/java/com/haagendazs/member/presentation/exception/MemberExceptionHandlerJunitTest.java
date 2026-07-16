package com.haagendazs.member.presentation.exception;

import com.haagendazs.common.exception.CommonErrorCode;
import com.haagendazs.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class MemberExceptionHandlerJunitTest {

    private final MemberExceptionHandler handler = new MemberExceptionHandler();

    @Test
    @DisplayName("[Happy] Validation 실패 시 첫 번째 필드 에러 메시지를 반환한다")
    void handleValidationException_withFieldError_returnsFieldMessage() {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "signupRequest");
        bindingResult.addError(new FieldError("signupRequest", "email", "이메일 형식이 올바르지 않습니다."));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("이메일 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("[Happy] Validation 필드 에러가 없으면 기본 INVALID_INPUT 메시지를 반환한다")
    void handleValidationException_withoutFieldError_returnsDefaultMessage() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(CommonErrorCode.INVALID_INPUT.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(CommonErrorCode.INVALID_INPUT.getMessage());
    }
}
