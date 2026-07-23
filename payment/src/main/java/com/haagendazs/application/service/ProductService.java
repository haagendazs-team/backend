package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.model.SubscriptionPlan;
import com.haagendazs.domain.repository.SubscriptionPlanRepository;
import com.haagendazs.application.dto.SubscriptionPlanResponse;
import com.haagendazs.domain.exception.PaymentErrorCode;
import com.haagendazs.domain.model.Products;
import com.haagendazs.domain.model.ProductType;
import com.haagendazs.domain.repository.ProductsRepository;
import com.haagendazs.application.dto.ProductDetailResponse;
import com.haagendazs.application.dto.ProductResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductsRepository productsRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    //상품정보
    @Transactional(readOnly = true)
    public List<ProductResponse> getProduct(ProductType type) {
        List<Products> products = productsRepository.findAllByProductType(type);

        if (products.isEmpty()) {
            throw new BusinessException(PaymentErrorCode.PRODUCT_NOT_FOUND);
        }

        return products.stream()
                .map(product -> new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getProductType(),
                        product.getPrice(),
                        product.getStatus(),
                        getProductDetail(product)
                ))
                .toList();
    }

    private ProductDetailResponse getProductDetail(Products product) {
        if (product.getProductType() == ProductType.SUBSCRIPTION) {
            SubscriptionPlan plan = subscriptionPlanRepository
                    .findById(product.getProduct_detail_id())
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.PRODUCT_DETAIL_NOT_FOUND));

            return new SubscriptionPlanResponse(
                    plan.getId(),
                    plan.getName(),
                    plan.getType(),
                    plan.getDurationDays(),
                    plan.getStatus()
            );
        }

        throw new BusinessException(PaymentErrorCode.UNSUPPORTED_PRODUCT_TYPE);
    }
}
