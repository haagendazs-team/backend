package com.haagendazs.payment.payment.service.tools;

import com.haagendazs.TestPaymentApplication;
import com.haagendazs.domain.model.Billing;
import com.haagendazs.domain.model.BillingStatus;
import com.haagendazs.domain.model.CardCompany;
import com.haagendazs.domain.model.CardType;
import com.haagendazs.domain.model.OwnerType;
import com.haagendazs.domain.repository.BillingRepository;
import com.haagendazs.application.service.BillingMethodService;
import com.haagendazs.application.service.BillingPaymentService;
import com.haagendazs.application.service.BillingPaymentTransactionService;
import com.haagendazs.application.service.BillingPaymentTransactionService.BillingPaymentPreparation;
import com.haagendazs.application.dto.BillingPaymentRequest;
import com.haagendazs.application.dto.TossBillingKeyIssueResponse;
import com.haagendazs.application.dto.TossBillingPaymentResponse;
import com.haagendazs.infrastructure.toss.TossBillingClient;

import com.haagendazs.infrastructure.toss.TossPaymentException;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(classes = TestPaymentApplication.class)
@ActiveProfiles("test")
@Tag("external")
// Run: tossRealApiTest, tossServiceRealApiTest, tossCardIssueRealApiTest 중 메서드 태그에 맞는 태스크
class TossBillingClientRealApiTest {

    private static final Long TEST_MEMBER_ID = 1L;
    private static final Long TEST_BILLING_ID = 1L;
    private static final String DEFAULT_TEST_CUSTOMER_KEY = "customer-key-seed-1";
    private static final String DEFAULT_TEST_BILLING_KEY = "l9GjF8M-fcwHWZ0_vB3c5JvVJj5KNNNRkXHBu_zBW2g=";
    private static final String DEFAULT_TEST_CARD_NUMBER = "4703491122223311";
    private static final String DEFAULT_TEST_CARD_EXPIRATION_YEAR = "26";
    private static final String DEFAULT_TEST_CARD_EXPIRATION_MONTH = "08";
    private static final String DEFAULT_TEST_CARD_PASSWORD = "1234";
    private static final String DEFAULT_TEST_CUSTOMER_IDENTITY_NUMBER = "991122";

    @Autowired
    private TossBillingClient tossBillingClient;

    @Autowired
    private BillingPaymentService billingPaymentService;

    @Autowired
    private BillingMethodService billingMethodService;

    @Autowired
    private BillingRepository billingRepository;

    @MockitoBean
    private BillingPaymentTransactionService billingPaymentTransactionService;

    @Test
    // Run: tossRealApiTest 또는 ./gradlew :payment:tossRealApiTest
    @DisplayName("실제 Toss 서버 - 존재하지 않는 주문번호 조회 시 Toss 에러 응답을 파싱한다")
    void getPaymentByOrderId_notFound_usesRealTossServer() {
        String orderId = "missing-" + UUID.randomUUID();

        assertThatThrownBy(() -> tossBillingClient.getPaymentByOrderId(orderId))
                .isInstanceOfSatisfying(TossPaymentException.class, exception -> {
                    TossPaymentException tossException = (TossPaymentException) exception;
                    assertThat(tossException.getHttpStatusCode()).isInstanceOf(HttpStatusCode.class);
                    assertThat(tossException.getTossCode()).isNotBlank();
                    assertThat(tossException.getTossMessage()).isNotBlank();
                });
    }

    @Test
    // Run: tossRealApiTest 또는 ./gradlew :payment:tossRealApiTest
    @DisplayName("실제 Toss 서버 - 테스트 billingKey가 있으면 자동결제와 주문 조회를 수행한다")
    void payWithBillingKey_thenGetPaymentByOrderId_usesRealTossServer() {
        BillingCredential billingCredential = billingCredential();
        assumeTrue(billingCredential.hasRequiredValues(),
                "TOSS_TEST_BILLING_KEY/TOSS_TEST_CUSTOMER_KEY or TOSS_TEST_CARD_* env values are required.");

        String orderId = "toss-it-" + UUID.randomUUID();
        Long amount = 100L;
        String orderName = "Toss real API integration test";
        String idempotencyKey = UUID.randomUUID().toString();

        TossBillingPaymentResponse paymentResponse = tossBillingClient.payWithBillingKey(
                billingCredential.billingKey(),
                billingCredential.customerKey(),
                orderId,
                amount,
                orderName,
                idempotencyKey
        );

        assertThat(paymentResponse.orderId()).isEqualTo(orderId);
        assertThat(paymentResponse.status()).isEqualTo("DONE");
        assertThat(paymentResponse.totalAmount()).isEqualTo(amount);
        assertThat(paymentResponse.paymentKey()).isNotBlank();

        TossBillingPaymentResponse queriedResponse = tossBillingClient.getPaymentByOrderId(orderId);
        assertThat(queriedResponse.paymentKey()).isEqualTo(paymentResponse.paymentKey());
        assertThat(queriedResponse.orderId()).isEqualTo(orderId);
        assertThat(queriedResponse.totalAmount()).isEqualTo(amount);
    }

    @Test
    @Tag("service-real-api")
    // Run: tossServiceRealApiTest 또는 ./gradlew :payment:tossServiceRealApiTest
    @DisplayName("서비스 레벨 실제 Toss 서버 - 등록된 billingKey로 결제를 수행한다")
    void payWithRegisteredBillingMethod_usesRealTossServer() {
        BillingPaymentPreparation preparation = billingPaymentPreparation();

        when(billingPaymentTransactionService.prepareBillingPayment(
                eq(TEST_MEMBER_ID),
                eq(preparation.orderNo()),
                eq(null)
        )).thenReturn(preparation);

        billingPaymentService.payWithRegisteredBillingMethod(
                TEST_MEMBER_ID,
                new BillingPaymentRequest(preparation.orderNo())
        );

        ArgumentCaptor<TossBillingPaymentResponse> responseCaptor =
                ArgumentCaptor.forClass(TossBillingPaymentResponse.class);
        verify(billingPaymentTransactionService).completeBillingPayment(
                eq(TEST_MEMBER_ID),
                eq(preparation),
                responseCaptor.capture()
        );

        TossBillingPaymentResponse paymentResponse = responseCaptor.getValue();
        assertThat(paymentResponse.orderId()).isEqualTo(preparation.orderNo());
        assertThat(paymentResponse.status()).isEqualTo("DONE");
        assertThat(paymentResponse.totalAmount()).isEqualTo(preparation.amount());
        assertThat(paymentResponse.paymentKey()).isNotBlank();
    }

    @Test
    @Tag("service-real-api")
    @Transactional
    // Run: tossServiceRealApiTest 또는 ./gradlew :payment:tossServiceRealApiTest
    @DisplayName("서비스 레벨 실제 Toss 서버 - 카드정보로 발급한 billingKey를 서비스로 삭제한다")
    void deleteBillingMethod_usesRealTossServer() {
        CardBillingInput cardInput = serviceDeleteTestCardBillingInput();
        assertThat(cardInput.customerIdentityNumber())
                .as("TOSS_TEST_CUSTOMER_IDENTITY_NUMBER env value is required.")
                .isNotBlank();

        TossBillingKeyIssueResponse issueResponse = issueBillingKeyWithCard(cardInput);
        Billing billing = billingRepository.save(Billing.builder()
                .memberId(TEST_MEMBER_ID)
                .billingKey(issueResponse.billingKey())
                .issuerCode(CardCompany.fromCode(issueResponse.card().issuerCode()))
                .cardNumber(issueResponse.card().number())
                .cardType(CardType.from(issueResponse.card().cardType()))
                .ownerType(OwnerType.from(issueResponse.card().ownerType()))
                .isDefault(false)
                .billingStatus(BillingStatus.ACTIVE)
                .build());

        billingMethodService.deleteBillingMethod(TEST_MEMBER_ID, billing.getId());

        Billing deletedBilling = billingRepository.findById(billing.getId()).orElseThrow();
        assertThat(deletedBilling.getBillingStatus()).isEqualTo(BillingStatus.INACTIVE);
        assertThat(deletedBilling.getIsDefault()).isFalse();
        assertThat(deletedBilling.getBillingKey()).isNull();
        assertThat(deletedBilling.getIssuerCode()).isNull();
        assertThat(deletedBilling.getCardNumber()).isNull();
        assertThat(deletedBilling.getCardType()).isNull();
        assertThat(deletedBilling.getOwnerType()).isNull();

        assertThatThrownBy(() -> tossBillingClient.payWithBillingKey(
                issueResponse.billingKey(),
                issueResponse.customerKey(),
                "deleted-service-billing-" + UUID.randomUUID(),
                100L,
                "Deleted service billing key test",
                UUID.randomUUID().toString()
        )).isInstanceOf(TossPaymentException.class);
    }

    @Test
    @Tag("card-issue")
    // Run: tossCardIssueRealApiTest 또는 ./gradlew :payment:tossCardIssueRealApiTest
    @DisplayName("실제 Toss 서버 - 카드 정보로 billingKey를 발급한 뒤 삭제한다")
    void issueBillingKeyWithCard_thenDeleteBillingKey_usesRealTossServer() {
        CardBillingInput cardInput = deleteTestCardBillingInput();
        assertThat(cardInput.customerIdentityNumber())
                .as("TOSS_TEST_CUSTOMER_IDENTITY_NUMBER env value is required.")
                .isNotBlank();

        TossBillingKeyIssueResponse issueResponse = issueBillingKeyWithCard(cardInput);
        assertThat(issueResponse.billingKey()).isNotBlank();
        assertThat(issueResponse.customerKey()).isEqualTo(cardInput.customerKey());

        tossBillingClient.deleteBillingKey(issueResponse.billingKey());

        assertThatThrownBy(() -> tossBillingClient.payWithBillingKey(
                issueResponse.billingKey(),
                issueResponse.customerKey(),
                "deleted-billing-" + UUID.randomUUID(),
                100L,
                "Deleted billing key test",
                UUID.randomUUID().toString()
        )).isInstanceOfSatisfying(TossPaymentException.class, exception -> {
            TossPaymentException tossException = (TossPaymentException) exception;
            assertThat(tossException.getHttpStatusCode()).isInstanceOf(HttpStatusCode.class);
            assertThat(tossException.getTossCode()).isNotBlank();
            assertThat(tossException.getTossMessage()).isNotBlank();
        });
    }

    private String env(String name) {
        return env(name, "");
    }

    private String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private BillingCredential billingCredential() {
        String billingKey = env("TOSS_TEST_BILLING_KEY", DEFAULT_TEST_BILLING_KEY);
        String customerKey = env("TOSS_TEST_CUSTOMER_KEY", DEFAULT_TEST_CUSTOMER_KEY);
        if (!billingKey.isBlank() && !customerKey.isBlank()) {
            return new BillingCredential(billingKey, customerKey);
        }

        CardBillingInput cardInput = cardBillingInput();
        if (!cardInput.hasRequiredValues()) {
            return BillingCredential.empty();
        }

        TossBillingKeyIssueResponse response = issueBillingKeyWithCard(cardInput);
        return new BillingCredential(response.billingKey(), response.customerKey());
    }

    private TossBillingKeyIssueResponse issueBillingKeyWithCard(CardBillingInput cardInput) {
        return tossBillingClient.issueBillingKeyWithCard(
                cardInput.customerKey(),
                cardInput.cardNumber(),
                cardInput.cardExpirationYear(),
                cardInput.cardExpirationMonth(),
                cardInput.customerIdentityNumber(),
                cardInput.cardPassword(),
                cardInput.customerName(),
                cardInput.customerEmail()
        );
    }

    private CardBillingInput cardBillingInput() {
        String customerKey = env("TOSS_TEST_CUSTOMER_KEY");
        if (customerKey.isBlank()) {
            customerKey = "customer_" + UUID.randomUUID();
        }

        return new CardBillingInput(
                customerKey,
                env("TOSS_TEST_CARD_NUMBER"),
                env("TOSS_TEST_CARD_EXPIRATION_YEAR"),
                env("TOSS_TEST_CARD_EXPIRATION_MONTH"),
                env("TOSS_TEST_CUSTOMER_IDENTITY_NUMBER"),
                env("TOSS_TEST_CARD_PASSWORD"),
                env("TOSS_TEST_CUSTOMER_NAME"),
                env("TOSS_TEST_CUSTOMER_EMAIL")
        );
    }

    private CardBillingInput deleteTestCardBillingInput() {
        return new CardBillingInput(
                "delete-test-" + UUID.randomUUID(),
                env("TOSS_TEST_CARD_NUMBER", DEFAULT_TEST_CARD_NUMBER),
                env("TOSS_TEST_CARD_EXPIRATION_YEAR", DEFAULT_TEST_CARD_EXPIRATION_YEAR),
                env("TOSS_TEST_CARD_EXPIRATION_MONTH", DEFAULT_TEST_CARD_EXPIRATION_MONTH),
                env("TOSS_TEST_CUSTOMER_IDENTITY_NUMBER", DEFAULT_TEST_CUSTOMER_IDENTITY_NUMBER),
                env("TOSS_TEST_CARD_PASSWORD", DEFAULT_TEST_CARD_PASSWORD),
                env("TOSS_TEST_CUSTOMER_NAME"),
                env("TOSS_TEST_CUSTOMER_EMAIL")
        );
    }

    private CardBillingInput serviceDeleteTestCardBillingInput() {
        return new CardBillingInput(
                "service-delete-test-" + UUID.randomUUID(),
                env("TOSS_TEST_CARD_NUMBER", DEFAULT_TEST_CARD_NUMBER),
                env("TOSS_TEST_CARD_EXPIRATION_YEAR", DEFAULT_TEST_CARD_EXPIRATION_YEAR),
                env("TOSS_TEST_CARD_EXPIRATION_MONTH", DEFAULT_TEST_CARD_EXPIRATION_MONTH),
                env("TOSS_TEST_CUSTOMER_IDENTITY_NUMBER", DEFAULT_TEST_CUSTOMER_IDENTITY_NUMBER),
                env("TOSS_TEST_CARD_PASSWORD", DEFAULT_TEST_CARD_PASSWORD),
                env("TOSS_TEST_CUSTOMER_NAME"),
                env("TOSS_TEST_CUSTOMER_EMAIL")
        );
    }

    private BillingPaymentPreparation billingPaymentPreparation() {
        String orderNo = "service-real-api-" + UUID.randomUUID();

        return new BillingPaymentPreparation(
                1L,
                orderNo,
                100L,
                "Service real API test",
                TEST_BILLING_ID,
                env("TOSS_TEST_BILLING_KEY", DEFAULT_TEST_BILLING_KEY),
                env("TOSS_TEST_CUSTOMER_KEY", DEFAULT_TEST_CUSTOMER_KEY),
                UUID.randomUUID().toString()
        );
    }

    private record BillingCredential(
            String billingKey,
            String customerKey
    ) {
        static BillingCredential empty() {
            return new BillingCredential("", "");
        }

        boolean hasRequiredValues() {
            return !billingKey.isBlank() && !customerKey.isBlank();
        }
    }

    private record CardBillingInput(
            String customerKey,
            String cardNumber,
            String cardExpirationYear,
            String cardExpirationMonth,
            String customerIdentityNumber,
            String cardPassword,
            String customerName,
            String customerEmail
    ) {
        boolean hasRequiredValues() {
            return !customerKey.isBlank()
                    && !cardNumber.isBlank()
                    && !cardExpirationYear.isBlank()
                    && !cardExpirationMonth.isBlank()
                    && !customerIdentityNumber.isBlank()
                    && !cardPassword.isBlank();
        }
    }
}
