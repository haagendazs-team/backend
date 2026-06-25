package com.haagendazs.common.response;

import com.haagendazs.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.code = "SUCCESS";
        response.message = "요청이 성공했습니다.";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = errorCode.getCode();
        response.message = errorCode.getMessage();
        return response;
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = errorCode.getCode();
        response.message = message;
        return response;
    }
}
