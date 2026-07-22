package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.domain.model.Orders;
import com.haagendazs.domain.model.OrderStatus;
import com.haagendazs.domain.model.OrderType;
import com.haagendazs.domain.repository.OrderRepository;
import com.haagendazs.application.dto.OrderCreateRequest;
import com.haagendazs.application.dto.OrderCreateResponse;
import com.haagendazs.application.dto.OrderDetailResponse;
import com.haagendazs.application.dto.OrderListResponse;
import com.haagendazs.application.dto.OrderPaymentResponse;
import com.haagendazs.domain.repository.PaymentRepository;
import com.haagendazs.application.service.PaymentCustomerKeyService;
import com.haagendazs.domain.model.OrderItems;
import com.haagendazs.domain.model.Products;
import com.haagendazs.domain.model.ProductStatus;
import com.haagendazs.domain.model.ProductType;
import com.haagendazs.domain.repository.ProductsRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductsRepository productsRepository;
    private final PaymentCustomerKeyService paymentCustomerKeyService;
    private final PaymentRepository paymentRepository;

    //주문생성
    @Transactional
    public OrderCreateResponse createOrder(Long memberId, Long workspaceId, OrderCreateRequest dto){
        return createOrder(memberId, workspaceId, dto, null);
    }

    //주문생성. idempotencyKey가 같으면 새 주문을 만들지 않고 기존 주문을 반환합니다.
    @Transactional
    public OrderCreateResponse createOrder(
            Long memberId,
            Long workspaceId,
            OrderCreateRequest dto,
            String idempotencyKey
    ){
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        if (normalizedIdempotencyKey != null) {
            Optional<Orders> existingOrder =
                    orderRepository.findByMemberIdAndWorkspaceIdAndIdempotencyKey(
                            memberId,
                            workspaceId,
                            normalizedIdempotencyKey
                    );

            if (existingOrder.isPresent()) {
                return toOrderCreateResponse(existingOrder.get());
            }
        }

        Products product = productsRepository.findById(dto.productId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() == ProductStatus.SUSPENDED) {
            throw new BusinessException(PaymentErrorCode.PRODUCT_SUSPENDED);
        }

        long quantity = 1L; //수량 지금은 1 고정
        long totalAmount = product.getPrice() * quantity;
        LocalDateTime orderedAt = LocalDateTime.now();

        OrderType orderType;

        if(product.getProductType() == ProductType.SUBSCRIPTION){
            orderType = OrderType.Billing;
        } else{
            orderType = OrderType.Normal;
        }

        String customerKey = null;//프론트에서 자동결제 등록을 할 때 사용함.

        if (orderType == OrderType.Billing) {
            customerKey = paymentCustomerKeyService.getOrCreateCustomerKey(memberId);
        }

        Orders order = Orders.builder()
                .memberId(memberId)
                .workspaceId(workspaceId)
                .orderNo(generateOrderNumber())
                .idempotencyKey(normalizedIdempotencyKey)
                .totalAmount(totalAmount)
                .orderStatus(OrderStatus.PENDING)
                .orderType(orderType)
                .orderedAt(orderedAt)
                .expiredAt(orderedAt.plusMinutes(30))
                .build();

        order.addOrderItem(OrderItems.builder()
                .productId(product.getId())
                .itemName(product.getName())
                .itemType(product.getProductType().name())
                .unitPrice(product.getPrice())
                .quantity(quantity)
                .totalPrice(totalAmount)
                .build());

        Orders savedOrder = saveOrderOrGetExisting(
                order,
                memberId,
                workspaceId,
                normalizedIdempotencyKey
        );

        return new OrderCreateResponse(
                savedOrder.getId(),
                savedOrder.getOrderNo(),
                getFirstItemName(savedOrder),
                savedOrder.getTotalAmount(),
                savedOrder.getOrderType(),
                customerKey
        );
    }

    @Transactional
    public OrderCreateResponse createSubscriptionOrderWithAmount(
            Long memberId,
            Long workspaceId,
            Long productId,
            Long amount
    ) {
        Products product = productsRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() == ProductStatus.SUSPENDED) {
            throw new BusinessException(PaymentErrorCode.PRODUCT_SUSPENDED);
        }
        if (product.getProductType() != ProductType.SUBSCRIPTION) {
            throw new BusinessException(PaymentErrorCode.UNSUPPORTED_PRODUCT_TYPE);
        }

        LocalDateTime orderedAt = LocalDateTime.now();
        String customerKey = paymentCustomerKeyService.getOrCreateCustomerKey(memberId);

        Orders order = Orders.builder()
                .memberId(memberId)
                .workspaceId(workspaceId)
                .orderNo(generateOrderNumber())
                .totalAmount(amount)
                .orderStatus(OrderStatus.PENDING)
                .orderType(OrderType.Billing)
                .orderedAt(orderedAt)
                .expiredAt(orderedAt.plusMinutes(30))
                .build();

        order.addOrderItem(OrderItems.builder()
                .productId(product.getId())
                .itemName(product.getName())
                .itemType(product.getProductType().name())
                .unitPrice(amount)
                .quantity(1L)
                .totalPrice(amount)
                .build());

        Orders savedOrder = orderRepository.save(order);

        return new OrderCreateResponse(
                savedOrder.getId(),
                savedOrder.getOrderNo(),
                product.getName(),
                savedOrder.getTotalAmount(),
                savedOrder.getOrderType(),
                customerKey
        );
    }

    private Orders saveOrderOrGetExisting(
            Orders order,
            Long memberId,
            Long workspaceId,
            String idempotencyKey
    ) {
        try {
            return orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey == null) {
                throw e;
            }

            return orderRepository.findByMemberIdAndWorkspaceIdAndIdempotencyKey(
                            memberId,
                            workspaceId,
                            idempotencyKey
                    )
                    .orElseThrow(() -> e);
        }
    }

    private OrderCreateResponse toOrderCreateResponse(Orders order) {
        String customerKey = order.getOrderType() == OrderType.Billing
                ? paymentCustomerKeyService.getOrCreateCustomerKey(order.getMemberId())
                : null;

        return new OrderCreateResponse(
                order.getId(),
                order.getOrderNo(),
                getFirstItemName(order),
                order.getTotalAmount(),
                order.getOrderType(),
                customerKey
        );
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        return idempotencyKey.trim();
    }

    private String getFirstItemName(Orders order) {
        if (order.getOrderItems().isEmpty()) {
            return null;
        }

        return order.getOrderItems().get(0).getItemName();
    }

    // 본인이 워크스페이스에서 생성한 주문 목록을 최신순으로 조회합니다.
    @Transactional(readOnly = true)
    public List<OrderListResponse> getMyOrders(
            Long memberId,
            Long workspaceId
    ) {
        return orderRepository.findByMemberIdAndWorkspaceIdOrderByOrderedAtDesc(memberId, workspaceId)
                .stream()
                .map(OrderListResponse::from)
                .toList();
    }

    // 본인 주문 상세와 해당 주문에 연결된 결제 내역을 함께 조회합니다.
    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrderDetail(
            Long memberId,
            Long workspaceId,
            String orderNo
    ) {
        Orders order = orderRepository.findByOrderNoAndMemberIdAndWorkspaceId(orderNo, memberId, workspaceId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND));

        OrderPaymentResponse payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .map(OrderPaymentResponse::from)
                .orElse(null);

        return OrderDetailResponse.of(order, payment);
    }

    // 주문번호 생성
    // 날짜(YYMMDD) + UUID 32자리 조합으로 DB 조회 없이 충돌 가능성을 사실상 제거합니다.
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    public static String generateOrderNumber() {
        String datePart = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).format(DATE_FORMATTER);
        String uniquePart = UUID.randomUUID().toString().replace("-", "");
        return datePart + uniquePart;
    }

    private OrderType resolveOrderType(Products product) {
        return product.getProductType() == ProductType.SUBSCRIPTION ? OrderType.Billing : OrderType.Normal;
    }

    //주문취소
}
