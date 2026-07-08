package com.haagendazs.payment.order.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.payment.global.PaymentErrorCode;
import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import com.haagendazs.payment.order.repository.OrderRepository;
import com.haagendazs.payment.order.service.dto.OrderCreateRequest;
import com.haagendazs.payment.order.service.dto.OrderCreateResponse;
import com.haagendazs.payment.payment.service.PaymentCustomerKeyService;
import com.haagendazs.payment.product.entity.OrderItems;
import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.enums.ProductStatus;
import com.haagendazs.payment.product.enums.ProductType;
import com.haagendazs.payment.product.repository.ProductsRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductsRepository productsRepository;
    private final PaymentCustomerKeyService paymentCustomerKeyService;

    //주문생성
    @Transactional
    public OrderCreateResponse createOrder(Long memberId, Long workspaceId, OrderCreateRequest dto){
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

        String customerKey = null;

        if (orderType == OrderType.Billing) {
            customerKey = paymentCustomerKeyService.getOrCreateCustomerKey(memberId);
        }

        Orders order = Orders.builder()
                .memberId(memberId)
                .workspaceId(workspaceId)
                .orderNo(generateOrderNumber())
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
