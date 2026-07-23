package com.haagendazs.application.dto;

import com.haagendazs.domain.model.ProductStatus;
import com.haagendazs.domain.model.ProductType;

public record ProductResponse(
        Long productId,
        String name,
        ProductType productType,
        Long price,
        ProductStatus status,
        ProductDetailResponse detail
) {
}
