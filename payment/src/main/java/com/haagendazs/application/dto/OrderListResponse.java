package com.haagendazs.application.dto;

import com.haagendazs.domain.model.Orders;
import com.haagendazs.domain.model.OrderStatus;
import com.haagendazs.domain.model.OrderType;
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
