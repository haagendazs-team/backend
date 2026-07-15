package com.haagendazs.payment.order.service.dto;

import com.haagendazs.payment.order.entity.Orders;
import com.haagendazs.payment.order.enums.OrderStatus;
import com.haagendazs.payment.order.enums.OrderType;
import java.time.LocalDateTime;

public record OrderListResponse(
        Long orderId,
        String orderNo,
        String orderName,
        Long totalAmount,
        OrderStatus orderStatus,
        OrderType orderType,
        LocalDateTime orderedAt
) {
    public static OrderListResponse from(Orders order) {
        return new OrderListResponse(
                order.getId(),
                order.getOrderNo(),
                getFirstItemName(order),
                order.getTotalAmount(),
                order.getOrderStatus(),
                order.getOrderType(),
                order.getOrderedAt()
        );
    }

    private static String getFirstItemName(Orders order) {
        if (order.getOrderItems().isEmpty()) {
            return null;
        }

        return order.getOrderItems().get(0).getItemName();
    }
}
