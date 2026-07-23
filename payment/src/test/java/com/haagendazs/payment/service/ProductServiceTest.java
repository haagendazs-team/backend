package com.haagendazs.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.TestPaymentApplication;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.domain.model.Products;
import com.haagendazs.domain.model.ProductStatus;
import com.haagendazs.domain.model.ProductType;
import com.haagendazs.domain.repository.ProductsRepository;
import com.haagendazs.application.service.ProductService;
import com.haagendazs.application.dto.ProductResponse;
import com.haagendazs.domain.model.PlanStatus;
import com.haagendazs.domain.model.PlanType;
import com.haagendazs.application.dto.SubscriptionPlanResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestPaymentApplication.class)
@Transactional
@ActiveProfiles("test")
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductsRepository productsRepository;

    @Test
    @DisplayName("구독 상품 목록 조회 - 플랜 상세 포함")
    void getSubscriptionProductsTest() {
        List<ProductResponse> responses = productService.getProduct(ProductType.SUBSCRIPTION);

        assertThat(responses).hasSize(3);
        assertThat(responses)
                .extracting(ProductResponse::name)
                .containsExactlyInAnyOrder(
                        "Standard Subscription",
                        "Plus Subscription",
                        "Pro Subscription"
                );
        assertThat(responses)
                .filteredOn(response -> response.productId().equals(2L))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.productType()).isEqualTo(ProductType.SUBSCRIPTION);
                    assertThat(response.price()).isEqualTo(19900L);
                    assertThat(response.status()).isEqualTo(ProductStatus.ACVIVE);
                    assertThat(response.detail()).isInstanceOf(SubscriptionPlanResponse.class);

                    SubscriptionPlanResponse detail = (SubscriptionPlanResponse) response.detail();
                    assertThat(detail.subscriptionPlanId()).isEqualTo(2L);
                    assertThat(detail.planName()).isEqualTo("Plus Plan");
                    assertThat(detail.type()).isEqualTo(PlanType.PLUS);
                    assertThat(detail.durationDays()).isEqualTo(30);
                    assertThat(detail.status()).isEqualTo(PlanStatus.ACTIVE);
                });
    }

    @Test
    @DisplayName("구독 상품 목록 조회 - 상품 없음")
    void getSubscriptionProductsNotFoundTest() {
        productsRepository.deleteAll();
        productsRepository.flush();

        assertThatThrownBy(() -> productService.getProduct(ProductType.SUBSCRIPTION))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("구독 상품 목록 조회 - 상품 상세 플랜 없음")
    void getSubscriptionProductsDetailNotFoundTest() {
        productsRepository.deleteAll();
        productsRepository.saveAndFlush(Products.builder()
                .name("Unknown Plan Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(9900L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(999L)
                .build());

        assertThatThrownBy(() -> productService.getProduct(ProductType.SUBSCRIPTION))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_DETAIL_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 상세 조회 - 지원하지 않는 상품 타입")
    void getProductDetailUnsupportedProductTypeTest() {
        productsRepository.saveAndFlush(Products.builder()
                .name("Unsupported Product")
                .productType(ProductType.NORMAL)
                .price(1000L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(1L)
                .build());

        assertThatThrownBy(() -> productService.getProduct(ProductType.NORMAL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.UNSUPPORTED_PRODUCT_TYPE);
    }
}
