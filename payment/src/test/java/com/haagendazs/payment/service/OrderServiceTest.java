package com.haagendazs.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.TestPaymentApplication;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.order.repository.OrderRepository;
import com.haagendazs.payment.order.service.OrderService;
import com.haagendazs.payment.order.service.dto.OrderCreateRequest;
import com.haagendazs.payment.order.service.dto.OrderCreateResponse;
import com.haagendazs.payment.payment.service.PaymentCustomerKeyService;
import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.enums.ProductStatus;
import com.haagendazs.payment.product.enums.ProductType;
import com.haagendazs.payment.product.repository.ProductsRepository;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestPaymentApplication.class)
@Transactional
@ActiveProfiles("test")
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductsRepository productsRepository;

    @MockitoBean
    private PaymentCustomerKeyService paymentCustomerKeyService;

    //createOrder 테스트
    @Test
    @DisplayName("createOrderTest 성공")
    void createOrderTest() {
        Long memberId = 1L;
        Long workspaceId = 1L;
        Long productId = 2L;
        String customerKey = "customer-key-1";
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn(customerKey);

        OrderCreateResponse response = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(productId)
        );

        assertThat(response.orderId()).isNotNull();
        assertThat(response.orderNo()).isNotBlank();
        assertThat(response.orderName()).isEqualTo("Plus Subscription");
        assertThat(response.amount()).isEqualTo(19900L);
        assertThat(response.orderType()).isEqualTo(OrderType.Billing);
        assertThat(response.customerKey()).isEqualTo(customerKey);

        Optional<Orders> savedOrder = orderRepository.findById(response.orderId());
        assertThat(savedOrder).isPresent();
        assertThat(savedOrder.get().getMemberId()).isEqualTo(memberId);
        assertThat(savedOrder.get().getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(savedOrder.get().getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.get().getOrderType()).isEqualTo(OrderType.Billing);
        assertThat(savedOrder.get().getTotalAmount()).isEqualTo(19900L);
        assertThat(savedOrder.get().getExpiredAt()).isEqualTo(savedOrder.get().getOrderedAt().plusMinutes(30));
        assertThat(savedOrder.get().getOrderItems())
                .singleElement()
                .satisfies(orderItem -> {
                    assertThat(orderItem.getProductId()).isEqualTo(productId);
                    assertThat(orderItem.getItemName()).isEqualTo("Plus Subscription");
                    assertThat(orderItem.getItemType()).isEqualTo(ProductType.SUBSCRIPTION.name());
                    assertThat(orderItem.getUnitPrice()).isEqualTo(19900L);
                    assertThat(orderItem.getQuantity()).isEqualTo(1L);
                    assertThat(orderItem.getTotalPrice()).isEqualTo(19900L);
                });

        verify(paymentCustomerKeyService).getOrCreateCustomerKey(memberId);
    }

    //createOrder 테스트
    @Test
    @DisplayName("createOrderTest 실패 - 상품 없음")
    void createOrderProductNotFoundTest() {
        Long memberId = 1L;
        Long workspaceId = 1L;

        assertThatThrownBy(() -> orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(999L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_NOT_FOUND);

        verify(paymentCustomerKeyService, never()).getOrCreateCustomerKey(memberId);
    }

    //createOrder 테스트
    @Test
    @DisplayName("createOrderTest 실패 - 판매 중지 상품")
    void createOrderSuspendedProductTest() {
        Long memberId = 1L;
        Long workspaceId = 1L;
        Products suspendedProduct = productsRepository.saveAndFlush(Products.builder()
                .name("Suspended Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(9900L)
                .status(ProductStatus.SUSPENDED)
                .product_detail_id(1L)
                .build());

        assertThatThrownBy(() -> orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(suspendedProduct.getId())
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_SUSPENDED);

        verify(paymentCustomerKeyService, never()).getOrCreateCustomerKey(memberId);
    }
}
