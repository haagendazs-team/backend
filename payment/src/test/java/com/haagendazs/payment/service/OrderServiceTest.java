package com.haagendazs.payment.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.TestPaymentApplication;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.domain.model.Orders;
import com.haagendazs.domain.model.OrderStatus;
import com.haagendazs.domain.model.OrderType;
import com.haagendazs.domain.repository.OrderRepository;
import com.haagendazs.application.service.OrderService;
import com.haagendazs.application.dto.OrderCreateRequest;
import com.haagendazs.application.dto.OrderCreateResponse;
import com.haagendazs.application.dto.OrderDetailResponse;
import com.haagendazs.application.dto.OrderListResponse;
import com.haagendazs.domain.model.Payments;
import com.haagendazs.domain.model.CardCompany;
import com.haagendazs.domain.model.PaymentMethod;
import com.haagendazs.domain.model.PaymentProvider;
import com.haagendazs.domain.model.PaymentStatus;
import com.haagendazs.domain.repository.PaymentRepository;
import com.haagendazs.application.service.PaymentCustomerKeyService;
import com.haagendazs.domain.model.Products;
import com.haagendazs.domain.model.ProductStatus;
import com.haagendazs.domain.model.ProductType;
import com.haagendazs.domain.repository.ProductsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Autowired
    private PaymentRepository paymentRepository;

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

    @Test
    @DisplayName("createOrderTest 성공 - 같은 멱등키는 기존 주문 반환")
    void createOrderIdempotentTest() {
        Long memberId = 2L;
        Long workspaceId = 2L;
        String idempotencyKey = "checkout-idempotency-key-2";
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-2");

        OrderCreateResponse firstResponse = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                idempotencyKey
        );
        OrderCreateResponse secondResponse = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                idempotencyKey
        );

        assertThat(secondResponse.orderId()).isEqualTo(firstResponse.orderId());
        assertThat(secondResponse.orderNo()).isEqualTo(firstResponse.orderNo());
        assertThat(secondResponse.customerKey()).isEqualTo("customer-key-2");
        assertThat(orderRepository.findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(memberId, workspaceId))
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.getId()).isEqualTo(firstResponse.orderId());
                    assertThat(order.getIdempotencyKey()).isEqualTo(idempotencyKey);
                });
    }

    @Test
    @DisplayName("createOrderTest 성공 - 멱등키는 앞뒤 공백 제거 후 비교")
    void createOrderIdempotentTrimmedKeyTest() {
        Long memberId = 21L;
        Long workspaceId = 21L;
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-21");

        OrderCreateResponse firstResponse = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                "  checkout-idempotency-key-21  "
        );
        OrderCreateResponse secondResponse = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                "checkout-idempotency-key-21"
        );

        assertThat(secondResponse.orderId()).isEqualTo(firstResponse.orderId());
        assertThat(orderRepository.findById(firstResponse.orderId()).get().getIdempotencyKey())
                .isEqualTo("checkout-idempotency-key-21");
    }

    @Test
    @DisplayName("createOrderTest 성공 - 빈 멱등키는 사용하지 않음")
    void createOrderBlankIdempotencyKeyTest() {
        Long memberId = 22L;
        Long workspaceId = 22L;
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-22");

        OrderCreateResponse firstResponse = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                "   "
        );
        OrderCreateResponse secondResponse = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                "   "
        );

        assertThat(secondResponse.orderId()).isNotEqualTo(firstResponse.orderId());
        assertThat(orderRepository.findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(memberId, workspaceId))
                .hasSize(2)
                .allSatisfy(order -> assertThat(order.getIdempotencyKey()).isNull());
    }

    @Test
    @DisplayName("createOrderTest 성공 - 기존 일반 주문은 customerKey 없이 반환")
    void createOrderExistingNormalOrderWithoutCustomerKeyTest() {
        Long memberId = 23L;
        Long workspaceId = 23L;
        Orders existingOrder = orderRepository.saveAndFlush(Orders.builder()
                .memberId(memberId)
                .workspaceId(workspaceId)
                .orderNo("ORDER-NORMAL-23")
                .idempotencyKey("normal-order-key-23")
                .totalAmount(5000L)
                .orderStatus(OrderStatus.PENDING)
                .orderType(OrderType.Normal)
                .orderedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build());

        OrderCreateResponse response = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                "normal-order-key-23"
        );

        assertThat(response.orderId()).isEqualTo(existingOrder.getId());
        assertThat(response.orderNo()).isEqualTo("ORDER-NORMAL-23");
        assertThat(response.orderName()).isNull();
        assertThat(response.orderType()).isEqualTo(OrderType.Normal);
        assertThat(response.customerKey()).isNull();
        verify(paymentCustomerKeyService, never()).getOrCreateCustomerKey(memberId);
    }

    @Test
    @DisplayName("createOrderTest 성공 - 일반 상품은 일반 주문으로 생성")
    void createOrderNormalProductTest() {
        Long memberId = 24L;
        Long workspaceId = 24L;
        Products normalProduct = productsRepository.saveAndFlush(Products.builder()
                .name("Normal Product")
                .productType(ProductType.NORMAL)
                .price(5000L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(1L)
                .build());

        OrderCreateResponse response = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(normalProduct.getId())
        );

        assertThat(response.orderId()).isNotNull();
        assertThat(response.orderName()).isEqualTo("Normal Product");
        assertThat(response.amount()).isEqualTo(5000L);
        assertThat(response.orderType()).isEqualTo(OrderType.Normal);
        assertThat(response.customerKey()).isNull();

        Orders savedOrder = orderRepository.findById(response.orderId()).get();
        assertThat(savedOrder.getOrderType()).isEqualTo(OrderType.Normal);
        assertThat(savedOrder.getOrderItems())
                .singleElement()
                .satisfies(orderItem -> {
                    assertThat(orderItem.getProductId()).isEqualTo(normalProduct.getId());
                    assertThat(orderItem.getItemName()).isEqualTo("Normal Product");
                    assertThat(orderItem.getItemType()).isEqualTo(ProductType.NORMAL.name());
                    assertThat(orderItem.getTotalPrice()).isEqualTo(5000L);
                });
        verify(paymentCustomerKeyService, never()).getOrCreateCustomerKey(memberId);
    }

    @Test
    @DisplayName("구독 주문 금액 지정 생성 성공")
    void createSubscriptionOrderWithAmountTest() {
        Long memberId = 3L;
        Long workspaceId = 3L;
        Long productId = 3L;
        Long amount = 10000L;
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-3");

        OrderCreateResponse response = orderService.createSubscriptionOrderWithAmount(
                memberId,
                workspaceId,
                productId,
                amount
        );

        assertThat(response.orderId()).isNotNull();
        assertThat(response.orderNo()).isNotBlank();
        assertThat(response.orderName()).isEqualTo("Pro Subscription");
        assertThat(response.amount()).isEqualTo(amount);
        assertThat(response.orderType()).isEqualTo(OrderType.Billing);
        assertThat(response.customerKey()).isEqualTo("customer-key-3");

        Orders savedOrder = orderRepository.findById(response.orderId()).get();
        assertThat(savedOrder.getTotalAmount()).isEqualTo(amount);
        assertThat(savedOrder.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getOrderItems())
                .singleElement()
                .satisfies(orderItem -> {
                    assertThat(orderItem.getProductId()).isEqualTo(productId);
                    assertThat(orderItem.getUnitPrice()).isEqualTo(amount);
                    assertThat(orderItem.getTotalPrice()).isEqualTo(amount);
                });
    }

    @Test
    @DisplayName("본인 주문 목록 조회")
    void getMyOrdersTest() {
        Long memberId = 10L;
        Long workspaceId = 10L;
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-10");
        OrderCreateResponse myOrder = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L)
        );
        orderService.createOrder(
                memberId,
                999L,
                new OrderCreateRequest(2L)
        );

        List<OrderListResponse> responses = orderService.getMyOrders(memberId, workspaceId);

        assertThat(responses)
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.orderId()).isEqualTo(myOrder.orderId());
                    assertThat(order.orderNo()).isEqualTo(myOrder.orderNo());
                    assertThat(order.orderName()).isEqualTo("Plus Subscription");
                    assertThat(order.totalAmount()).isEqualTo(19900L);
                    assertThat(order.orderStatus()).isEqualTo(OrderStatus.PENDING);
                    assertThat(order.orderType()).isEqualTo(OrderType.Billing);
                });
    }

    @Test
    @DisplayName("본인 주문 상세 조회 - 결제 내역 포함")
    void getMyOrderDetailTest() {
        Long memberId = 11L;
        Long workspaceId = 11L;
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-11");
        OrderCreateResponse createdOrder = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L)
        );
        paymentRepository.saveAndFlush(Payments.builder()
                .orderId(createdOrder.orderId())
                .paymentKey("payment-key-11")
                .paymentMethod(PaymentMethod.CARD)
                .paymentProvider(PaymentProvider.TOSS)
                .paymentStatus(PaymentStatus.DONE)
                .totalAmount(19900L)
                .approvedAt(LocalDateTime.now())
                .cardCompany(CardCompany.SHINHAN)
                .cardNumber("123456******7890")
                .receiptUrl("https://receipt.example/11")
                .build());

        OrderDetailResponse response =
                orderService.getMyOrderDetail(memberId, workspaceId, createdOrder.orderNo());

        assertThat(response.orderId()).isEqualTo(createdOrder.orderId());
        assertThat(response.orderNo()).isEqualTo(createdOrder.orderNo());
        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        assertThat(response.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.productId()).isEqualTo(2L);
                    assertThat(item.itemName()).isEqualTo("Plus Subscription");
                    assertThat(item.totalPrice()).isEqualTo(19900L);
                });
        assertThat(response.payment()).isNotNull();
        assertThat(response.payment().paymentKey()).isEqualTo("payment-key-11");
        assertThat(response.payment().paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.payment().paymentProvider()).isEqualTo(PaymentProvider.TOSS);
        assertThat(response.payment().paymentStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(response.payment().totalAmount()).isEqualTo(19900L);
        assertThat(response.payment().cardCompany()).isEqualTo(CardCompany.SHINHAN);
        assertThat(response.payment().receiptUrl()).isEqualTo("https://receipt.example/11");
    }

    @Test
    @DisplayName("본인 주문 상세 조회 - 결제 내역 없음")
    void getMyOrderDetailWithoutPaymentTest() {
        Long memberId = 13L;
        Long workspaceId = 13L;
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-13");
        OrderCreateResponse createdOrder = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L)
        );

        OrderDetailResponse response =
                orderService.getMyOrderDetail(memberId, workspaceId, createdOrder.orderNo());

        assertThat(response.orderId()).isEqualTo(createdOrder.orderId());
        assertThat(response.payment()).isNull();
    }

    @Test
    @DisplayName("본인 주문 상세 조회 - 다른 회원 주문 조회 불가")
    void getMyOrderDetailNotFoundTest() {
        Long memberId = 12L;
        Long workspaceId = 12L;
        when(paymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-12");
        OrderCreateResponse createdOrder = orderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L)
        );

        assertThatThrownBy(() -> orderService.getMyOrderDetail(999L, workspaceId, createdOrder.orderNo()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND);
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

    @Test
    @DisplayName("구독 주문 금액 지정 생성 실패 - 상품 없음")
    void createSubscriptionOrderWithAmountProductNotFoundTest() {
        Long memberId = 4L;
        Long workspaceId = 4L;

        assertThatThrownBy(() -> orderService.createSubscriptionOrderWithAmount(
                memberId,
                workspaceId,
                999L,
                10000L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_NOT_FOUND);

        verify(paymentCustomerKeyService, never()).getOrCreateCustomerKey(memberId);
    }

    @Test
    @DisplayName("구독 주문 금액 지정 생성 실패 - 판매 중지 상품")
    void createSubscriptionOrderWithAmountSuspendedProductTest() {
        Long memberId = 5L;
        Long workspaceId = 5L;
        Products suspendedProduct = productsRepository.saveAndFlush(Products.builder()
                .name("Suspended Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(9900L)
                .status(ProductStatus.SUSPENDED)
                .product_detail_id(1L)
                .build());

        assertThatThrownBy(() -> orderService.createSubscriptionOrderWithAmount(
                memberId,
                workspaceId,
                suspendedProduct.getId(),
                5000L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PRODUCT_SUSPENDED);

        verify(paymentCustomerKeyService, never()).getOrCreateCustomerKey(memberId);
    }

    @Test
    @DisplayName("createOrderTest 성공 - 멱등키 저장 충돌시 기존 주문 반환")
    void createOrderIdempotencySaveConflictReturnsExistingOrderTest() {
        Long memberId = 30L;
        Long workspaceId = 30L;
        ProductsRepository mockProductsRepository = mock(ProductsRepository.class);
        OrderRepository mockOrderRepository = mock(OrderRepository.class);
        PaymentCustomerKeyService mockPaymentCustomerKeyService = mock(PaymentCustomerKeyService.class);
        PaymentRepository mockPaymentRepository = mock(PaymentRepository.class);
        OrderService unitOrderService = new OrderService(
                mockOrderRepository,
                mockProductsRepository,
                mockPaymentCustomerKeyService,
                mockPaymentRepository
        );
        Products product = Products.builder()
                .id(2L)
                .name("Plus Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(19900L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(2L)
                .build();
        Orders existingOrder = Orders.builder()
                .id(300L)
                .memberId(memberId)
                .workspaceId(workspaceId)
                .orderNo("ORDER-300")
                .idempotencyKey("idempotency-key-30")
                .totalAmount(19900L)
                .orderStatus(OrderStatus.PENDING)
                .orderType(OrderType.Billing)
                .orderedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .build();
        existingOrder.addOrderItem(com.haagendazs.domain.model.OrderItems.builder()
                .productId(2L)
                .itemName("Plus Subscription")
                .itemType(ProductType.SUBSCRIPTION.name())
                .unitPrice(19900L)
                .quantity(1L)
                .totalPrice(19900L)
                .build());
        when(mockOrderRepository.findByMemberIdAndWorkspaceIdAndIdempotencyKey(
                memberId,
                workspaceId,
                "idempotency-key-30"
        )).thenReturn(Optional.empty(), Optional.of(existingOrder));
        when(mockProductsRepository.findById(2L)).thenReturn(Optional.of(product));
        when(mockPaymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-30");
        when(mockOrderRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        OrderCreateResponse response = unitOrderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L),
                "idempotency-key-30"
        );

        assertThat(response.orderId()).isEqualTo(existingOrder.getId());
        assertThat(response.orderNo()).isEqualTo(existingOrder.getOrderNo());
        assertThat(response.orderName()).isEqualTo("Plus Subscription");
        assertThat(response.customerKey()).isEqualTo("customer-key-30");
    }

    @Test
    @DisplayName("createOrderTest 실패 - 멱등키 없는 저장 충돌은 재던짐")
    void createOrderSaveConflictWithoutIdempotencyKeyThrowsTest() {
        Long memberId = 31L;
        Long workspaceId = 31L;
        ProductsRepository mockProductsRepository = mock(ProductsRepository.class);
        OrderRepository mockOrderRepository = mock(OrderRepository.class);
        PaymentCustomerKeyService mockPaymentCustomerKeyService = mock(PaymentCustomerKeyService.class);
        PaymentRepository mockPaymentRepository = mock(PaymentRepository.class);
        OrderService unitOrderService = new OrderService(
                mockOrderRepository,
                mockProductsRepository,
                mockPaymentCustomerKeyService,
                mockPaymentRepository
        );
        Products product = Products.builder()
                .id(2L)
                .name("Plus Subscription")
                .productType(ProductType.SUBSCRIPTION)
                .price(19900L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(2L)
                .build();
        when(mockProductsRepository.findById(2L)).thenReturn(Optional.of(product));
        when(mockPaymentCustomerKeyService.getOrCreateCustomerKey(memberId)).thenReturn("customer-key-31");
        when(mockOrderRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate order number"));

        assertThatThrownBy(() -> unitOrderService.createOrder(
                memberId,
                workspaceId,
                new OrderCreateRequest(2L)
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("구독 주문 금액 지정 생성 실패 - 지원하지 않는 상품 타입")
    void createSubscriptionOrderWithAmountUnsupportedProductTypeTest() {
        Long memberId = 32L;
        Long workspaceId = 32L;
        Products normalProduct = productsRepository.saveAndFlush(Products.builder()
                .name("Normal Product")
                .productType(ProductType.NORMAL)
                .price(5000L)
                .status(ProductStatus.ACVIVE)
                .product_detail_id(1L)
                .build());

        assertThatThrownBy(() -> orderService.createSubscriptionOrderWithAmount(
                memberId,
                workspaceId,
                normalProduct.getId(),
                10000L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.UNSUPPORTED_PRODUCT_TYPE);
        verify(paymentCustomerKeyService, never()).getOrCreateCustomerKey(memberId);
    }
}
