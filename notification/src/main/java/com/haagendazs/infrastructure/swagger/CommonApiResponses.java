package com.haagendazs.infrastructure.swagger;

import java.lang.annotation.*;

/**
 * 모든 API에 공통으로 붙는 마커 어노테이션.
 * 400 등 도메인 전용 에러는 @SwaggerApiError(ErrorCode.XXX)로 메서드에 직접 선언한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CommonApiResponses {
}
