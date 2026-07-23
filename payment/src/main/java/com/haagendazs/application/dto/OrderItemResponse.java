package com.haagendazs.application.dto;

import com.haagendazs.domain.model.OrderItems;

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
