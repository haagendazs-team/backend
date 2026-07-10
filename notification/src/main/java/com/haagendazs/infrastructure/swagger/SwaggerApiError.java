package com.haagendazs.infrastructure.swagger;

import com.haagendazs.domain.exception.NotificationErrorCode;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(SwaggerApiErrors.class)
public @interface SwaggerApiError {
    NotificationErrorCode[] errors() default {};
    NotificationErrorCode value();
    String detail() default "";
}
