package com.haagendazs.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.common.exception.ErrorCode;
import com.haagendazs.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Order(-1)
@Component
@RequiredArgsConstructor
public class GlobalGatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ApiResponse<Void> body = resolveBody(ex);
        HttpStatus status = resolveStatus(ex);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return writeResponse(exchange, body);
    }

    private ApiResponse<Void> resolveBody(Throwable ex) {
        if (ex instanceof BusinessException businessException) {
            return ApiResponse.fail(businessException.getErrorCode());
        }
        if (ex instanceof ResponseStatusException responseStatusException) {
            String reason = responseStatusException.getReason();
            return reason != null
                    ? ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR, reason)
                    : ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException.getErrorCode().getHttpStatus();
        }
        if (ex instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Mono<Void> writeResponse(ServerWebExchange exchange, ApiResponse<Void> body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }
    }
}
