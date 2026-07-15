package com.haagendazs.payment.order.service.dto;

import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        String orderNo,
        Long workspaceId,
        Long totalAmount,
        OrderStatus orderStatus,
        OrderType orderType,
        LocalDateTime orderedAt,
        LocalDateTime expiredAt,
        List<OrderItemResponse> items,
        OrderPaymentResponse payment
) {
    public static OrderDetailResponse of(
            Orders order,
            OrderPaymentResponse payment
    ) {
        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNo(),
                order.getWorkspaceId(),
                order.getTotalAmount(),
                order.getOrderStatus(),
                order.getOrderType(),
                order.getOrderedAt(),
                order.getExpiredAt(),
                order.getOrderItems().stream()
                        .map(OrderItemResponse::from)
                        .toList(),
                payment
        );
    }
}
