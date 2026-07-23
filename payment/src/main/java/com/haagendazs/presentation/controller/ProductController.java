package com.haagendazs.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.domain.model.ProductType;
import com.haagendazs.application.service.ProductService;
import com.haagendazs.application.dto.ProductResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    //구독 상품 목록
    @GetMapping
    public ApiResponse<List<ProductResponse>> getProduct(@RequestParam ProductType type) {
        return ApiResponse.ok(productService.getProduct(type));
    }
}
