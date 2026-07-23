package com.haagendazs.infrastructure.swagger;

import com.haagendazs.domain.exception.NotificationErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.*;

/**
 * @Operation + 200 응답을 하나로 축약한 어노테이션.
 * 성공 응답 스키마는 메서드 반환 타입에서 Springdoc이 자동 생성한다.
 * 204 등 본문 없는 응답은 responseCode를 지정한다.
 * errors로 에러 응답을 인라인으로 선언할 수 있다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Operation(summary = "")
@ApiResponses(@ApiResponse(responseCode = "200", description = ""))
public @interface SwaggerApi {
    String summary();
    String description() default "";
    String responseCode() default "200";
    String responseDescription() default "성공";
    NotificationErrorCode[] errors() default {};
}
