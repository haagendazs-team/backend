package com.haagendazs.infrastructure.swagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.exception.ErrorCode;
import com.haagendazs.domain.exception.NotificationErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

@Component
public class SwaggerApiErrorCustomizer implements OperationCustomizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        applySwaggerApi(operation, handlerMethod);
        applyErrors(operation, handlerMethod);
        return operation;
    }

    private void applySwaggerApi(Operation operation, HandlerMethod handlerMethod) {
        SwaggerApi swaggerApi = handlerMethod.getMethodAnnotation(SwaggerApi.class);
        if (swaggerApi == null) {
            return;
        }

        operation.setSummary(swaggerApi.summary());
        if (!swaggerApi.description().isBlank()) {
            operation.setDescription(swaggerApi.description());
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        String targetCode = swaggerApi.responseCode();
        if ("200".equals(targetCode)) {
            responses.computeIfAbsent("200", k -> new ApiResponse())
                    .setDescription(swaggerApi.responseDescription());
        } else {
            responses.remove("200");
            ApiResponse noContent = new ApiResponse();
            noContent.setDescription(swaggerApi.responseDescription());
            responses.put(targetCode, noContent);
        }
    }

    private void applyErrors(Operation operation, HandlerMethod handlerMethod) {
        List<ErrorCode> errors = collectErrors(handlerMethod);
        if (errors.isEmpty()) {
            return;
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        for (ErrorCode errorCode : errors) {
            String statusCode = String.valueOf(errorCode.getHttpStatus().value());
            ApiResponse apiResponse = responses.computeIfAbsent(statusCode, k -> new ApiResponse());
            if (apiResponse.getDescription() == null) {
                apiResponse.setDescription(errorCode.getMessage());
            }
            mergeErrorExample(apiResponse, errorCode, "");
        }
    }

    private List<ErrorCode> collectErrors(HandlerMethod handlerMethod) {
        List<ErrorCode> errors = new ArrayList<>();

        if (hasRequestBody(handlerMethod)) {
            errors.add(com.haagendazs.common.exception.CommonErrorCode.INVALID_INPUT);
        }

        SwaggerApi swaggerApi = handlerMethod.getMethodAnnotation(SwaggerApi.class);
        if (swaggerApi != null) {
            for (NotificationErrorCode code : swaggerApi.errors()) {
                errors.add(code);
            }
        }

        SwaggerApiError single = handlerMethod.getMethodAnnotation(SwaggerApiError.class);
        SwaggerApiErrors container = handlerMethod.getMethodAnnotation(SwaggerApiErrors.class);

        if (single != null) {
            if (single.errors().length > 0) {
                for (NotificationErrorCode code : single.errors()) {
                    errors.add(code);
                }
            } else {
                errors.add(single.value());
            }
        } else if (container != null) {
            for (SwaggerApiError error : container.value()) {
                if (error.errors().length > 0) {
                    for (NotificationErrorCode code : error.errors()) {
                        errors.add(code);
                    }
                } else {
                    errors.add(error.value());
                }
            }
        }

        return errors;
    }

    private boolean hasRequestBody(HandlerMethod handlerMethod) {
        for (Parameter parameter : handlerMethod.getMethod().getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                return true;
            }
        }
        return false;
    }

    private void mergeErrorExample(ApiResponse apiResponse, ErrorCode errorCode, String detail) {
        io.swagger.v3.oas.models.examples.Example example = new io.swagger.v3.oas.models.examples.Example();
        String message = (detail == null || detail.isBlank()) ? errorCode.getMessage() : detail;
        example.setValue(parseJson(
                "{\"code\":\"" + errorCode.getCode() + "\",\"message\":\"" + message + "\"}"
        ));

        if (apiResponse.getContent() == null) {
            Schema<String> schema = new Schema<>();
            schema.setName("ErrorResponse");
            MediaType mediaType = new MediaType();
            mediaType.setSchema(schema);
            mediaType.addExamples(errorCode.getCode(), example);
            Content content = new Content();
            content.addMediaType("application/json", mediaType);
            apiResponse.setContent(content);
        } else {
            MediaType mediaType = apiResponse.getContent().get("application/json");
            if (mediaType != null) {
                mediaType.addExamples(errorCode.getCode(), example);
            }
        }
    }

    private Object parseJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
