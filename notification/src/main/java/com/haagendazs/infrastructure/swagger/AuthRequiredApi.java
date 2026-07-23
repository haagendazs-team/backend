package com.haagendazs.infrastructure.swagger;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.lang.annotation.*;

/**
 * JWT 인증이 필요한 API. 401/403 응답과 bearerAuth 보안 요구사항을 함께 선언한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SecurityRequirement(name = "bearerAuth")
@SwaggerApiResponse.Unauthorized
public @interface AuthRequiredApi {
}
