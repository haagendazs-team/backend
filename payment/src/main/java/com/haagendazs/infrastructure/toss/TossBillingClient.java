package com.haagendazs.infrastructure.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.application.dto.TossBillingKeyIssueRequest;
import com.haagendazs.application.dto.TossBillingKeyIssueResponse;
import com.haagendazs.application.dto.TossBillingPaymentRequest;
import com.haagendazs.application.dto.TossBillingPaymentResponse;
import com.haagendazs.application.dto.TossCardBillingKeyIssueRequest;
import com.haagendazs.application.dto.TossErrorResponse;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class TossBillingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // billing auth 성공 후 받은 authKey로 Toss billingKey를 발급합니다.
    public TossBillingKeyIssueResponse issueBillingKey(
            String authKey,
            String customerKey
    ) {
        TossBillingKeyIssueRequest request = new TossBillingKeyIssueRequest(
                authKey,
                customerKey
        );

        try {
            return restClient.post()
                    .uri("/v1/billing/authorizations/issue")
                    .body(request)
                    .retrieve()
                    .body(TossBillingKeyIssueResponse.class);
        } catch (RestClientResponseException e) {
            throw toTossPaymentException(e);
        } catch (ResourceAccessException e) {
            throw toTossTimeoutException(e);
        }
    }

    // 카드 정보를 직접 전달해 billingKey를 발급합니다. 카드 정보는 저장하지 말고 호출 직후 폐기해야 합니다.
    public TossBillingKeyIssueResponse issueBillingKeyWithCard(
            String customerKey,
            String cardNumber,
            String cardExpirationYear,
            String cardExpirationMonth,
            String customerIdentityNumber,
            String cardPassword,
            String customerName,
            String customerEmail
    ) {
        TossCardBillingKeyIssueRequest request = new TossCardBillingKeyIssueRequest(
                customerKey,
                cardNumber,
                cardExpirationYear,
                cardExpirationMonth,
                customerIdentityNumber,
                cardPassword,
                customerName,
                customerEmail
        );

        try {
            return restClient.post()
                    .uri("/v1/billing/authorizations/card")
                    .body(request)
                    .retrieve()
                    .body(TossBillingKeyIssueResponse.class);
        } catch (RestClientResponseException e) {
            throw toTossPaymentException(e);
        } catch (ResourceAccessException e) {
            throw toTossTimeoutException(e);
        }
    }

    // 발급된 billingKey로 자동결제를 실행합니다. 멱등성 키는 같은 주문 재시도 중복 결제를 막기 위한 헤더입니다.
    public TossBillingPaymentResponse payWithBillingKey(
            String billingKey,
            String customerKey,
            String orderId, //주문번호
            Long amount,
            String orderName,
            String idempotencyKey
    ) {
        TossBillingPaymentRequest request = new TossBillingPaymentRequest(
                customerKey,
                orderId,
                amount,
                orderName
        );

        try {
            return restClient.post()
                    .uri("/v1/billing/{billingKey}", billingKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(TossBillingPaymentResponse.class);
        } catch (RestClientResponseException e) {
            throw toTossPaymentException(e);
        } catch (ResourceAccessException e) {
            throw toTossTimeoutException(e);
        }
    }

    // 주문번호로 Toss 결제 상태를 조회합니다. 중복 주문번호나 타임아웃 후 보정 흐름에서 사용합니다.
    public TossBillingPaymentResponse getPaymentByOrderId(String orderId) {
        try {
            return restClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .retrieve()
                    .body(TossBillingPaymentResponse.class);
        } catch (RestClientResponseException e) {
            throw toTossPaymentException(e);
        } catch (ResourceAccessException e) {
            throw toTossTimeoutException(e);
        }
    }

    // Toss에 저장된 billingKey를 삭제합니다. 로컬 결제수단 삭제와 함께 호출됩니다.
    public void deleteBillingKey(String billingKey) {
        try {
            restClient.delete()
                    .uri("/v1/billing/{billingKey}", billingKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw toTossPaymentException(e);
        } catch (ResourceAccessException e) {
            throw toTossTimeoutException(e);
        }
    }

    // Toss 원본 오류를 보존하면서 사용자 응답용 PaymentErrorCode로 변환된 예외를 생성합니다.
    private TossPaymentException toTossPaymentException(RestClientResponseException exception) {
        TossErrorResponse errorResponse = parseErrorResponse(exception);

        return new TossPaymentException(
                exception.getStatusCode(),
                errorResponse.code(),
                errorResponse.message()
        );
    }

    // 연결 실패는 재시도 대상으로, 응답 대기 초과는 결제 결과 불확실 상태로 보고 조회 보정 대상으로 분류합니다.
    private TossPaymentException toTossTimeoutException(ResourceAccessException exception) {
        if (isReadTimeout(exception)) {
            return new TossPaymentException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "TOSS_READ_TIMEOUT",
                    "토스페이먼츠 응답 대기 시간이 초과되었습니다."
            );
        }

        return new TossPaymentException(
                HttpStatus.GATEWAY_TIMEOUT,
                "TOSS_CONNECT_TIMEOUT",
                "토스페이먼츠 연결 시간이 초과되었습니다."
        );
    }

    private boolean isReadTimeout(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof SocketTimeoutException
                    && current.getMessage() != null
                    && current.getMessage().toLowerCase().contains("read")) {
                return true;
            }
            if (current instanceof ConnectException) {
                return false;
            }
            current = current.getCause();
        }

        return false;
    }

    // Toss 에러 body는 보통 {code, message}입니다. 파싱 불가 응답도 HTTP 상태와 원문을 fallback으로 남깁니다.
    private TossErrorResponse parseErrorResponse(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();

        if (responseBody == null || responseBody.isBlank()) {
            return new TossErrorResponse(
                    "HTTP_" + exception.getStatusCode().value(),
                    exception.getMessage()
            );
        }

        try {
            return objectMapper.readValue(responseBody, TossErrorResponse.class);
        } catch (JsonProcessingException e) {
            return new TossErrorResponse(
                    "HTTP_" + exception.getStatusCode().value(),
                    responseBody
            );
        }
    }
}
