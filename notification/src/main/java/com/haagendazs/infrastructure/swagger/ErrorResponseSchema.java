package com.haagendazs.infrastructure.swagger;

import io.swagger.v3.oas.annotations.media.Schema;

/** Swagger 에러 응답 스키마 전용 모델. 실제 사용 금지. */
@Schema(name = "ErrorResponse", description = "공통 에러 응답")
public record ErrorResponseSchema(
        @Schema(description = "에러 코드", example = "INVALID_INPUT") String code,
        @Schema(description = "에러 메시지", example = "입력값이 올바르지 않습니다.") String message,
        @Schema(description = "상세 정보", nullable = true, example = "field: 유효하지 않은 값입니다.") String detail
) {}
