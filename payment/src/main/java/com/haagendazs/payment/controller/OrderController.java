package com.haagendazs.payment.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.payment.order.service.OrderService;
import com.haagendazs.payment.order.service.dto.OrderCreateRequest;
import com.haagendazs.payment.order.service.dto.OrderCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/workspaces/{workspaceId}/orders")
public class OrderController {

    private final OrderService orderService;

    //구독 주문
    @PostMapping("/subscription")
    public ApiResponse<OrderCreateResponse> createSubscriptionOrder(
            @RequestHeader("X-Member-Id")
            Long memberId,
            @PathVariable
            Long workspaceId,
            @RequestBody @Valid
            OrderCreateRequest request
    ){
        OrderCreateResponse response = orderService.createOrder(memberId, workspaceId, request);
        return ApiResponse.ok(response);
    }
}
