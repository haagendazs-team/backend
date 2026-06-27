package com.haagendazs.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InfrastructureErrorCode implements ErrorCode {

    REDIS_STREAMS_INIT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INFRA001", "Redis Streams 초기화에 실패했습니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "INFRA002", "이메일 발송에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
