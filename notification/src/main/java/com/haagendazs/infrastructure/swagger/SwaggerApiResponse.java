package com.haagendazs.infrastructure.swagger;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.*;

/**
 * 도메인 에러와 무관한 공통 응답 단축 어노테이션.
 * 도메인 전용 에러는 @SwaggerApiError(ErrorCode.XXX)를 사용한다.
 */
public final class SwaggerApiResponse {

    private SwaggerApiResponse() {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(responseCode = "204", description = "성공 (본문 없음)")
    public @interface NoContent {}

    @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(schema = @Schema(implementation = ErrorResponseSchema.class))
    )
    public @interface Unauthorized {}
}
