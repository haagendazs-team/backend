package com.haagendazs.payment.order.service.dto;

import com.haagendazs.payment.product.entity.OrderItems;

public record OrderItemResponse(
        Long productId,
        String itemName,
        String itemType,
        Long unitPrice,
        Long quantity,
        Long totalPrice
) {
    public static OrderItemResponse from(OrderItems orderItem) {
        return new OrderItemResponse(
                orderItem.getProductId(),
                orderItem.getItemName(),
                orderItem.getItemType(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getTotalPrice()
        );
    }
}
