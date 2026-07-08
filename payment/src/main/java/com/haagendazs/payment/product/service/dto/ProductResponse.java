package com.haagendazs.payment.product.service.dto;

import com.haagendazs.payment.product.enums.ProductStatus;
import com.haagendazs.payment.product.enums.ProductType;

public record ProductResponse(
        Long productId,
        String name,
        ProductType productType,
        Long price,
        ProductStatus status,
        ProductDetailResponse detail
) {
}
