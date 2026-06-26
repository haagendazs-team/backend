package com.haagendazs.common.exception;

import lombok.Getter;

@Getter
public class InfrastructureException extends RuntimeException {

    private final InfrastructureErrorCode errorCode;

    public InfrastructureException(InfrastructureErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public InfrastructureException(InfrastructureErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
